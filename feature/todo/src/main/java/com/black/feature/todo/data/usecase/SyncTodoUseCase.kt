package com.black.feature.todo.data.usecase

import android.content.Context
import com.black.core.network.NetworkResult
import com.black.core.network.RateLimitRetry
import com.black.core.network.logSummary
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.feature.todo.data.TodoRepository
import com.black.feature.todo.data.TodoSettingsRepository
import com.black.feature.todo.data.network.NotionPage
import com.black.feature.todo.data.network.NotionQueryRequest
import com.black.feature.todo.data.network.NotionService
import com.black.feature.todo.data.network.NotionTodoMapper
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoConnection
import com.black.feature.todo.model.TodoSyncState
import com.black.feature.todo.model.TodoSyncStatus
import com.black.feature.todo.notification.TodoReminderRescheduler
import com.black.feature.todo.notification.TodoReminderScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** [SyncTodoUseCase.replaceConnection]이 연결은 저장했지만 저장본을 비우지 못했을 때의 실패 */
class TodoStoreClearException : Exception()

/**
 * [SyncTodoUseCase.syncWithStage]의 결과 상태와 노션 조회 단계에서 실패했는지 여부
 *
 * [failedOnFetch]가 true면 조회가 실패해 반영 대기 항목을 보내지 않았고 저장본도 그대로임.
 * 조회에 성공한 뒤 보내기·저장에서 실패하면 false.
 */
data class TodoSyncResult(val status: TodoSyncStatus, val failedOnFetch: Boolean)

/**
 * 노션 `Todo`를 끝까지 읽고 앱에서 바꾼 항목을 노션에 보낸 뒤 Room 저장본을 바꾸고 결과를 남김
 *
 * 토큰·요청과 응답 본문·할 일 내용은 로그에 남기지 않고 상태 코드와 예외 클래스 이름만 남김.
 * 조회가 하나라도 실패하면 보내지 않고 저장본은 그대로 두고 실패 상태만 남김.
 * 노션 데이터베이스에는 메모도 있지만 앱은 Todo만 다루므로 종류가 메모인 항목은 저장하지 않음([NotionTodoMapper]가 거름).
 * [dispatcher]는 응답 해석을 메인 스레드 밖에서 돌리기 위한 것이며, 테스트는 가상 시간을 쓰도록 테스트 디스패처를 넘김.
 * [zone]은 페이지 추가 요청에 시각 기한을 실을 때와, 받은 시각 기한에 오프셋과 쓸 수 있는 `time_zone`이 모두 없을 때 쓰는 시간대.
 * [alarms]는 저장본을 바꾼 뒤 Todo 시각 알림을 다시 예약하는 데 씀.
 */
