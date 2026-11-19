package com.black.feature.todo.data.usecase

import android.util.Log
import com.black.core.network.NetworkResult
import com.black.core.util.JsonUtil
import com.black.feature.todo.data.TodoRepository
import com.black.feature.todo.data.TodoSettingsRepository
import com.black.feature.todo.data.network.NotionCreatePageRequest
import com.black.feature.todo.data.network.NotionPage
import com.black.feature.todo.data.network.NotionQueryRequest
import com.black.feature.todo.data.network.NotionQueryResponse
import com.black.feature.todo.data.network.NotionService
import com.black.feature.todo.data.network.NotionTodoMapper
import com.black.feature.todo.data.network.NotionUpdatePageRequest
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoConnection
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.feature.todo.model.TodoSyncState
import com.black.feature.todo.model.TodoSyncStatus
import com.black.feature.todo.notification.TodoReminderRescheduler
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/** [SyncTodoUseCase]의 조회 반복·실패 판정·반영 대기 보내기·저장본 교체·연결 교체 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SyncTodoUseCaseTest : BaseTest() {

    @MockK
    lateinit var settings: TodoSettingsRepository

    @MockK
    lateinit var repository: TodoRepository

    @MockK
    lateinit var alarms: TodoReminderRescheduler

    private val nowMillis = 1794971520000L
    private val seoul = ZoneId.of("Asia/Seoul")

    /** 완료 체크 요청 한 건 */
    private data class UpdateRequest(val authorization: String, val pageId: String, val request: NotionUpdatePageRequest)

    /**
     * 결과를 차례로 돌려주고 받은 요청을 기록하는 가짜 노션 서비스
     *
     * 조회는 [queryResults], 페이지 추가는 [createResults], 완료 체크는 [updateResults]에서 차례로 돌려줌.
     */
    private class FakeNotionService(
        private val queryResults: List<suspend () -> NetworkResult<NotionQueryResponse>>,
        private val createResults: List<suspend () -> NetworkResult<NotionPage>> = emptyList(),
        private val updateResults: List<NetworkResult<NotionPage>> = emptyList(),
    ) : NotionService {
        val queryRequests = mutableListOf<NotionQueryRequest>()
        val createRequests = mutableListOf<NotionCreatePageRequest>()
        val updateRequests = mutableListOf<UpdateRequest>()

        constructor(vararg queryResults: NetworkResult<NotionQueryResponse>) : this(queryResults.map { result -> suspend { result } })

        override suspend fun queryDataSource(
            authorization: String,
            dataSourceId: String,
            request: NotionQueryRequest,
        ): NetworkResult<NotionQueryResponse> {
            queryRequests += request
            return queryResults[queryRequests.size - 1]()
        }

        override suspend fun createPage(authorization: String, request: NotionCreatePageRequest): NetworkResult<NotionPage> {
            createRequests += request
            return createResults[createRequests.size - 1]()
        }

        override suspend fun updatePage(
            authorization: String,
            pageId: String,
            request: NotionUpdatePageRequest,
        ): NetworkResult<NotionPage> {
            updateRequests += UpdateRequest(authorization, pageId, request)
            return updateResults[updateRequests.size - 1]
        }
    }

    @Before
    fun setupDefaults() {
        coEvery { settings.getConnection() } returns TodoConnection("tok", "ds-1")
        coEvery { settings.saveLastSync(any()) } returns Result.success(Unit)
        coEvery { repository.replaceSynced(any()) } just Runs
        coEvery { repository.getPending() } returns emptyList()
        coEvery { repository.resolvePending(any(), any()) } just Runs
        coEvery { settings.saveConnection(any()) } returns Result.success(Unit)
        coEvery { settings.clearLastSync() } returns Result.success(Unit)
        coEvery { repository.clear() } just Runs
        coEvery { alarms.reschedule() } just Runs
    }

    /**
     * 응답 해석 디스패처를 테스트 디스패처로 두어 runTest의 가상 시간을 함께 쓰는 동기화기
     *
     * 추가 요청 본문과 시간대 없는 기한 해석의 시간대는 서울.
     */
    private fun synchronizer(service: NotionService) = SyncTodoUseCase(
        settings,
        repository,
        service,
        testDispatcherRule.testDispatcher,
        now = { Instant.ofEpochMilli(nowMillis) },
        zone = { seoul },
        alarms = alarms,
    )

    /** 조회 결과 [queries]에 이어 추가 결과 [creates]와 완료 체크 결과 [updates]를 차례로 돌려주는 가짜 서비스 */
    private fun pushService(
        queries: List<NetworkResult<NotionQueryResponse>>,
        creates: List<NetworkResult<NotionPage>> = emptyList(),
        updates: List<NetworkResult<NotionPage>> = emptyList(),
    ) = FakeNotionService(queries.map { result -> suspend { result } }, creates.map { result -> suspend { result } }, updates)

    /** [kind]가 null이면 `종류` 속성을 넣지 않음 */
    private fun pageJson(
        id: String,
        done: Boolean = false,
        lastEditedTime: String = "2026-11-18T03:12:00.000Z",
        kind: String? = null,
        extra: String = "",
    ): String {
        val kindProperty = if (kind == null) "" else ""","종류":{"type":"select","select":{"name":"$kind"}}"""
        return """{"object":"page","id":"$id","last_edited_time":"$lastEditedTime","url":"https://www.notion.so/$id",$extra""" +
            """"properties":{"이름":{"type":"title","title":[{"plain_text":"$id"}]},"완료":{"checkbox":$done}$kindProperty}}"""
    }

    /** 조회 성공 결과이며 [nextCursor]가 있으면 다음 쪽이 있음 */
    private fun ok(pages: List<String>, nextCursor: String? = null): NetworkResult<NotionQueryResponse> {
        val cursor = if (nextCursor == null) "null" else "\"$nextCursor\""
        val body = """{"object":"list","results":[${pages.joinToString(",")}],"has_more":${nextCursor != null},"next_cursor":$cursor}"""
        return NetworkResult.Success(JsonUtil.kotlinSerialization.decodeFromString(NotionQueryResponse.serializer(), body), 200, headersOf())
    }

    /** 본문 [json]으로 온 페이지 추가·수정 성공 결과 */
    private fun pageResult(json: String): NetworkResult<NotionPage> =
        NetworkResult.Success(JsonUtil.kotlinSerialization.decodeFromString(NotionPage.serializer(), json), 200, headersOf())

    /** 페이지 추가·수정 성공 결과 */
    private fun pageResponse(id: String, done: Boolean = false, lastEditedTime: String = "2026-11-18T03:12:00.000Z") =
        pageResult(pageJson(id, done, lastEditedTime))

    /** 2xx가 아닌 응답이며 [retryAfter]가 있으면 `Retry-After` 헤더로 실음 */
    private fun httpFailure(statusCode: Int, retryAfter: String? = null) =
        NetworkResult.HttpFailure(statusCode, if (retryAfter == null) headersOf() else headersOf("Retry-After", retryAfter))

    private fun connectionFailure() = NetworkResult.ConnectionFailure(IOException())

    private fun parseFailure() = NetworkResult.ParseFailure(SerializationException())

    private fun task(id: String, done: Boolean = false, lastEditedTime: Long = 1794971520000) = Todo(
        pageId = id,
        title = id,
        done = done,
        due = null,
        reminder = TodoReminder.NONE,
        content = "",
        lastEditedTime = Instant.ofEpochMilli(lastEditedTime),
        url = "https://www.notion.so/$id",
    )

    private fun parse(body: String): JsonElement = JsonUtil.kotlinSerialization.parseToJsonElement(body)

    /**
     * 설정이 없으면 API를 부르지 않는지 검증
     *
     * Given: 빈 연결
     * When: sync 호출
     * Then: NOT_CONFIGURED, service 호출 0회, replaceSynced·saveLastSync 호출 없음
     */
    @Test
    fun test_01_notConfiguredSkipsApi() = runTest {
        /** Given **/
        coEvery { settings.getConnection() } returns TodoConnection("", "")
        val service = FakeNotionService()

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.NOT_CONFIGURED, status)
        assertEquals(0, service.queryRequests.size)
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
        coVerify(exactly = 0) { settings.saveLastSync(any()) }
    }

    /**
     * 커서를 따라 모든 쪽을 받아 저장본을 교체하는지 검증
     *
     * Given: 미완료 1쪽(다음 쪽 c1)·미완료 2쪽·완료 1쪽
     * When: sync 호출
     * Then: 세 쪽 항목을 합쳐 replaceSynced, 첫 요청은 커서 없고 두 번째 요청의 start_cursor c1, SUCCESS 저장
     */
    @Test
    fun test_02_followsCursorsAndReplaces() = runTest {
        /** Given **/
        val service = FakeNotionService(
            ok(listOf(pageJson("a")), nextCursor = "c1"),
            ok(listOf(pageJson("b"))),
            ok(listOf(pageJson("c", done = true))),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        assertEquals(3, service.queryRequests.size)
        assertNull(service.queryRequests[0].startCursor)
        assertEquals("c1", service.queryRequests[1].startCursor)
        coVerify { repository.replaceSynced(listOf(task("a"), task("b"), task("c", done = true))) }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.SUCCESS)) }
    }

    /**
     * 중간 실패 시 저장본을 건드리지 않는지 검증
     *
     * Given: 미완료 1쪽 성공 뒤 2쪽이 연결 예외
     * When: sync 호출
     * Then: NETWORK_ERROR, replaceSynced 호출 없음, NETWORK_ERROR 저장
     */
    @Test
    fun test_03_failureMidwayKeepsStore() = runTest {
        /** Given **/
        val service = FakeNotionService(ok(listOf(pageJson("a")), nextCursor = "c1"), connectionFailure())

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.NETWORK_ERROR, status)
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.NETWORK_ERROR)) }
    }

    /**
     * 401·403·404가 인증 오류로 판정되는지 검증
     *
     * Given: 각각 401·403·404를 돌려주는 service
     * When: sync 호출
     * Then: 모두 AUTH_ERROR
     */
    @Test
    fun test_04_authErrors() = runTest {
        listOf(401, 403, 404).forEach { statusCode ->
            /** Given **/
            val service = FakeNotionService(httpFailure(statusCode))

            /** When **/
            val status = synchronizer(service).sync()

            /** Then **/
            assertEquals("status=$statusCode", TodoSyncStatus.AUTH_ERROR, status)
        }
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
    }

    /**
     * 400과 해석 불가 본문이 요청 오류로 판정되는지 검증
     *
     * Given: 400 응답 또는 본문이 "{"인 200 응답
     * When: sync 호출
     * Then: 모두 REQUEST_ERROR
     */
    @Test
    fun test_05_badRequestIsRequestError() = runTest {
        listOf(httpFailure(400), parseFailure()).forEach { response ->
            /** Given **/
            val service = FakeNotionService(response)

            /** When **/
            val status = synchronizer(service).sync()

            /** Then **/
            assertEquals(TodoSyncStatus.REQUEST_ERROR, status)
        }
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
    }

    /**
     * 429이면 안내된 시간만큼 기다린 뒤 다시 보내는지 검증
     *
     * Given: 429(Retry-After 2) 뒤 미완료·완료 200 응답
     * When: sync 호출
     * Then: SUCCESS, 가상 시간 2000ms 이상 진행, 요청 3회
     */
    @Test
    fun test_06_rateLimitWaitsThenRetries() = runTest {
        /** Given **/
        val service = FakeNotionService(httpFailure(429, retryAfter = "2"), ok(listOf(pageJson("a"))), ok(emptyList()))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        assertTrue(currentTime >= 2000)
        assertEquals(3, service.queryRequests.size)
    }

    /**
     * 429가 한도를 넘어 반복되면 요청 오류로 끝나는지 검증
     *
     * Given: 429가 계속 돌아오는 service
     * When: sync 호출
     * Then: REQUEST_ERROR, 요청 MAX_RATE_LIMIT_RETRIES + 1회, replaceSynced 호출 없음
     */
    @Test
    fun test_07_repeatedRateLimitIsRequestError() = runTest {
        /** Given **/
        val service = FakeNotionService(*Array(10) { httpFailure(429) })

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.REQUEST_ERROR, status)
        assertEquals(SyncTodoUseCase.MAX_RATE_LIMIT_RETRIES + 1, service.queryRequests.size)
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
    }

    /**
     * 동시에 부른 sync가 하나씩 도는지 검증
     *
     * Given: 첫 응답을 붙잡아 둔 service
     * When: sync 두 개를 띄움
     * Then: 첫 sync가 끝나기 전 service 호출 1회, 이후 모두 완료
     */
    @Test
    fun test_08_concurrentSyncRunsOneAtATime() = runTest {
        /** Given **/
        val gate = CompletableDeferred<NetworkResult<NotionQueryResponse>>()
        val service = FakeNotionService(
            listOf(
                { gate.await() },
                { ok(emptyList()) },
                { ok(emptyList()) },
                { ok(emptyList()) },
            )
        )
        val target = synchronizer(service)
        try {
            /** When **/
            val first = launch { target.sync() }
            val second = launch { target.sync() }
            advanceUntilIdle()

            /** Then **/
            assertEquals(1, service.queryRequests.size)
            gate.complete(ok(emptyList()))
            advanceUntilIdle()
            first.join()
            second.join()
            assertEquals(4, service.queryRequests.size)
        } finally {
            // 단언이 실패해도 붙잡힌 sync가 프로세스 전역 잠금을 쥔 채 남지 않게 풂
            gate.complete(ok(emptyList()))
        }
    }

    /**
     * 완료 조회가 7일 창을 쓰는지 검증
     *
     * Given: 미완료·완료 각 1쪽
     * When: sync 호출
     * Then: 완료 조회의 on_or_after가 now(2026-11-18T03:12:00.000Z) - DONE_WINDOW인 2026-11-11T03:12:00.000Z
     */
    @Test
    fun test_09_doneQueryUsesSevenDayWindow() = runTest {
        /** Given **/
        val service = FakeNotionService(ok(emptyList()), ok(emptyList()))

        /** When **/
        synchronizer(service).sync()

        /** Then **/
        val onOrAfter = service.queryRequests[1].filter.and!![1].lastEditedTime!!.onOrAfter
        assertEquals("2026-11-11T03:12:00.000Z", onOrAfter)
    }

    /**
     * 같은 pageId가 두 조회에 다 오면 나중에 수정된 쪽, 수정 시각이 같으면 미완료 쪽이 남는지 검증
     *
     * Given: 미완료 조회에 a·b(미완료, 03:12), 완료 조회에 a(완료, 03:20)·b(완료, 03:12)
     * When: sync 호출
     * Then: replaceSynced에 a는 나중에 수정된 완료, b는 같은 시각이라 미완료로 하나씩
     */
    @Test
    fun test_10_newerEditWinsOnDuplicatePageId() = runTest {
        /** Given **/
        val service = FakeNotionService(
            ok(listOf(pageJson("a"), pageJson("b"))),
            ok(listOf(pageJson("a", done = true, lastEditedTime = "2026-11-18T03:20:00.000Z"), pageJson("b", done = true))),
        )

        /** When **/
        synchronizer(service).sync()

        /** Then **/
        coVerify { repository.replaceSynced(listOf(task("a", done = true, lastEditedTime = 1794972000000), task("b"))) }
    }

    /**
     * 요청 결과 판정표 검증
     *
     * Given: 연결 실패, 해석 실패, 성공, 상태 코드 401·403·404·500·503·599·400·409·429의 HttpFailure
     * When: statusOf 호출
     * Then: NETWORK_ERROR, REQUEST_ERROR, SUCCESS, AUTH_ERROR 3개, SERVER_ERROR 3개, REQUEST_ERROR 3개
     */
    @Test
    fun test_11_statusOfMapsResults() = runTest {
        /** Given / When / Then **/
        assertEquals(TodoSyncStatus.NETWORK_ERROR, SyncTodoUseCase.statusOf(connectionFailure()))
        assertEquals(TodoSyncStatus.REQUEST_ERROR, SyncTodoUseCase.statusOf(parseFailure()))
        assertEquals(TodoSyncStatus.SUCCESS, SyncTodoUseCase.statusOf(pageResponse("a")))
        listOf(401, 403, 404).forEach { assertEquals(TodoSyncStatus.AUTH_ERROR, SyncTodoUseCase.statusOf(httpFailure(it))) }
        listOf(500, 503, 599).forEach { assertEquals(TodoSyncStatus.SERVER_ERROR, SyncTodoUseCase.statusOf(httpFailure(it))) }
        listOf(400, 409, 429).forEach { assertEquals(TodoSyncStatus.REQUEST_ERROR, SyncTodoUseCase.statusOf(httpFailure(it))) }
    }

    /**
     * data source ID가 바뀌면 연결 저장 뒤 저장본과 마지막 동기화를 비우는지 검증
     *
     * Given: 저장된 연결 ("tok", "ds-1")
     * When: replaceConnection(("tok", "ds-2"))
     * Then: 성공, saveConnection → clear → clearLastSync 순서로 호출
     */
    @Test
    fun test_12_replaceConnectionWithNewDataSourceClearsStore() = runTest {
        /** Given **/
        val target = synchronizer(FakeNotionService())

        /** When **/
        val result = target.replaceConnection(TodoConnection("tok", "ds-2"))

        /** Then **/
        assertTrue(result.isSuccess)
        coVerifyOrder {
            settings.saveConnection(TodoConnection("tok", "ds-2"))
            repository.clear()
            settings.clearLastSync()
        }
    }

    /**
     * 토큰만 바뀌면 연결만 저장하고 저장본은 그대로 두는지 검증
     *
     * Given: 저장된 연결 ("tok", "ds-1")
     * When: replaceConnection(("tok-2", "ds-1"))
     * Then: 성공, saveConnection 1회, clear·clearLastSync·reschedule 호출 없음
     */
    @Test
    fun test_13_replaceConnectionWithSameDataSourceKeepsStore() = runTest {
        /** Given **/
        val target = synchronizer(FakeNotionService())

        /** When **/
        val result = target.replaceConnection(TodoConnection("tok-2", "ds-1"))

        /** Then **/
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { settings.saveConnection(TodoConnection("tok-2", "ds-1")) }
        coVerify(exactly = 0) {
            repository.clear()
            settings.clearLastSync()
            alarms.reschedule()
        }
    }

    /**
     * 연결 저장이 실패하면 저장본을 비우지 않고 실패를 돌려주는지 검증
     *
     * Given: saveConnection이 실패 결과를 돌려줌
     * When: replaceConnection(("tok", "ds-2"))
     * Then: 실패 결과, clear·clearLastSync·reschedule 호출 없음
     */
    @Test
    fun test_14_replaceConnectionSaveFailureKeepsStore() = runTest {
        /** Given **/
        coEvery { settings.saveConnection(any()) } returns Result.failure(IllegalStateException())
        val target = synchronizer(FakeNotionService())

        /** When **/
        val result = target.replaceConnection(TodoConnection("tok", "ds-2"))

        /** Then **/
        assertTrue(result.isFailure)
        coVerify(exactly = 0) {
            repository.clear()
            settings.clearLastSync()
            alarms.reschedule()
        }
    }

    /**
     * 동기화가 잠금을 쥔 동안 연결 교체가 기다렸다가 동기화 뒤에 저장본을 비우는지 검증
     *
     * Given: 첫 응답을 붙잡아 둔 service로 sync 진행 중
     * When: data source ID를 바꾸는 replaceConnection을 띄움
     * Then: 붙잡힌 동안 saveConnection 호출 없음, 풀린 뒤 replaceSynced → saveConnection → clear → clearLastSync 순서
     */
    @Test
    fun test_15_replaceConnectionWaitsForRunningSync() = runTest {
        /** Given **/
        val gate = CompletableDeferred<NetworkResult<NotionQueryResponse>>()
        val service = FakeNotionService(listOf({ gate.await() }, { ok(emptyList()) }))
        val target = synchronizer(service)
        try {
            val syncing = launch { target.sync() }
            advanceUntilIdle()

            /** When **/
            val replacing = launch { target.replaceConnection(TodoConnection("tok", "ds-2")) }
            advanceUntilIdle()

            /** Then **/
            coVerify(exactly = 0) { settings.saveConnection(any()) }
            gate.complete(ok(listOf(pageJson("a"))))
            advanceUntilIdle()
            syncing.join()
            replacing.join()
            coVerifyOrder {
                repository.replaceSynced(listOf(task("a")))
                settings.saveConnection(TodoConnection("tok", "ds-2"))
                repository.clear()
                settings.clearLastSync()
            }
        } finally {
            // 단언이 실패해도 붙잡힌 sync가 프로세스 전역 잠금을 쥔 채 남지 않게 풂
            gate.complete(ok(emptyList()))
        }
    }

    /**
     * 같은 커서가 다시 오면 쪽 넘김을 멈추고 요청 오류로 끝내는지 검증
     *
     * Given: 매번 같은 next_cursor "same"을 돌려주는 service
     * When: sync 호출
     * Then: REQUEST_ERROR, 요청 2회로 멈춤, replaceSynced 호출 없음, REQUEST_ERROR 저장
     */
    @Test
    fun test_16_repeatedCursorIsRequestError() = runTest {
        /** Given **/
        val service = FakeNotionService(List(10) { suspend { ok(listOf(pageJson("a")), nextCursor = "same") } })

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.REQUEST_ERROR, status)
        assertEquals(2, service.queryRequests.size)
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.REQUEST_ERROR)) }
    }

    /**
     * 쪽 수 상한을 넘겨도 다음 쪽이 남으면 요청 오류로 끝내는지 검증
     *
     * Given: 매번 새 next_cursor를 돌려주는 service
     * When: sync 호출
     * Then: REQUEST_ERROR, 요청 MAX_PAGES회로 멈춤, replaceSynced 호출 없음
     */
    @Test
    fun test_17_pageLimitIsRequestError() = runTest {
        /** Given **/
        val service = FakeNotionService(
            List(SyncTodoUseCase.MAX_PAGES + 10) { index -> suspend { ok(listOf(pageJson("p$index")), nextCursor = "c$index") } }
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.REQUEST_ERROR, status)
        assertEquals(SyncTodoUseCase.MAX_PAGES, service.queryRequests.size)
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
    }

    /**
     * 노션 5xx 응답이 서버 오류로 남는지 검증
     *
     * Given: 503 응답
     * When: sync 호출
     * Then: SERVER_ERROR, replaceSynced 호출 없음, SERVER_ERROR 저장
     */
    @Test
    fun test_18_serverErrorIsRecorded() = runTest {
        /** Given **/
        val service = FakeNotionService(httpFailure(503))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SERVER_ERROR, status)
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.SERVER_ERROR)) }
    }

    /**
     * 종류가 메모인 항목은 저장본에 넣지 않고, 종류가 할 일이거나 비어 있으면 넣는지 검증
     *
     * Given: 미완료 쪽에 종류 할 일·메모·없음 항목, 완료 쪽에 종류 메모 항목
     * When: sync 호출
     * Then: SUCCESS, replaceSynced에 할 일과 종류 없는 항목만
     */
    @Test
    fun test_19_memoKindNotStored() = runTest {
        /** Given **/
        val service = FakeNotionService(
            ok(listOf(pageJson("todo", kind = "할 일"), pageJson("memo", kind = "메모"), pageJson("blank"))),
            ok(listOf(pageJson("doneMemo", done = true, kind = "메모"))),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        coVerify { repository.replaceSynced(listOf(task("todo"), task("blank"))) }
    }

    /**
     * 반영 대기가 없으면 보내지 않고 받은 목록으로 저장본을 바꾸는지 검증
     *
     * Given: 반영 대기 없음, 미완료 쪽에 a
     * When: sync 호출
     * Then: SUCCESS, createPage·updatePage 0회, resolvePending 호출 없음, replaceSynced에 a, SUCCESS 저장
     */
    @Test
    fun test_20_noPendingSkipsPush() = runTest {
        /** Given **/
        val service = pushService(listOf(ok(listOf(pageJson("a"))), ok(emptyList())))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        assertEquals(0, service.createRequests.size)
        assertEquals(0, service.updateRequests.size)
        coVerify(exactly = 0) { repository.resolvePending(any(), any()) }
        coVerify {
            repository.replaceSynced(listOf(task("a")))
            settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.SUCCESS))
        }
    }

    /**
     * 임시 ID 항목을 노션에 추가하고 받은 page로 정리하는지 검증
     *
     * Given: 시각 기한이 있는 임시 항목 local-1, 미완료 쪽에 a, 추가 응답 200(page p-new)
     * When: sync 호출
     * Then: SUCCESS, 추가 요청의 parent.data_source_id "ds-1"과 종류 Todo, 기한 time_zone "Asia/Seoul", resolvePending(임시, p-new), replaceSynced에 a와 p-new
     */
    @Test
    fun test_21_createsLocalTask() = runTest {
        /** Given **/
        val local = Todo(
            pageId = "local-1",
            title = "새 Todo",
            done = false,
            due = TodoDue.DateTime(Instant.ofEpochMilli(1795134600000)),
            reminder = TodoReminder.TIME,
            content = "",
            lastEditedTime = Instant.ofEpochMilli(nowMillis),
            url = "",
            pendingSync = true,
        )
        coEvery { repository.getPending() } returns listOf(local)
        val service = pushService(listOf(ok(listOf(pageJson("a"))), ok(emptyList())), creates = listOf(pageResponse("p-new")))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        val request = service.createRequests.single()
        assertEquals("ds-1", request.parent.dataSourceId)
        assertEquals(NotionTodoMapper.KIND_TODO, request.properties.getValue(NotionTodoMapper.PROPERTY_KIND).select?.name)
        assertEquals("Asia/Seoul", request.properties.getValue(NotionTodoMapper.PROPERTY_DUE).date?.timeZone)
        coVerify {
            repository.resolvePending(local, task("p-new"))
            repository.replaceSynced(listOf(task("a"), task("p-new")))
        }
    }

    /**
     * 노션 쪽이 더 나중에 수정됐으면 보내지 않고 노션 값을 따르는지 검증
     *
     * Given: 반영 대기 a(완료, 03:00), 받은 a(미완료, 03:12)
     * When: sync 호출
     * Then: SUCCESS, updatePage 0회, resolvePending(대기 a, 받은 a), replaceSynced의 a는 미완료
     */
    @Test
    fun test_22_notionNewerWinsWithoutPatch() = runTest {
        /** Given **/
        val pendingTask = task("a", done = true, lastEditedTime = 1794970800000).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(pendingTask)
        val service = pushService(listOf(ok(listOf(pageJson("a"))), ok(emptyList())))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        assertEquals(0, service.updateRequests.size)
        coVerify {
            repository.resolvePending(pendingTask, task("a"))
            repository.replaceSynced(listOf(task("a")))
        }
    }

    /**
     * 앱 쪽이 더 나중에 바뀌었으면 완료만 보내고 응답으로 정리하는지 검증
     *
     * Given: 반영 대기 a(완료, 03:20), 받은 a(미완료, 03:12), 완료 체크 응답 a(완료, 03:21)
     * When: sync 호출
     * Then: SUCCESS, updatePage("Bearer tok", "a", 완료 true 본문), resolvePending(대기 a, 응답 a), replaceSynced의 a는 완료
     */
    @Test
    fun test_23_localNewerSendsPatch() = runTest {
        /** Given **/
        val pendingTask = task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        val resolved = task("a", done = true, lastEditedTime = 1794972060000)
        coEvery { repository.getPending() } returns listOf(pendingTask)
        val service = pushService(
            listOf(ok(listOf(pageJson("a"))), ok(emptyList())),
            updates = listOf(pageResponse("a", done = true, lastEditedTime = "2026-11-18T03:21:00.000Z")),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        val request = service.updateRequests.single()
        assertEquals("Bearer tok", request.authorization)
        assertEquals("a", request.pageId)
        assertEquals(
            parse("""{"properties":{"완료":{"checkbox":true}}}"""),
            JsonUtil.kotlinSerialization.encodeToJsonElement(NotionUpdatePageRequest.serializer(), request.request),
        )
        coVerify {
            repository.resolvePending(pendingTask, resolved)
            repository.replaceSynced(listOf(resolved))
        }
    }

    /**
     * 완료 체크한 페이지가 404면 그 항목을 저장본에서 빼는지 검증
     *
     * Given: 반영 대기 a(03:20), 받은 a·b(03:12), 완료 체크 응답 404
     * When: sync 호출
     * Then: SUCCESS, resolvePending(대기 a, null), replaceSynced에 b만, SUCCESS 저장
     */
    @Test
    fun test_24_patchNotFoundDropsChange() = runTest {
        /** Given **/
        val pendingTask = task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(pendingTask)
        val service = pushService(
            listOf(ok(listOf(pageJson("a"), pageJson("b"))), ok(emptyList())),
            updates = listOf(httpFailure(404)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        coVerify {
            repository.resolvePending(pendingTask, null)
            repository.replaceSynced(listOf(task("b")))
            settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.SUCCESS))
        }
    }

    /**
     * 보내기 연결 예외면 남은 항목을 보내지 않고 반영 대기로 남기는지 검증
     *
     * Given: 반영 대기 a·b(03:20), 받은 c, 첫 완료 체크가 연결 예외
     * When: sync 호출
     * Then: NETWORK_ERROR, 완료 체크 1회, resolvePending 호출 없음, replaceSynced에 c, NETWORK_ERROR 저장
     */
    @Test
    fun test_25_pushNetworkErrorKeepsPendingAndRecords() = runTest {
        /** Given **/
        coEvery { repository.getPending() } returns listOf(
            task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
            task("b", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
        )
        val service = pushService(
            listOf(ok(listOf(pageJson("c"))), ok(emptyList())),
            updates = listOf(connectionFailure(), pageResponse("b", done = true)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.NETWORK_ERROR, status)
        assertEquals(1, service.updateRequests.size)
        coVerify(exactly = 0) { repository.resolvePending(any(), any()) }
        coVerify {
            repository.replaceSynced(listOf(task("c")))
            settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.NETWORK_ERROR))
        }
    }

    /**
     * 받은 목록에 있는 페이지의 보내기 400이면 그 항목만 반영 대기로 남기고 다음 항목을 보내는지 검증
     *
     * Given: 반영 대기 a·b(03:20), 받은 a·b(03:12), 완료 체크 응답 400 뒤 200(b 완료)
     * When: sync 호출
     * Then: REQUEST_ERROR, 완료 체크 2회, resolvePending은 b만 1회, replaceSynced에 받은 a와 응답 b, REQUEST_ERROR 저장
     */
    @Test
    fun test_26_pushBadRequestContinues() = runTest {
        /** Given **/
        val pendingB = task("b", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(
            task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
            pendingB,
        )
        val service = pushService(
            listOf(ok(listOf(pageJson("a"), pageJson("b"))), ok(emptyList())),
            updates = listOf(httpFailure(400), pageResponse("b", done = true)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.REQUEST_ERROR, status)
        assertEquals(2, service.updateRequests.size)
        coVerify(exactly = 1) { repository.resolvePending(any(), any()) }
        coVerify {
            repository.resolvePending(pendingB, task("b", done = true))
            repository.replaceSynced(listOf(task("a"), task("b", done = true)))
            settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.REQUEST_ERROR))
        }
    }

    /**
     * 보내기 429면 안내된 시간만큼 기다린 뒤 다시 보내는지 검증
     *
     * Given: 반영 대기 a(03:20), 완료 체크 응답 429(Retry-After 1) 뒤 200(a 완료)
     * When: sync 호출
     * Then: SUCCESS, 가상 시간 1000ms 진행, 완료 체크 2회, resolvePending(대기 a, 응답 a)
     */
    @Test
    fun test_27_pushRateLimitRetries() = runTest {
        /** Given **/
        val pendingTask = task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(pendingTask)
        val service = pushService(
            listOf(ok(emptyList()), ok(emptyList())),
            updates = listOf(httpFailure(429, retryAfter = "1"), pageResponse("a", done = true)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        assertEquals(1000L, currentTime)
        assertEquals(2, service.updateRequests.size)
        coVerify { repository.resolvePending(pendingTask, task("a", done = true)) }
    }

    /**
     * 페이지 추가 404가 인증 오류로 남고 반영 대기를 유지하는지 검증
     *
     * Given: 임시 항목 local-1, 추가 응답 404
     * When: sync 호출
     * Then: AUTH_ERROR, 추가 1회, resolvePending 호출 없음, AUTH_ERROR 저장
     */
    @Test
    fun test_28_createNotFoundIsAuthError() = runTest {
        /** Given **/
        coEvery { repository.getPending() } returns listOf(task("local-1").copy(url = "", pendingSync = true))
        val service = pushService(
            listOf(ok(emptyList()), ok(emptyList())),
            creates = listOf(httpFailure(404)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.AUTH_ERROR, status)
        assertEquals(1, service.createRequests.size)
        coVerify(exactly = 0) { repository.resolvePending(any(), any()) }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.AUTH_ERROR)) }
    }

    /**
     * 반영 대기 없이 저장본 교체 예외가 나면 저장 오류로 남고 시각 알림을 다시 예약하지 않는지 검증
     *
     * Given: 반영 대기 없음, replaceSynced가 예외를 던짐
     * When: sync 호출
     * Then: STORAGE_ERROR 반환, reschedule 호출 없음, STORAGE_ERROR 저장
     */
    @Test
    fun test_29_storageFailureRecorded() = runTest {
        /** Given **/
        coEvery { repository.replaceSynced(any()) } throws IllegalStateException()
        val service = pushService(listOf(ok(listOf(pageJson("a"))), ok(emptyList())))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.STORAGE_ERROR, status)
        coVerify(exactly = 0) { alarms.reschedule() }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.STORAGE_ERROR)) }
    }

    /**
     * 하이픈과 대소문자만 다른 data source ID는 같은 데이터베이스로 보는지 검증
     *
     * Given: 저장된 ID "41cffbf344f88196a0dd000b5dbcdc02"
     * When: 하이픈을 넣은 ID와 대문자 ID로 각각 replaceConnection
     * Then: 모두 성공, saveConnection 2회, clear·clearLastSync·reschedule 호출 없음
     */
    @Test
    fun test_30_replaceConnectionIgnoresHyphens() = runTest {
        /** Given **/
        coEvery { settings.getConnection() } returns TodoConnection("tok", "41cffbf344f88196a0dd000b5dbcdc02")
        val target = synchronizer(FakeNotionService())

        listOf("41cffbf3-44f8-8196-a0dd-000b5dbcdc02", "41CFFBF344F88196A0DD000B5DBCDC02").forEach { dataSourceId ->
            /** When **/
            val result = target.replaceConnection(TodoConnection("tok", dataSourceId))

            /** Then **/
            assertTrue("id=$dataSourceId", result.isSuccess)
        }
        coVerify(exactly = 2) { settings.saveConnection(any()) }
        coVerify(exactly = 0) {
            repository.clear()
            settings.clearLastSync()
            alarms.reschedule()
        }
    }

    /**
     * 연결은 저장했지만 저장본을 비우지 못하면 비우기 실패를 돌려주는지 검증
     *
     * Given: 저장된 연결 ("tok", "ds-1"), clear가 예외를 던짐
     * When: replaceConnection(("tok", "ds-2"))
     * Then: TodoStoreClearException 실패, saveConnection·clearLastSync 호출, reschedule 1회
     */
    @Test
    fun test_31_replaceConnectionClearFailure() = runTest {
        /** Given **/
        coEvery { repository.clear() } throws IllegalStateException()
        val target = synchronizer(FakeNotionService())

        /** When **/
        val result = target.replaceConnection(TodoConnection("tok", "ds-2"))

        /** Then **/
        assertTrue(result.exceptionOrNull() is TodoStoreClearException)
        coVerify {
            settings.saveConnection(TodoConnection("tok", "ds-2"))
            settings.clearLastSync()
        }
        coVerify(exactly = 1) { alarms.reschedule() }
    }

    /**
     * 추가 요청이 진행 중일 때 동기화가 취소돼도 추가 응답을 저장본에 반영하고 다음 항목은 보내지 않는지 검증
     *
     * Given: 임시 항목 local-1·local-2, 첫 추가 응답을 붙잡아 둔 service로 sync 진행 중
     * When: sync 코루틴을 취소한 뒤 첫 추가 응답 200(page p-new)을 풂
     * Then: sync 취소됨, resolvePending은 (local-1, p-new) 1회뿐, 추가 1회
     */
    @Test
    fun test_32_cancelDuringCreateStillResolves() = runTest {
        /** Given **/
        val first = task("local-1").copy(url = "", pendingSync = true)
        val second = task("local-2").copy(url = "", pendingSync = true)
        coEvery { repository.getPending() } returns listOf(first, second)
        val gate = CompletableDeferred<NetworkResult<NotionPage>>()
        val service = FakeNotionService(
            listOf(suspend { ok(emptyList()) }, suspend { ok(emptyList()) }),
            createResults = listOf({ gate.await() }, { pageResponse("p-second") }),
        )
        try {
            val syncing = launch { synchronizer(service).sync() }
            advanceUntilIdle()
            assertEquals(1, service.createRequests.size)

            /** When **/
            syncing.cancel()
            gate.complete(pageResponse("p-new"))
            advanceUntilIdle()

            /** Then **/
            assertTrue(syncing.isCancelled)
            coVerify(exactly = 1) { repository.resolvePending(any(), any()) }
            coVerify { repository.resolvePending(first, task("p-new")) }
            assertEquals(1, service.createRequests.size)
        } finally {
            // 단언이 실패해도 붙잡힌 sync가 프로세스 전역 잠금을 쥔 채 남지 않게 풂
            gate.complete(pageResponse("p-new"))
        }
    }

    /**
     * 받은 목록에 없는 페이지의 완료 체크가 400이면 반영 대기를 풀고 저장본에서 빼는지 검증
     *
     * Given: 반영 대기 a(03:20), 받은 목록에 b만, 완료 체크 응답 400
     * When: sync 호출
     * Then: SUCCESS, resolvePending(대기 a, null), replaceSynced에 b만, SUCCESS 저장
     */
    @Test
    fun test_33_patchBadRequestForUnfetchedPageDropsChange() = runTest {
        /** Given **/
        val pendingTask = task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(pendingTask)
        val service = pushService(
            listOf(ok(listOf(pageJson("b"))), ok(emptyList())),
            updates = listOf(httpFailure(400)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        coVerify {
            repository.resolvePending(pendingTask, null)
            repository.replaceSynced(listOf(task("b")))
            settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.SUCCESS))
        }
    }

    /**
     * 완료 체크 응답의 종류가 메모면 반영 대기를 풀고 저장본에 넣지 않는지 검증
     *
     * Given: 반영 대기 a(03:20), 받은 a·b(03:12), 완료 체크 응답 a(완료, 종류 메모)
     * When: sync 호출
     * Then: SUCCESS, resolvePending(대기 a, null), replaceSynced에 b만
     */
    @Test
    fun test_34_memoPushResponseNotStored() = runTest {
        /** Given **/
        val pendingTask = task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(pendingTask)
        val service = pushService(
            listOf(ok(listOf(pageJson("a"), pageJson("b"))), ok(emptyList())),
            updates = listOf(pageResult(pageJson("a", done = true, kind = "메모"))),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        coVerify {
            repository.resolvePending(pendingTask, null)
            repository.replaceSynced(listOf(task("b")))
        }
    }

    /**
     * 반영 대기 읽기 예외가 저장 오류로 남고 보내지 않는지 검증
     *
     * Given: getPending이 예외를 던짐
     * When: sync 호출
     * Then: STORAGE_ERROR, 추가·완료 체크 0회, replaceSynced·reschedule 호출 없음, STORAGE_ERROR 저장
     */
    @Test
    fun test_35_getPendingFailureIsStorageError() = runTest {
        /** Given **/
        coEvery { repository.getPending() } throws IllegalStateException()
        val service = pushService(listOf(ok(listOf(pageJson("a"))), ok(emptyList())))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.STORAGE_ERROR, status)
        assertEquals(0, service.createRequests.size)
        assertEquals(0, service.updateRequests.size)
        coVerify(exactly = 0) {
            repository.replaceSynced(any())
            alarms.reschedule()
        }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.STORAGE_ERROR)) }
    }

    /**
     * 첫 항목의 보내기 결과 반영 예외가 저장 오류로 남고 남은 항목, 저장본 교체, 시각 알림 다시 예약을 하지 않는지 검증
     *
     * Given: 반영 대기 a·b(03:20), resolvePending이 예외를 던짐, 완료 체크 응답 200(a 완료)
     * When: sync 호출
     * Then: STORAGE_ERROR, 완료 체크 1회, replaceSynced·reschedule 호출 없음, STORAGE_ERROR 저장
     */
    @Test
    fun test_36_resolvePendingFailureIsStorageError() = runTest {
        /** Given **/
        coEvery { repository.getPending() } returns listOf(
            task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
            task("b", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
        )
        coEvery { repository.resolvePending(any(), any()) } throws IllegalStateException()
        val service = pushService(
            listOf(ok(emptyList()), ok(emptyList())),
            updates = listOf(pageResponse("a", done = true), pageResponse("b", done = true)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.STORAGE_ERROR, status)
        assertEquals(1, service.updateRequests.size)
        coVerify(exactly = 0) {
            repository.replaceSynced(any())
            alarms.reschedule()
        }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.STORAGE_ERROR)) }
    }

    /**
     * 완료 체크 401·5xx와 추가 404면 남은 항목을 보내지 않는지 검증
     *
     * Given: 완료 체크 반영 대기 a·b(03:20)에 첫 응답 401 또는 503, 임시 항목 local-1·local-2에 첫 추가 응답 404
     * When: 경우마다 sync 호출
     * Then: 각각 AUTH_ERROR·SERVER_ERROR·AUTH_ERROR, 보낸 요청 1회, resolvePending 호출 없음
     */
    @Test
    fun test_37_stoppingFailuresSkipRemaining() = runTest {
        /** Given **/
        val pendingUpdates = listOf(
            task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
            task("b", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
        )
        val pendingCreates = listOf(
            task("local-1").copy(url = "", pendingSync = true),
            task("local-2").copy(url = "", pendingSync = true),
        )
        val queries = listOf(ok(emptyList()), ok(emptyList()))
        val error = """{"object":"error"}"""
        listOf(401 to TodoSyncStatus.AUTH_ERROR, 503 to TodoSyncStatus.SERVER_ERROR).forEach { (statusCode, expected) ->
            coEvery { repository.getPending() } returns pendingUpdates
            val service = pushService(queries, updates = listOf(httpFailure(statusCode), pageResponse("b", done = true)))

            /** When **/
            val status = synchronizer(service).sync()

            /** Then **/
            assertEquals("code=$statusCode", expected, status)
            assertEquals("code=$statusCode", 1, service.updateRequests.size)
        }
        coEvery { repository.getPending() } returns pendingCreates
        val createService = pushService(queries, creates = listOf(httpFailure(404), pageResponse("p-second")))
        assertEquals(TodoSyncStatus.AUTH_ERROR, synchronizer(createService).sync())
        assertEquals(1, createService.createRequests.size)
        coVerify(exactly = 0) { repository.resolvePending(any(), any()) }
    }

    /**
     * 노션과 앱의 수정 시각이 같으면 앱 쪽 완료 체크를 보내는지 검증
     *
     * Given: 반영 대기 a(완료, 03:12), 받은 a(미완료, 03:12), 완료 체크 응답 a(완료, 03:13)
     * When: sync 호출
     * Then: SUCCESS, updatePage("a") 1회, resolvePending(대기 a, 응답 a)
     */
    @Test
    fun test_38_sameEditTimeSendsLocalChange() = runTest {
        /** Given **/
        val pendingTask = task("a", done = true, lastEditedTime = nowMillis).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(pendingTask)
        val service = pushService(
            listOf(ok(listOf(pageJson("a"))), ok(emptyList())),
            updates = listOf(pageResponse("a", done = true, lastEditedTime = "2026-11-18T03:13:00.000Z")),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        assertEquals("a", service.updateRequests.single().pageId)
        coVerify { repository.resolvePending(pendingTask, task("a", done = true, lastEditedTime = 1794971580000)) }
    }

    /**
     * 동기화가 저장본을 바꾼 뒤 시각 알림을 한 번 다시 예약하는지 검증
     *
     * Given: 미완료 a, 완료 없음
     * When: sync 호출
     * Then: SUCCESS, replaceSynced 뒤 reschedule 1회
     */
    @Test
    fun test_39_successReschedulesAfterReplace() = runTest {
        /** Given **/
        val service = FakeNotionService(ok(listOf(pageJson("a"))), ok(emptyList()))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.SUCCESS, status)
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.replaceSynced(listOf(task("a")))
            alarms.reschedule()
        }
    }

    /**
     * 보내기가 실패해도 저장본을 바꿨으면 시각 알림을 한 번 다시 예약하는지 검증
     *
     * Given: 반영 대기 a, 받은 c, 완료 체크가 연결 예외
     * When: sync 호출
     * Then: NETWORK_ERROR, replaceSynced 뒤 reschedule 1회
     */
    @Test
    fun test_40_pushFailureStillReschedules() = runTest {
        /** Given **/
        coEvery { repository.getPending() } returns listOf(task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true))
        val service = pushService(listOf(ok(listOf(pageJson("c"))), ok(emptyList())), updates = listOf(connectionFailure()))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.NETWORK_ERROR, status)
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.replaceSynced(listOf(task("c")))
            alarms.reschedule()
        }
    }

    /**
     * 조회가 실패해 저장본을 바꾸지 않으면 다시 예약하지 않는지 검증
     *
     * Given: 첫 조회가 연결 예외
     * When: sync 호출
     * Then: NETWORK_ERROR, replaceSynced와 reschedule 호출 없음
     */
    @Test
    fun test_41_fetchFailureSkipsReschedule() = runTest {
        /** Given **/
        val service = FakeNotionService(connectionFailure())

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.NETWORK_ERROR, status)
        coVerify(exactly = 0) {
            repository.replaceSynced(any())
            alarms.reschedule()
        }
    }

    /**
     * data source ID를 바꿔 저장본을 비우면 시각 알림을 한 번 다시 예약하는지 검증
     *
     * Given: 저장된 연결 ("tok", "ds-1")
     * When: replaceConnection(("tok", "ds-2"))
     * Then: 성공, clear 뒤 reschedule 1회
     */
    @Test
    fun test_42_replaceConnectionWithNewDataSourceReschedules() = runTest {
        /** Given **/
        val target = synchronizer(FakeNotionService())

        /** When **/
        val result = target.replaceConnection(TodoConnection("tok", "ds-2"))

        /** Then **/
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.clear()
            alarms.reschedule()
        }
    }

    /**
     * 보내기 결과를 반영한 뒤 저장본 교체가 실패해도 시각 알림을 한 번 다시 예약하는지 검증
     *
     * Given: 반영 대기 a(03:20), 완료 체크 응답 200(a 완료), replaceSynced가 예외를 던짐
     * When: sync 호출
     * Then: STORAGE_ERROR, resolvePending 뒤 reschedule 1회, STORAGE_ERROR 저장
     */
    @Test
    fun test_43_replaceSyncedFailureAfterPushReschedules() = runTest {
        /** Given **/
        coEvery { repository.getPending() } returns listOf(task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true))
        coEvery { repository.replaceSynced(any()) } throws IllegalStateException()
        val service = pushService(listOf(ok(emptyList()), ok(emptyList())), updates = listOf(pageResponse("a", done = true)))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.STORAGE_ERROR, status)
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.resolvePending(any(), any())
            repository.replaceSynced(any())
            alarms.reschedule()
        }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.STORAGE_ERROR)) }
    }

    /**
     * 보낸 결과 하나를 저장본에 반영한 뒤 다음 항목의 반영이 실패하면 시각 알림을 한 번 다시 예약하는지 검증
     *
     * Given: 반영 대기 a·b(03:20), 완료 체크 응답 200(a 완료)·200(b 완료), b의 resolvePending만 예외를 던짐
     * When: sync 호출
     * Then: STORAGE_ERROR, 완료 체크 2회, replaceSynced 호출 없음, resolvePending(a)·resolvePending(b) 뒤 reschedule 1회, STORAGE_ERROR 저장
     */
    @Test
    fun test_44_resolvePendingFailureAfterResolvedReschedules() = runTest {
        /** Given **/
        val pendingA = task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        val pendingB = task("b", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(pendingA, pendingB)
        coEvery { repository.resolvePending(pendingB, any()) } throws IllegalStateException()
        val service = pushService(
            listOf(ok(emptyList()), ok(emptyList())),
            updates = listOf(pageResponse("a", done = true), pageResponse("b", done = true)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.STORAGE_ERROR, status)
        assertEquals(2, service.updateRequests.size)
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.resolvePending(pendingA, task("a", done = true))
            repository.resolvePending(pendingB, task("b", done = true))
            alarms.reschedule()
        }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.STORAGE_ERROR)) }
    }

    /**
     * 추가 결과를 저장본에 반영한 뒤 다음 항목 전에 동기화가 취소되면 시각 알림을 끝까지 한 번 다시 예약하고 취소를 전파하는지 검증
     *
     * Given: 임시 항목 local-1·local-2, 추가 응답 200(page p-new), local-1의 resolvePending 중 sync 코루틴 취소, reschedule은 한 번 양보한 뒤 끝남
     * When: sync 코루틴 실행
     * Then: sync가 값을 돌려주지 않고 취소됨, 추가 1회, replaceSynced·saveLastSync 호출 없음, resolvePending(local-1, p-new) 뒤 reschedule 1회가 끝까지 실행
     */
    @Test
    fun test_45_cancelAfterResolvedReschedules() = runTest {
        /** Given **/
        val first = task("local-1").copy(url = "", pendingSync = true)
        val second = task("local-2").copy(url = "", pendingSync = true)
        coEvery { repository.getPending() } returns listOf(first, second)
        lateinit var syncing: Job
        coEvery { repository.resolvePending(first, any()) } answers { syncing.cancel() }
        var rescheduleFinished = false
        coEvery { alarms.reschedule() } coAnswers {
            // 취소된 코루틴에서 불리면 여기서 취소 예외가 남
            yield()
            rescheduleFinished = true
        }
        val service = pushService(listOf(ok(emptyList()), ok(emptyList())), creates = listOf(pageResponse("p-new"), pageResponse("p-second")))
        var returnedStatus: TodoSyncStatus? = null

        /** When **/
        syncing = launch { returnedStatus = synchronizer(service).sync() }
        advanceUntilIdle()

        /** Then **/
        assertTrue(syncing.isCancelled)
        assertNull(returnedStatus)
        assertEquals(1, service.createRequests.size)
        coVerify(exactly = 0) {
            repository.replaceSynced(any())
            settings.saveLastSync(any())
        }
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.resolvePending(first, task("p-new"))
            alarms.reschedule()
        }
        assertTrue(rescheduleFinished)
    }

    /**
     * 노션 조회가 연결 예외로 끝나면 조회 단계 실패로 알려 주는지 검증
     *
     * Given: 미완료 조회가 연결 예외
     * When: syncWithStage 호출
     * Then: NETWORK_ERROR, failedOnFetch true, 추가·완료 체크 요청 없음
     */
    @Test
    fun test_46_fetchNetworkErrorReportsFetchStage() = runTest {
        /** Given **/
        coEvery { repository.getPending() } returns listOf(task("local-1").copy(url = "", pendingSync = true))
        val service = pushService(listOf(connectionFailure()))

        /** When **/
        val result = synchronizer(service).syncWithStage()

        /** Then **/
        assertEquals(TodoSyncResult(TodoSyncStatus.NETWORK_ERROR, failedOnFetch = true), result)
        assertEquals(0, service.createRequests.size)
        assertEquals(0, service.updateRequests.size)
    }

    /**
     * 조회에 성공한 뒤 추가 요청이 연결 예외로 끝나면 조회 단계 실패가 아니라고 알려 주는지 검증
     *
     * Given: 임시 항목 local-1, 조회 성공(빈 목록 두 번), 추가 요청이 연결 예외
     * When: syncWithStage 호출
     * Then: NETWORK_ERROR, failedOnFetch false, 추가 1회, replaceSynced 호출
     */
    @Test
    fun test_47_pushNetworkErrorReportsNotFetchStage() = runTest {
        /** Given **/
        coEvery { repository.getPending() } returns listOf(task("local-1").copy(url = "", pendingSync = true))
        val service = pushService(listOf(ok(emptyList()), ok(emptyList())), creates = listOf(connectionFailure()))

        /** When **/
        val result = synchronizer(service).syncWithStage()

        /** Then **/
        assertEquals(TodoSyncResult(TodoSyncStatus.NETWORK_ERROR, failedOnFetch = false), result)
        assertEquals(1, service.createRequests.size)
        coVerify { repository.replaceSynced(emptyList()) }
    }

    /**
     * data source ID를 바꾸며 저장본을 비우는 중 취소되면 마지막 동기화 비우기와 시각 알림 다시 예약을 끝까지 실행하고 취소를 전파하는지 검증
     *
     * Given: 저장된 연결 ("tok", "ds-1"), clear 중 replaceConnection 코루틴 취소, clearLastSync와 reschedule은 한 번 양보한 뒤 끝남
     * When: replaceConnection(("tok", "ds-2")) 코루틴 실행
     * Then: replaceConnection이 값을 돌려주지 않고 취소됨, clear 뒤 clearLastSync와 reschedule 1회가 끝까지 실행
     */
    @Test
    fun test_48_cancelAfterClearReschedules() = runTest {
        /** Given **/
        lateinit var replacing: Job
        coEvery { repository.clear() } answers { replacing.cancel() }
        var clearLastSyncFinished = false
        coEvery { settings.clearLastSync() } coAnswers {
            // DataStore 저장처럼 취소된 코루틴에서 불리면 여기서 취소 예외가 남
            yield()
            clearLastSyncFinished = true
            Result.success(Unit)
        }
        var rescheduleFinished = false
        coEvery { alarms.reschedule() } coAnswers {
            // 취소된 코루틴에서 불리면 여기서 취소 예외가 남
            yield()
            rescheduleFinished = true
        }
        val target = synchronizer(FakeNotionService())
        var returnedResult: Result<Unit>? = null

        /** When **/
        replacing = launch { returnedResult = target.replaceConnection(TodoConnection("tok", "ds-2")) }
        advanceUntilIdle()

        /** Then **/
        assertTrue(replacing.isCancelled)
        assertNull(returnedResult)
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.clear()
            settings.clearLastSync()
            alarms.reschedule()
        }
        assertTrue(clearLastSyncFinished)
        assertTrue(rescheduleFinished)
    }

    /**
     * 연결 저장 중 취소돼도 저장본 비우기와 시각 알림 다시 예약을 끝까지 실행하고 취소를 전파하는지 검증
     *
     * Given: 저장된 연결 ("tok", "ds-1"), saveConnection 중 replaceConnection 코루틴 취소, saveConnection은 한 번 양보한 뒤 성공
     * When: replaceConnection(("tok", "ds-2")) 코루틴 실행
     * Then: replaceConnection이 값을 돌려주지 않고 취소됨, saveConnection → clear → clearLastSync → reschedule 순서로 1회씩 실행
     */
    @Test
    fun test_49_cancelDuringSaveConnectionClearsAndReschedules() = runTest {
        /** Given **/
        lateinit var replacing: Job
        coEvery { settings.saveConnection(any()) } coAnswers {
            replacing.cancel()
            // DataStore 저장처럼 쓰기를 마친 뒤 취소된 코루틴으로 돌아오면 여기서 취소 예외가 남
            yield()
            Result.success(Unit)
        }
        val target = synchronizer(FakeNotionService())
        var returnedResult: Result<Unit>? = null

        /** When **/
        replacing = launch { returnedResult = target.replaceConnection(TodoConnection("tok", "ds-2")) }
        advanceUntilIdle()

        /** Then **/
        assertTrue(replacing.isCancelled)
        assertNull(returnedResult)
        coVerify(exactly = 1) {
            repository.clear()
            settings.clearLastSync()
            alarms.reschedule()
        }
        coVerifyOrder {
            settings.saveConnection(TodoConnection("tok", "ds-2"))
            repository.clear()
            settings.clearLastSync()
            alarms.reschedule()
        }
    }

    /**
     * 조회 429의 Retry-After가 최대 대기 시간을 넘으면 기다리지 않고 요청 오류로 끝나는지 검증
     *
     * Given: 429(Retry-After 61) 뒤 미완료·완료 200 응답
     * When: sync 호출
     * Then: REQUEST_ERROR, 가상 시간 진행 없음, 요청 1회, replaceSynced 호출 없음, REQUEST_ERROR 저장
     */
    @Test
    fun test_50_queryRetryAfterOverMaxWaitIsRequestError() = runTest {
        /** Given **/
        val service = FakeNotionService(httpFailure(429, retryAfter = "61"), ok(listOf(pageJson("a"))), ok(emptyList()))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.REQUEST_ERROR, status)
        assertEquals(0L, currentTime)
        assertEquals(1, service.queryRequests.size)
        coVerify(exactly = 0) { repository.replaceSynced(any()) }
        coVerify { settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.REQUEST_ERROR)) }
    }

    /**
     * 완료 체크 429의 Retry-After가 최대 대기 시간을 넘으면 기다리지 않고 남은 항목도 보내지 않는지 검증
     *
     * Given: 반영 대기 a·b(03:20), 받은 c, 첫 완료 체크 응답 429(Retry-After 120)
     * When: sync 호출
     * Then: REQUEST_ERROR, 가상 시간 진행 없음, 완료 체크 1회, resolvePending 호출 없음, replaceSynced에 c, REQUEST_ERROR 저장
     */
    @Test
    fun test_51_pushRetryAfterOverMaxWaitStopsRemaining() = runTest {
        /** Given **/
        coEvery { repository.getPending() } returns listOf(
            task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
            task("b", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
        )
        val service = pushService(
            listOf(ok(listOf(pageJson("c"))), ok(emptyList())),
            updates = listOf(httpFailure(429, retryAfter = "120"), pageResponse("b", done = true)),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.REQUEST_ERROR, status)
        assertEquals(0L, currentTime)
        assertEquals(1, service.updateRequests.size)
        coVerify(exactly = 0) { repository.resolvePending(any(), any()) }
        coVerify {
            repository.replaceSynced(listOf(task("c")))
            settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.REQUEST_ERROR))
        }
    }

    /**
     * 보내기 응답이 쓸 수 없는 페이지이거나 해석에 실패하면 그 항목만 반영 대기로 남기고 다음 항목을 보내는지 검증
     *
     * Given: 반영 대기 a·b·c(03:20), 받은 a·b·c(03:12), 완료 체크 응답 휴지통 페이지 a, 해석 실패, 200(c 완료)
     * When: sync 호출
     * Then: REQUEST_ERROR, 완료 체크 3회, resolvePending은 c만 1회, replaceSynced에 받은 a·b와 응답 c, REQUEST_ERROR 저장
     */
    @Test
    fun test_52_unusablePushResponseContinues() = runTest {
        /** Given **/
        val pendingC = task("c", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true)
        coEvery { repository.getPending() } returns listOf(
            task("a", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
            task("b", done = true, lastEditedTime = 1794972000000).copy(pendingSync = true),
            pendingC,
        )
        val service = pushService(
            listOf(ok(listOf(pageJson("a"), pageJson("b"), pageJson("c"))), ok(emptyList())),
            updates = listOf(
                pageResult(pageJson("a", done = true, extra = """"in_trash":true,""")),
                parseFailure(),
                pageResponse("c", done = true),
            ),
        )

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.REQUEST_ERROR, status)
        assertEquals(3, service.updateRequests.size)
        coVerify(exactly = 1) { repository.resolvePending(any(), any()) }
        coVerify {
            repository.resolvePending(pendingC, task("c", done = true))
            repository.replaceSynced(listOf(task("a"), task("b"), task("c", done = true)))
            settings.saveLastSync(TodoSyncState(nowMillis, TodoSyncStatus.REQUEST_ERROR))
        }
    }

    /**
     * 연결 실패를 로그에 남길 때 예외 클래스 이름만 남기고 예외 메시지의 주소와 토큰은 남기지 않는지 검증
     *
     * Given: 토큰 secret-token인 연결, 주소와 토큰이 메시지에 든 IOException으로 끝나는 조회
     * When: sync 호출
     * Then: NETWORK_ERROR, "조회 실패: connection=IOException" 경고 로그, 주소나 토큰이 든 경고 로그 없음
     */
    @Test
    fun test_53_connectionFailureLogsOnlyExceptionClass() = runTest {
        /** Given **/
        coEvery { settings.getConnection() } returns TodoConnection("secret-token", "ds-1")
        val service = FakeNotionService(NetworkResult.ConnectionFailure(IOException("api.notion.com secret-token")))

        /** When **/
        val status = synchronizer(service).sync()

        /** Then **/
        assertEquals(TodoSyncStatus.NETWORK_ERROR, status)
        verify { Log.w(any<String>(), match<String> { it.contains("조회 실패: connection=IOException") }) }
        verify(exactly = 0) { Log.w(any<String>(), match<String> { it.contains("api.notion.com") || it.contains("secret-token") }) }
    }
}
