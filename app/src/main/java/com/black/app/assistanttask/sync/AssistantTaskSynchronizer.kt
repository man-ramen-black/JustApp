package com.black.app.assistanttask.sync

import android.content.Context
import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskKind
import com.black.app.assistanttask.alarm.AssistantTaskAlarmRescheduler
import com.black.app.assistanttask.alarm.AssistantTaskAlarmScheduler
import com.black.app.assistanttask.notion.NotionHttpClient
import com.black.app.assistanttask.notion.NotionHttpResponse
import com.black.app.assistanttask.notion.NotionQueryPage
import com.black.app.assistanttask.notion.NotionTaskApi
import com.black.app.assistanttask.notion.NotionTaskFormat
import com.black.app.model.AssistantTaskConnection
import com.black.app.model.AssistantTaskRepository
import com.black.app.model.AssistantTaskSettingsRepository
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.TimeZone
import javax.inject.Inject

/** [AssistantTaskSynchronizer.replaceConnection]이 연결은 저장했지만 저장본을 비우지 못했을 때의 실패 */
class AssistantTaskStoreClearException : Exception()

/**
 * [AssistantTaskSynchronizer.syncWithStage]의 결과 상태와 노션 조회 단계에서 실패했는지 여부
 *
 * [failedOnFetch]가 true면 조회가 실패해 반영 대기 항목을 보내지 않았고 저장본도 그대로임.
 * 조회에 성공한 뒤 보내기·저장에서 실패하면 false.
 */
data class AssistantTaskSyncResult(val status: AssistantTaskSyncStatus, val failedOnFetch: Boolean)

/**
 * 노션 `비서 할 일`을 끝까지 읽고 앱에서 바꾼 항목을 노션에 보낸 뒤 Room 저장본을 바꾸고 결과를 남김
 *
 * 토큰·요청과 응답 본문·할 일 내용은 로그에 남기지 않고 상태 코드와 예외 클래스 이름만 남김.
 * 조회가 하나라도 실패하면 보내지 않고 저장본은 그대로 두고 실패 상태만 남김.
 * 노션 데이터베이스에는 메모도 있지만 앱은 Todo만 다루므로 종류가 메모인 항목은 저장하지 않음.
 * [dispatcher]는 응답 해석을 메인 스레드 밖에서 돌리기 위한 것이며, 테스트는 가상 시간을 쓰도록 테스트 디스패처를 넘김.
 * [zone]은 페이지 추가 요청에 시각 기한을 실을 때 쓰는 시간대.
 * [alarms]는 저장본을 바꾼 뒤 Todo 시각 알림을 다시 예약하는 데 씀.
 */