class SyncTodoUseCase(
    private val settings: TodoSettingsRepository,
    private val repository: TodoRepository,
    private val service: NotionService,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
    private val alarms: TodoReminderRescheduler,
) {
    @Inject
    constructor(
        settings: TodoSettingsRepository,
        repository: TodoRepository,
        alarms: TodoReminderScheduler,
    ) : this(settings, repository, NotionService.default, Dispatchers.Default, Instant::now, ZoneId::systemDefault, alarms)

    /**
     * Hilt 없이 의존성을 직접 만드는 WorkManager 작업이 쓰는 생성자
     *
     * 주기 동기화 작업과 집 도착 작업이 씀.
     */
    constructor(context: Context) : this(
        TodoSettingsRepository(context.applicationContext),
        TodoRepository(context.applicationContext),
        TodoReminderScheduler(context.applicationContext),
    )

    /** 조회 결과이며 성공이면 [Fetched.value], 실패면 마지막 동기화로 남길 [Failed.status]를 담음 */
    private sealed interface FetchResult<out T> {
        class Fetched<T>(val value: T) : FetchResult<T>
        class Failed(val status: TodoSyncStatus) : FetchResult<Nothing>
    }

    /** 조회 한 쪽의 Todo와, 다음 쪽이 있으면 그 커서 */
    private class FetchedPage(val todos: List<Todo>, val nextCursor: String?)

    /** 반영 대기 항목 하나를 노션에 보낸 결과 */
    private sealed interface PushResult {
        /** 노션 반영이 끝나 [task]로 정리할 결과이며, 노션에서 페이지가 없어졌거나 Todo가 아니면 [task]는 null */
        class Resolved(val task: Todo?) : PushResult

        /** 반영 대기로 남길 실패이며, [stopsRemaining]이면 남은 항목도 보내지 않음 */
        class Failed(val status: TodoSyncStatus, val stopsRemaining: Boolean) : PushResult

        /** 노션 반영 결과를 저장본에 적지 못한 실패 */
        data object StorageFailed : PushResult
    }

    /**
     * 동기화 한 번을 돌리고 결과 상태를 돌려줌
     *
     * 프로세스 전역 [Mutex]로 화면과 Worker가 동시에 돌지 않게 하며, 설정이 없으면 마지막 동기화를 남기지 않음.
     * 보낸 결과를 저장본에 반영했거나 저장본을 교체했으면 보내기 실패·저장 실패·취소로 끝나도 시각 알림을 한 번 다시 예약함.
     */
    suspend fun sync(): TodoSyncStatus = syncWithStage().status

    /**
     * [sync]와 같은 동기화 한 번을 돌리고 결과 상태와 함께 노션 조회 단계에서 실패했는지 돌려줌
     *
     * 집 도착 작업이 조회 단계의 네트워크 실패에만 다시 동기화하는 데 씀.
     * 보내기 단계 실패 뒤 다시 돌리면 응답만 끊긴 추가 요청을 또 보내 같은 Todo가 여러 개 생길 수 있음.
     */
    suspend fun syncWithStage(): TodoSyncResult =
        // 잠금을 디스패처 안에서 잡아, 동기화가 끝나면 호출자 디스패처로 돌아가기를 기다리지 않고 바로 풂
        withContext(dispatcher) { mutex.withLock { syncLocked() } }

    /**
     * 노션 연결을 [connection]으로 바꿈
     *
     * data source ID가 바뀌면 다른 데이터베이스라 저장본과 마지막 동기화도 비우고, 토큰만 바뀌면 같은 데이터베이스라 그대로 둠.
     * 저장본을 비우려 했으면 성공·실패·취소와 상관없이 남은 저장본으로 시각 알림을 다시 예약해 옛 데이터베이스의 알람을 정리함.
     * 하이픈 유무와 대소문자만 다른 ID는 같은 데이터베이스로 봄.
     * [sync]와 같은 잠금 안에서 처리해 진행 중인 동기화가 옛 연결로 저장본을 다시 채우지 못하게 함.
     * 결과는 연결 저장의 성공 여부이며, 연결은 저장했지만 저장본을 비우지 못했으면 [TodoStoreClearException] 실패.
     * 연결 저장부터 다시 예약까지는 취소돼도 끝까지 마친 뒤 취소를 전파함.
     */
    suspend fun replaceConnection(connection: TodoConnection): Result<Unit> = mutex.withLock {
        // 저장 전 연결 확인
        val previous = settings.getConnection()
        // 연결 저장부터 저장본 비우기, 다시 예약까지 취소되지 않게 묶음(모두 기기 안의 짧은 작업)
        // 취소가 연결 저장 안에서 오면 DataStore 쓰기는 커밋된 채 저장본 비우기와 다시 예약을 건너뛰어 새 연결에 옛 데이터베이스의 저장본이 남기 때문
        val result = withContext(NonCancellable) {
            // 연결 저장
            val saved = settings.saveConnection(connection)
            // 저장에 실패했거나 같은 데이터베이스면 저장본 그대로
            if (saved.isFailure || normalizeId(previous.dataSourceId) == normalizeId(connection.dataSourceId)) {
                return@withContext saved
            }
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
     * 연결은 이미 저장된 뒤라 저장본 삭제 실패는 연결 저장 실패와 구분해 [TodoStoreClearException]으로 돌려줌.
     * 반영 대기가 아닌 옛 항목은 새 연결의 다음 동기화가 저장본을 바꿀 때 사라지지만, 반영 대기 항목은 남아 새 연결로 보내질 수 있음.
     */
    private suspend fun clearStore(): Result<Unit> {
        // 저장본 비우기
        val cleared = storageCall { repository.clear() } != null
        // 옛 데이터베이스의 마지막 동기화 비우기
        settings.clearLastSync()
        return if (cleared) Result.success(Unit) else Result.failure(TodoStoreClearException())
    }

    private suspend fun syncLocked(): TodoSyncResult {
        // 연결 설정 확인
        val connection = settings.getConnection()
        if (!connection.isConfigured) {
            return TodoSyncResult(TodoSyncStatus.NOT_CONFIGURED, failedOnFetch = false)
        }

        // 미완료 조회와 최근 완료 조회를 끝까지 받음
        val since = now() - DONE_WINDOW.toJavaDuration()
        val openTasks = when (val result = fetchAll(connection) { cursor -> NotionTodoMapper.openTodosQuery(cursor) }) {
            is FetchResult.Fetched -> result.value
            is FetchResult.Failed -> return TodoSyncResult(recordFailure(result.status), failedOnFetch = true)
        }
        val doneTasks = when (val result = fetchAll(connection) { cursor -> NotionTodoMapper.recentlyDoneQuery(since, cursor) }) {
            is FetchResult.Fetched -> result.value
            is FetchResult.Failed -> return TodoSyncResult(recordFailure(result.status), failedOnFetch = true)
        }

        // 두 조회 사이에 수정돼 양쪽에 다 온 페이지는 나중에 수정된 쪽, 수정 시각이 같으면 미완료 쪽을 남김
        val merged = LinkedHashMap<String, Todo>()
        (openTasks + doneTasks).forEach { task ->
            val kept = merged[task.pageId]
            merged[task.pageId] = if (kept == null) task else listOf(kept, task).maxWith(DUPLICATE_PRIORITY)
        }

        return TodoSyncResult(pushAndReplace(connection, merged), failedOnFetch = false)
    }

    /**
     * 조회에 성공한 뒤 반영 대기 항목을 보내고, 받은 목록 [merged]에 그 결과를 덮어 저장본을 바꾼 결과 상태
     *
     * [merged]는 보내기 결과로 고쳐 씀.
     */
    private suspend fun pushAndReplace(
        connection: TodoConnection,
        merged: MutableMap<String, Todo>,
    ): TodoSyncStatus {
        // 반영 대기 항목을 하나씩 보내고 결과를 바로 저장본에 반영
        val pending = storageCall { repository.getPending() } ?: return recordFailure(TodoSyncStatus.STORAGE_ERROR)
        // 보낸 결과를 저장본에 반영했거나 저장본을 교체했는지 여부
        // (resolvePending·replaceSynced는 트랜잭션이라 실패하면 저장본이 그대로이므로, 바꾼 적 없이 끝나면 다시 예약하지 않음)
        var storeChanged = false
        try {
            var pushFailure: TodoSyncStatus? = null
            for (snapshot in pending) {
                // 항목마다 시작 전 취소 확인(추가 요청은 취소돼도 끝까지 마치므로 다음 항목으로 넘어가지 않게 여기서 멈춤)
                currentCoroutineContext().ensureActive()
                when (val result = push(connection, snapshot, merged[snapshot.pageId])) {
                    is PushResult.Resolved -> {
                        // recordPush가 저장본에 반영까지 마친 결과
                        storeChanged = true
                        // 받은 목록에 보내기 결과 덮기(없어진 페이지는 빼고 추가한 페이지는 더함)
                        if (result.task == null) {
                            merged.remove(snapshot.pageId)
                        } else {
                            merged[result.task.pageId] = result.task
                        }
                    }
                    is PushResult.Failed -> {
                        pushFailure = result.status
                        if (result.stopsRemaining) {
                            break
                        }
                    }
                    PushResult.StorageFailed -> return recordFailure(TodoSyncStatus.STORAGE_ERROR)
                }
            }

            // 반영 대기 행은 남기고 저장본 교체
            storageCall { repository.replaceSynced(merged.values.toList()) } ?: return recordFailure(TodoSyncStatus.STORAGE_ERROR)
            storeChanged = true

            // 기억한 보내기 실패가 있으면 그 상태, 없으면 성공을 마지막 동기화로 저장
            if (pushFailure != null) {
                return recordFailure(pushFailure)
            }
            settings.saveLastSync(TodoSyncState(now().toEpochMilli(), TodoSyncStatus.SUCCESS))
            return TodoSyncStatus.SUCCESS
        } finally {
            // 바뀐 저장본으로 시각 알림 다시 예약(보내기 실패·저장 실패·취소로 끝나도 저장본이 바뀌었으면 함께)
            // 추가 결과가 임시 ID 행을 실제 page ID 행으로 바꾼 뒤 취소되면 임시 ID 알람만 남으므로 취소 중에도 끝까지 실행
            if (storeChanged) {
                withContext(NonCancellable) { alarms.reschedule() }
            }
        }
    }

    /**
     * 다음 쪽이 없을 때까지 [request]로 조회하며 하나라도 실패하면 거기서 멈춤
     *
     * 같은 커서가 다시 오거나 [MAX_PAGES]쪽을 받고도 다음 쪽이 남으면 끝없이 넘기지 않도록 요청 오류로 끝냄.
     */
    private suspend fun fetchAll(
        connection: TodoConnection,
        request: (String?) -> NotionQueryRequest,
    ): FetchResult<List<Todo>> {
        val tasks = mutableListOf<Todo>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var pageCount = 0
        while (true) {
            val page = when (val result = fetchPage(connection, request(cursor))) {
                is FetchResult.Fetched -> result.value
                is FetchResult.Failed -> return result
            }
            tasks += page.todos
            pageCount++
            val nextCursor = page.nextCursor ?: return FetchResult.Fetched(tasks)
            if (!seenCursors.add(nextCursor)) {
                Log.w("[SyncTodoUseCase] 같은 커서 반복: ${pageCount}쪽")
                return FetchResult.Failed(TodoSyncStatus.REQUEST_ERROR)
            }
            if (pageCount >= MAX_PAGES) {
                Log.w("[SyncTodoUseCase] 쪽 수 상한 초과: ${pageCount}쪽")
                return FetchResult.Failed(TodoSyncStatus.REQUEST_ERROR)
            }
            cursor = nextCursor
        }
    }

    /**
     * 한 쪽을 조회
     *
     * 429 재시도는 [RateLimitRetry.send]가 맡고, 다시 보낸 뒤에도 429이거나 안내된 대기 시간이 [MAX_RETRY_WAIT]를 넘으면 요청 오류.
     * 조회 결과가 아닌 본문(노션 오류 본문, 빈 객체)도 요청 오류.
     */
    private suspend fun fetchPage(connection: TodoConnection, request: NotionQueryRequest): FetchResult<FetchedPage> {
        // 요청 제한이면 기다렸다가 다시 보냄
        val result = RateLimitRetry.send(MAX_RATE_LIMIT_RETRIES, MAX_RETRY_WAIT) {
            service.queryDataSource(NotionService.authorization(connection.token), connection.dataSourceId, request)
        }
        if (result !is NetworkResult.Success) {
            Log.w("[SyncTodoUseCase] 조회 실패: ${result.logSummary()}")
            return FetchResult.Failed(statusOf(result))
        }

        // 조회 결과 변환
        val response = result.data
        val todos = NotionTodoMapper.toTodos(response, zone())
        if (todos == null) {
            Log.w("[SyncTodoUseCase] 응답 해석 실패: ${result.statusCode}")
            return FetchResult.Failed(TodoSyncStatus.REQUEST_ERROR)
        }
        return FetchResult.Fetched(FetchedPage(todos, response.nextCursor.takeIf { response.hasMore }))
    }

    /**
     * 반영 대기 항목 [snapshot] 하나를 노션에 보내고 그 결과를 저장본에 반영
     *
     * 임시 ID면 페이지를 추가하고, 그 밖은 받은 목록의 같은 페이지 [fetched]가 더 나중에 수정됐으면 보내지 않고 노션 값을 따르며
     * 아니면 `완료`만 바꿈. 수정 시각이 같으면 앱 쪽을 보냄.
     * 노션에 닿은 추가 요청의 결과를 취소로 버리면 남은 임시 행을 다음 동기화가 다시 추가해 같은 Todo가 둘이 되므로,
     * 추가 요청 한 번과 그 결과의 [TodoRepository.resolvePending]은 취소되지 않게 묶음.
     * 완료 체크는 다시 보내도 결과가 같아 묶지 않음.
     */
    private suspend fun push(connection: TodoConnection, snapshot: Todo, fetched: Todo?): PushResult {
        // 노션 쪽이 더 나중에 수정됐으면 보내지 않음(임시 ID는 받은 목록에 없음)
        if (fetched != null && fetched.lastEditedTime > snapshot.lastEditedTime) {
            return recordPush(snapshot, PushResult.Resolved(fetched))
        }

        // 임시 ID는 페이지 추가, 그 밖은 완료 체크
        val authorization = NotionService.authorization(connection.token)
        return if (snapshot.isLocalOnly) {
            val request = NotionTodoMapper.createPageRequest(snapshot, connection.dataSourceId, zone())
            RateLimitRetry.send(
                maxRetries = MAX_RATE_LIMIT_RETRIES,
                maxWait = MAX_RETRY_WAIT,
                attemptContext = NonCancellable,
                request = { service.createPage(authorization, request) },
            ) { result ->
                recordPush(snapshot, judgePush(snapshot, fetched, result))
            }
        } else {
            val request = NotionTodoMapper.updateDoneRequest(snapshot.done)
            RateLimitRetry.send(
                maxRetries = MAX_RATE_LIMIT_RETRIES,
                maxWait = MAX_RETRY_WAIT,
                request = { service.updatePage(authorization, snapshot.pageId, request) },
            ) { result ->
                recordPush(snapshot, judgePush(snapshot, fetched, result))
            }
        }
    }

    /**
     * 보내기 결과 [result]를 판정
     *
     * 받은 page의 종류가 메모면 Todo가 아니라 저장본에서 뺌. 페이지가 아니거나 휴지통·보관 페이지면 해석할 수 없는 응답으로 봄.
     * 완료를 바꾼 페이지가 404이거나, 받은 목록에 없는 페이지([fetched]가 null)가 400이면 노션에서 지워졌거나
     * 휴지통·보관으로 옮겨졌거나 연결이 풀린 것으로 보고 저장본에서 뺌. 받은 목록에 있는 페이지의 400은 반영 대기로 남김.
     * 그 밖의 4xx와 해석할 수 없는 응답은 그 항목만의 문제라 다음 항목으로 넘어가고, 연결 예외·5xx·401·403·추가 404·429는
     * 남은 항목도 같은 이유로 실패하므로 멈춤. 429는 다시 보낸 뒤에도 429이거나 안내된 대기 시간이 [MAX_RETRY_WAIT]를 넘은 경우.
     */
    private fun judgePush(snapshot: Todo, fetched: Todo?, result: NetworkResult<NotionPage>): PushResult {
        // 받은 page 반영(메모면 null이라 저장본에서 뺌)
        if (result is NetworkResult.Success) {
            if (!NotionTodoMapper.isUsablePage(result.data)) {
                Log.w("[SyncTodoUseCase] 보내기 응답 해석 실패: ${result.statusCode}")
                return PushResult.Failed(TodoSyncStatus.REQUEST_ERROR, stopsRemaining = false)
            }
            return PushResult.Resolved(NotionTodoMapper.toTodo(result.data, zone()))
        }

        // 실패 판정
        val statusCode = (result as? NetworkResult.HttpFailure)?.statusCode
        return when {
            statusCode == 404 && !snapshot.isLocalOnly -> {
                Log.w("[SyncTodoUseCase] 완료 체크할 페이지 없음: $statusCode")
                PushResult.Resolved(null)
            }
            statusCode == 400 && !snapshot.isLocalOnly && fetched == null -> {
                Log.w("[SyncTodoUseCase] 받은 목록에 없는 페이지의 완료 체크 거절: $statusCode")
                PushResult.Resolved(null)
            }
            else -> {
                Log.w("[SyncTodoUseCase] 보내기 실패: ${result.logSummary()}")
                val status = statusOf(result)
                val rateLimited = statusCode == RateLimitRetry.STATUS_TOO_MANY_REQUESTS
                PushResult.Failed(status, stopsRemaining = status != TodoSyncStatus.REQUEST_ERROR || rateLimited)
            }
        }
    }

    /** 정리할 결과 [result]를 [snapshot] 행에 반영하며, 반영하지 못하면 [PushResult.StorageFailed] */
    private suspend fun recordPush(snapshot: Todo, result: PushResult): PushResult {
        if (result !is PushResult.Resolved) {
            return result
        }
        return if (storageCall { repository.resolvePending(snapshot, result.task) } != null) result else PushResult.StorageFailed
    }

    /** 저장본 호출 [block]을 돌리며 예외는 클래스 이름만 남기고 null로 돌려줌 */
    private suspend fun <T : Any> storageCall(block: suspend () -> T): T? = runSuspendCatching { block() }
        .onFailure { Log.w("[SyncTodoUseCase] 저장본 처리 실패: ${it::class.java.simpleName}") }
        .getOrNull()

    /** 실패 상태를 마지막 동기화로 남기고 그대로 돌려줌 */
    private suspend fun recordFailure(status: TodoSyncStatus): TodoSyncStatus {
        settings.saveLastSync(TodoSyncState(now().toEpochMilli(), status))
        return status
    }

    companion object {
        /** 최근 완료 조회가 거슬러 올라가는 수정 시각 범위 */
        val DONE_WINDOW: Duration = 7.days
        const val MAX_RATE_LIMIT_RETRIES = 3

        /**
         * 429 응답에서 기다릴 최대 시간
         *
         * `Retry-After`가 이보다 길면 기다리지 않고 그 429를 실패로 판정함.
         */
        val MAX_RETRY_WAIT: Duration = 60.seconds

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
        private val DUPLICATE_PRIORITY = compareBy<Todo>({ it.lastEditedTime }, { !it.done })

        /**
         * 요청 결과를 동기화 결과로 바꿈
         *
         * 연결 실패는 네트워크 오류, 해석 실패는 요청 오류.
         * 2xx가 아닌 응답은 401·403·404면 인증 오류, 5xx면 서버 오류, 그 밖은 요청 오류.
         */
        internal fun statusOf(result: NetworkResult<*>): TodoSyncStatus = when (result) {
            is NetworkResult.Success -> TodoSyncStatus.SUCCESS
            is NetworkResult.ConnectionFailure -> TodoSyncStatus.NETWORK_ERROR
            is NetworkResult.ParseFailure -> TodoSyncStatus.REQUEST_ERROR
            is NetworkResult.HttpFailure -> when (result.statusCode) {
                401, 403, 404 -> TodoSyncStatus.AUTH_ERROR
                in 500..599 -> TodoSyncStatus.SERVER_ERROR
                else -> TodoSyncStatus.REQUEST_ERROR
            }
        }

        /** 하이픈을 빼고 소문자로 맞춘 data source ID */
        private fun normalizeId(dataSourceId: String): String = dataSourceId.replace("-", "").lowercase()
    }
}