class AssistantTaskSynchronizer(
    private val settings: AssistantTaskSettingsRepository,
    private val repository: AssistantTaskRepository,
    private val api: NotionTaskApi,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
    private val zone: () -> TimeZone,
    private val alarms: AssistantTaskAlarmRescheduler,
) {
    @Inject
    constructor(
        settings: AssistantTaskSettingsRepository,
        repository: AssistantTaskRepository,
        api: NotionHttpClient,
        alarms: AssistantTaskAlarmScheduler,
    ) : this(settings, repository, api, Dispatchers.Default, System::currentTimeMillis, TimeZone::getDefault, alarms)

    /**
     * Hilt 없이 의존성을 직접 만드는 WorkManager 작업이 쓰는 생성자
     *
     * 주기 동기화 작업과 집 도착 작업이 씀.
     */
    constructor(context: Context) : this(
        AssistantTaskSettingsRepository(context.applicationContext),
        AssistantTaskRepository(context.applicationContext),
        NotionHttpClient(),
        AssistantTaskAlarmScheduler(context.applicationContext),
    )

    /** 조회 결과이며 성공이면 [Fetched.value], 실패면 마지막 동기화로 남길 [Failed.status]를 담음 */
    private sealed interface FetchResult<out T> {
        class Fetched<T>(val value: T) : FetchResult<T>
        class Failed(val status: AssistantTaskSyncStatus) : FetchResult<Nothing>
    }

    /** 반영 대기 항목 하나를 노션에 보낸 결과 */
    private sealed interface PushResult {
        /** 노션 반영이 끝나 [task]로 정리할 결과이며, 노션에서 페이지가 없어졌거나 Todo가 아니면 [task]는 null */
        class Resolved(val task: AssistantTask?) : PushResult

        /** 반영 대기로 남길 실패이며, [stopsRemaining]이면 남은 항목도 보내지 않음 */
        class Failed(val status: AssistantTaskSyncStatus, val stopsRemaining: Boolean) : PushResult

        /** 노션 반영 결과를 저장본에 적지 못한 실패 */
        data object StorageFailed : PushResult
    }

    /**
     * 동기화 한 번을 돌리고 결과 상태를 돌려줌
     *
     * 프로세스 전역 [Mutex]로 화면과 Worker가 동시에 돌지 않게 하며, 설정이 없으면 마지막 동기화를 남기지 않음.
     * 보낸 결과를 저장본에 반영했거나 저장본을 교체했으면 보내기 실패·저장 실패·취소로 끝나도 시각 알림을 한 번 다시 예약함.
     */
    suspend fun sync(): AssistantTaskSyncStatus = syncWithStage().status

    /**
     * [sync]와 같은 동기화 한 번을 돌리고 결과 상태와 함께 노션 조회 단계에서 실패했는지 돌려줌
     *
     * 집 도착 작업이 조회 단계의 네트워크 실패에만 다시 동기화하는 데 씀.
     * 보내기 단계 실패 뒤 다시 돌리면 응답만 끊긴 추가 요청을 또 보내 같은 Todo가 여러 개 생길 수 있음.
     */
    suspend fun syncWithStage(): AssistantTaskSyncResult =
        // 잠금을 디스패처 안에서 잡아, 동기화가 끝나면 호출자 디스패처로 돌아가기를 기다리지 않고 바로 풂
        withContext(dispatcher) { mutex.withLock { syncLocked() } }

    /**
     * 노션 연결을 [connection]으로 바꿈
     *
     * data source ID가 바뀌면 다른 데이터베이스라 저장본과 마지막 동기화도 비우고, 토큰만 바뀌면 같은 데이터베이스라 그대로 둠.
     * 저장본을 비우려 했으면 성공·실패·취소와 상관없이 남은 저장본으로 시각 알림을 다시 예약해 옛 데이터베이스의 알람을 정리함.
     * 하이픈 유무와 대소문자만 다른 ID는 같은 데이터베이스로 봄.
     * [sync]와 같은 잠금 안에서 처리해 진행 중인 동기화가 옛 연결로 저장본을 다시 채우지 못하게 함.
     * 결과는 연결 저장의 성공 여부이며, 연결은 저장했지만 저장본을 비우지 못했으면 [AssistantTaskStoreClearException] 실패.
     * 연결 저장부터 다시 예약까지는 취소돼도 끝까지 마친 뒤 취소를 전파함.
     */
    suspend fun replaceConnection(connection: AssistantTaskConnection): Result<Unit> = mutex.withLock {
        // 저장 전 연결 확인
        val previous = settings.getConnection()
        // 연결 저장부터 저장본 비우기, 다시 예약까지 취소되지 않게 묶음(모두 기기 안의 짧은 작업)
        // 취소가 연결 저장 안에서 오면 DataStore 쓰기는 커밋된 채 저장본 비우기와 다시 예약을 건너뛰어 새 연결에 옛 데이터베이스의 저장본이 남기 때문
        val result = withContext(NonCancellable) {
            // 연결 저장
            val saved = settings.saveConnection(connection)
            // 저장에 실패했거나 같은 데이터베이스면 저장본 그대로
            if (saved.isFailure || normalizeId(previous.dataSourceId) == normalizeId(connection.dataSourceId)) return@withContext saved
            try {
                // 다른 데이터베이스로 바뀌었으면 저장본 비우기
                clearStore()
            } finally {
                // 남은 저장본으로 시각 알림 다시 예약
                // 저장본을 비운 뒤 화면을 닫아 취소되면 옛 데이터베이스의 알람만 남으므로 취소 중에도 끝까지 실행
                alarms.reschedule()
            }
        }
        // 묶은 작업을 마친 뒤 취소됐으면 결과 대신 취소 전파
        currentCoroutineContext().ensureActive()
        result
    }

    /**
     * 저장본과 마지막 동기화 비우기
     *
     * 연결은 이미 저장된 뒤라 저장본 삭제 실패는 연결 저장 실패와 구분해 [AssistantTaskStoreClearException]으로 돌려줌.
     * 반영 대기가 아닌 옛 항목은 새 연결의 다음 동기화가 저장본을 바꿀 때 사라지지만, 반영 대기 항목은 남아 새 연결로 보내질 수 있음.
     */
    private suspend fun clearStore(): Result<Unit> {
        // 저장본 비우기
        val cleared = storageCall { repository.clear() } != null
        // 옛 데이터베이스의 마지막 동기화 비우기
        settings.clearLastSync()
        return if (cleared) Result.success(Unit) else Result.failure(AssistantTaskStoreClearException())
    }

    private suspend fun syncLocked(): AssistantTaskSyncResult {
        // 연결 설정 확인
        val connection = settings.getConnection()
        if (!connection.isConfigured) return AssistantTaskSyncResult(AssistantTaskSyncStatus.NOT_CONFIGURED, failedOnFetch = false)

        // 미완료 조회와 최근 완료 조회를 끝까지 받음
        val sinceMillis = now() - DONE_WINDOW_MILLIS
        val openTasks = when (val result = fetchAll(connection) { cursor -> NotionTaskFormat.openTasksQuery(cursor) }) {
            is FetchResult.Fetched -> result.value
            is FetchResult.Failed -> return AssistantTaskSyncResult(recordFailure(result.status), failedOnFetch = true)
        }
        val doneTasks = when (val result = fetchAll(connection) { cursor -> NotionTaskFormat.recentlyDoneQuery(sinceMillis, cursor) }) {
            is FetchResult.Fetched -> result.value
            is FetchResult.Failed -> return AssistantTaskSyncResult(recordFailure(result.status), failedOnFetch = true)
        }

        // 종류가 메모인 항목 제외(종류가 비면 해석 단계에서 Todo로 봄)
        val todos = (openTasks + doneTasks).filter { task -> task.kind == AssistantTaskKind.TASK }

        // 두 조회 사이에 수정돼 양쪽에 다 온 페이지는 나중에 수정된 쪽, 수정 시각이 같으면 미완료 쪽을 남김
        val merged = LinkedHashMap<String, AssistantTask>()
        todos.forEach { task ->
            val kept = merged[task.pageId]
            merged[task.pageId] = if (kept == null) task else listOf(kept, task).maxWith(DUPLICATE_PRIORITY)
        }

        return AssistantTaskSyncResult(pushAndReplace(connection, merged), failedOnFetch = false)
    }

    /**
     * 조회에 성공한 뒤 반영 대기 항목을 보내고, 받은 목록 [merged]에 그 결과를 덮어 저장본을 바꾼 결과 상태
     *
     * [merged]는 보내기 결과로 고쳐 씀.
     */
    private suspend fun pushAndReplace(
        connection: AssistantTaskConnection,
        merged: MutableMap<String, AssistantTask>,
    ): AssistantTaskSyncStatus {
        // 반영 대기 항목을 하나씩 보내고 결과를 바로 저장본에 반영
        val pending = storageCall { repository.getPending() } ?: return recordFailure(AssistantTaskSyncStatus.STORAGE_ERROR)
        // 보낸 결과를 저장본에 반영했거나 저장본을 교체했는지 여부
        // (resolvePending·replaceSynced는 트랜잭션이라 실패하면 저장본이 그대로이므로, 바꾼 적 없이 끝나면 다시 예약하지 않음)
        var storeChanged = false
        try {
            var pushFailure: AssistantTaskSyncStatus? = null
            for (snapshot in pending) {
                // 항목마다 시작 전 취소 확인(추가 요청은 취소돼도 끝까지 마치므로 다음 항목으로 넘어가지 않게 여기서 멈춤)
                currentCoroutineContext().ensureActive()
                when (val result = push(connection, snapshot, merged[snapshot.pageId])) {
                    is PushResult.Resolved -> {
                        // recordPush가 저장본에 반영까지 마친 결과
                        storeChanged = true
                        // 받은 목록에 보내기 결과 덮기(없어진 페이지는 빼고 추가한 페이지는 더함)
                        if (result.task == null) merged.remove(snapshot.pageId) else merged[result.task.pageId] = result.task
                    }
                    is PushResult.Failed -> {
                        pushFailure = result.status
                        if (result.stopsRemaining) break
                    }
                    PushResult.StorageFailed -> return recordFailure(AssistantTaskSyncStatus.STORAGE_ERROR)
                }
            }

            // 반영 대기 행은 남기고 저장본 교체
            storageCall { repository.replaceSynced(merged.values.toList()) } ?: return recordFailure(AssistantTaskSyncStatus.STORAGE_ERROR)
            storeChanged = true

            // 기억한 보내기 실패가 있으면 그 상태, 없으면 성공을 마지막 동기화로 저장
            if (pushFailure != null) return recordFailure(pushFailure)
            settings.saveLastSync(AssistantTaskSyncState(now(), AssistantTaskSyncStatus.SUCCESS))
            return AssistantTaskSyncStatus.SUCCESS
        } finally {
            // 바뀐 저장본으로 시각 알림 다시 예약(보내기 실패·저장 실패·취소로 끝나도 저장본이 바뀌었으면 함께)
            // 추가 결과가 임시 ID 행을 실제 page ID 행으로 바꾼 뒤 취소되면 임시 ID 알람만 남으므로 취소 중에도 끝까지 실행
            if (storeChanged) withContext(NonCancellable) { alarms.reschedule() }
        }
    }

    /**
     * 다음 쪽이 없을 때까지 [requestBody]로 조회하며 하나라도 실패하면 거기서 멈춤
     *
     * 같은 커서가 다시 오거나 [MAX_PAGES]쪽을 받고도 다음 쪽이 남으면 끝없이 넘기지 않도록 요청 오류로 끝냄.
     */
    private suspend fun fetchAll(
        connection: AssistantTaskConnection,
        requestBody: (String?) -> String,
    ): FetchResult<List<AssistantTask>> {
        val tasks = mutableListOf<AssistantTask>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var pageCount = 0
        while (true) {
            val page = when (val result = fetchPage(connection, requestBody(cursor))) {
                is FetchResult.Fetched -> result.value
                is FetchResult.Failed -> return result
            }
            tasks += page.tasks
            pageCount++
            val nextCursor = page.nextCursor.takeIf { page.hasMore } ?: return FetchResult.Fetched(tasks)
            if (!seenCursors.add(nextCursor)) {
                Log.w("[AssistantTaskSynchronizer] 같은 커서 반복: ${pageCount}쪽")
                return FetchResult.Failed(AssistantTaskSyncStatus.REQUEST_ERROR)
            }
            if (pageCount >= MAX_PAGES) {
                Log.w("[AssistantTaskSynchronizer] 쪽 수 상한 초과: ${pageCount}쪽")
                return FetchResult.Failed(AssistantTaskSyncStatus.REQUEST_ERROR)
            }
            cursor = nextCursor
        }
    }

    /**
     * 한 쪽을 조회
     *
     * 429 재시도는 [sendWithRateLimitRetry]가 맡고, 재시도 뒤에도 429면 요청 오류.
     */
    private suspend fun fetchPage(connection: AssistantTaskConnection, requestBody: String): FetchResult<NotionQueryPage> {
        val response = sendWithRateLimitRetry(request = { api.queryDataSource(connection.token, connection.dataSourceId, requestBody) }) { it }
        if (response.statusCode !in 200..299) {
            Log.w("[AssistantTaskSynchronizer] 조회 실패: ${response.statusCode}")
            return FetchResult.Failed(statusOf(response.statusCode))
        }
        val page = response.body?.let { NotionTaskFormat.parseQueryResponse(it) }
        if (page == null) {
            Log.w("[AssistantTaskSynchronizer] 응답 해석 실패: ${response.statusCode}")
            return FetchResult.Failed(AssistantTaskSyncStatus.REQUEST_ERROR)
        }
        return FetchResult.Fetched(page)
    }

    /**
     * 반영 대기 항목 [snapshot] 하나를 노션에 보내고 그 결과를 저장본에 반영
     *
     * 임시 ID면 페이지를 추가하고, 그 밖은 받은 목록의 같은 페이지 [fetched]가 더 나중에 수정됐으면 보내지 않고 노션 값을 따르며
     * 아니면 `완료`만 바꿈. 수정 시각이 같으면 앱 쪽을 보냄.
     * 노션에 닿은 추가 요청의 결과를 취소로 버리면 남은 임시 행을 다음 동기화가 다시 추가해 같은 Todo가 둘이 되므로,
     * 추가 요청 한 번과 그 결과의 [AssistantTaskRepository.resolvePending]은 취소되지 않게 묶음.
     * 완료 체크는 다시 보내도 결과가 같아 묶지 않음.
     */
    private suspend fun push(connection: AssistantTaskConnection, snapshot: AssistantTask, fetched: AssistantTask?): PushResult {
        // 노션 쪽이 더 나중에 수정됐으면 보내지 않음(임시 ID는 받은 목록에 없음)
        if (fetched != null && fetched.lastEditedTime > snapshot.lastEditedTime) {
            return recordPush(snapshot, PushResult.Resolved(fetched))
        }

        // 임시 ID는 페이지 추가, 그 밖은 완료 체크
        return if (snapshot.isLocalOnly) {
            val requestBody = NotionTaskFormat.createPageBody(snapshot, connection.dataSourceId, zone())
            sendWithRateLimitRetry(request = { api.createPage(connection.token, requestBody) }, keepsResultOnCancel = true) { response ->
                recordPush(snapshot, judgePush(snapshot, fetched, response))
            }
        } else {
            val requestBody = NotionTaskFormat.updateDoneBody(snapshot.done)
            sendWithRateLimitRetry(request = { api.updatePage(connection.token, snapshot.pageId, requestBody) }) { response ->
                recordPush(snapshot, judgePush(snapshot, fetched, response))
            }
        }
    }

    /**
     * 보내기 응답 [response]를 판정
     *
     * 받은 page의 종류가 메모면 Todo가 아니라 저장본에서 뺌.
     * 완료를 바꾼 페이지가 404이거나, 받은 목록에 없는 페이지([fetched]가 null)가 400이면 노션에서 지워졌거나
     * 휴지통·보관으로 옮겨졌거나 연결이 풀린 것으로 보고 저장본에서 뺌. 받은 목록에 있는 페이지의 400은 반영 대기로 남김.
     * 그 밖의 4xx와 해석할 수 없는 응답은 그 항목만의 문제라 다음 항목으로 넘어가고, 연결 예외·5xx·401·403·추가 404·429 반복은
     * 남은 항목도 같은 이유로 실패하므로 멈춤.
     */
    private fun judgePush(snapshot: AssistantTask, fetched: AssistantTask?, response: NotionHttpResponse): PushResult {
        val statusCode = response.statusCode
        return when {
            statusCode in 200..299 -> {
                val resolved = response.body?.let { NotionTaskFormat.parsePageResponse(it) }
                when {
                    resolved == null -> {
                        Log.w("[AssistantTaskSynchronizer] 보내기 응답 해석 실패: $statusCode")
                        PushResult.Failed(AssistantTaskSyncStatus.REQUEST_ERROR, stopsRemaining = false)
                    }
                    resolved.kind == AssistantTaskKind.MEMO -> PushResult.Resolved(null)
                    else -> PushResult.Resolved(resolved)
                }
            }
            statusCode == 404 && !snapshot.isLocalOnly -> {
                Log.w("[AssistantTaskSynchronizer] 완료 체크할 페이지 없음: $statusCode")
                PushResult.Resolved(null)
            }
            statusCode == 400 && !snapshot.isLocalOnly && fetched == null -> {
                Log.w("[AssistantTaskSynchronizer] 받은 목록에 없는 페이지의 완료 체크 거절: $statusCode")
                PushResult.Resolved(null)
            }
            else -> {
                Log.w("[AssistantTaskSynchronizer] 보내기 실패: $statusCode")
                val status = statusOf(statusCode)
                PushResult.Failed(status, stopsRemaining = status != AssistantTaskSyncStatus.REQUEST_ERROR || statusCode == 429)
            }
        }
    }

    /** 정리할 결과 [result]를 [snapshot] 행에 반영하며, 반영하지 못하면 [PushResult.StorageFailed] */
    private suspend fun recordPush(snapshot: AssistantTask, result: PushResult): PushResult {
        if (result !is PushResult.Resolved) return result
        return if (storageCall { repository.resolvePending(snapshot, result.task) } != null) result else PushResult.StorageFailed
    }

    /**
     * [request]를 보내 받은 응답을 [handle]로 처리하며, 429이면 안내된 시간만큼 기다려 같은 요청을 최대 [MAX_RATE_LIMIT_RETRIES]번 다시 보냄
     *
     * 안내된 시간이 없으면 1초를 기다리고, 최대 [MAX_RETRY_AFTER_SECONDS]초까지만 기다림.
     * 다시 보내고도 429면 그 응답을 그대로 [handle]에 넘김.
     * [keepsResultOnCancel]이면 요청 한 번과 그 응답의 [handle]을 [NonCancellable]로 묶어 노션에 닿은 요청의 결과를 취소로 버리지 않음.
     * 재시도 대기는 묶지 않아 취소할 수 있음.
     */
    private suspend fun <T : Any> sendWithRateLimitRetry(
        request: suspend () -> NotionHttpResponse,
        keepsResultOnCancel: Boolean = false,
        handle: suspend (NotionHttpResponse) -> T,
    ): T {
        var retryCount = 0
        while (true) {
            // 요청 한 번과, 다시 보내지 않을 응답이면 그 처리까지(다시 보낼 429면 처리 결과 null)
            val canRetry = retryCount < MAX_RATE_LIMIT_RETRIES
            val attempt: suspend () -> Pair<NotionHttpResponse, T?> = {
                val response = request()
                when {
                    response.statusCode != 429 -> response to handle(response)
                    canRetry -> response to null
                    else -> {
                        Log.w("[AssistantTaskSynchronizer] 요청 제한 반복: ${response.statusCode}")
                        response to handle(response)
                    }
                }
            }
            val (response, handled) = if (keepsResultOnCancel) withContext(NonCancellable) { attempt() } else attempt()
            if (handled != null) return handled

            // 안내된 시간만큼 대기 후 다시 보냄
            val waitSeconds = (response.retryAfterSeconds ?: 1L).coerceIn(0L, MAX_RETRY_AFTER_SECONDS)
            delay(waitSeconds * 1000)
            retryCount++
        }
    }

    /** 저장본 호출 [block]을 돌리며 예외는 클래스 이름만 남기고 null로 돌려줌 */
    private suspend fun <T : Any> storageCall(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Log.w("[AssistantTaskSynchronizer] 저장본 처리 실패: ${failure::class.java.simpleName}")
        null
    }

    /** 실패 상태를 마지막 동기화로 남기고 그대로 돌려줌 */
    private suspend fun recordFailure(status: AssistantTaskSyncStatus): AssistantTaskSyncStatus {
        settings.saveLastSync(AssistantTaskSyncState(now(), status))
        return status
    }

    companion object {
        const val DONE_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val MAX_RATE_LIMIT_RETRIES = 3
        const val MAX_RETRY_AFTER_SECONDS = 60L

        /**
         * 조회 하나에서 받는 최대 쪽 수
         *
         * 쪽당 최대 100개라 5,000개까지 받음.
         */
        const val MAX_PAGES = 50
        private val mutex = Mutex()

        /**
         * 같은 pageId가 두 조회에 다 왔을 때 남길 쪽을 고르는 순서
         *
         * 나중에 수정된 쪽이 크고, 수정 시각이 같으면 미완료 쪽이 큼.
         */
        private val DUPLICATE_PRIORITY = compareBy<AssistantTask>({ it.lastEditedTime }, { !it.done })

        /**
         * 응답 상태 코드를 동기화 결과로 바꿈
         *
         * null은 연결 예외, 401·403·404는 인증 오류, 5xx는 서버 오류, 그 밖은 요청 오류.
         */
        internal fun statusOf(statusCode: Int?): AssistantTaskSyncStatus = when (statusCode) {
            null -> AssistantTaskSyncStatus.NETWORK_ERROR
            401, 403, 404 -> AssistantTaskSyncStatus.AUTH_ERROR
            in 500..599 -> AssistantTaskSyncStatus.SERVER_ERROR
            else -> AssistantTaskSyncStatus.REQUEST_ERROR
        }

        /** 하이픈을 빼고 소문자로 맞춘 data source ID */
        private fun normalizeId(dataSourceId: String): String = dataSourceId.replace("-", "").lowercase()
    }
}
