package com.black.feature.todo.data.network

import com.black.core.network.Network
import com.black.core.network.NetworkResult
import com.black.core.util.JsonUtil
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.test.BaseTest
import com.black.test.captureDebugLogs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.ZoneId

/** [NotionService]가 보내는 요청의 메서드·경로·헤더·JSON 본문과 응답 해석을 MockWebServer로 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotionServiceTest : BaseTest() {

    private lateinit var server: MockWebServer

    private lateinit var service: NotionService

    private val pageBody =
        """{"object":"page","id":"p-1","last_edited_time":"2026-11-18T03:12:00.000Z","url":"https://www.notion.so/p-1",""" +
            """"properties":{"이름":{"type":"title","title":[{"plain_text":"장보기"}]},"완료":{"checkbox":true}}}"""

    @Before
    fun setupServer() {
        server = MockWebServer()
        server.start()
        service = NotionService.create(server.url("/").toString())
    }

    @After
    fun shutdownServer() {
        Network.setLoggingEnabled(false)
        server.shutdown()
    }

    private fun json(text: String): JsonElement = JsonUtil.kotlinSerialization.parseToJsonElement(text)

    /** 노션 공통 헤더(토큰, API 버전, JSON 본문, 고정 User-Agent) 확인 */
    private fun assertNotionHeaders(request: RecordedRequest) {
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        assertEquals("2025-09-03", request.getHeader("Notion-Version"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("JustApp", request.getHeader("User-Agent"))
    }

    /**
     * 미완료 조회를 data source 경로로 보내고 조회 응답을 해석하는지 검증
     *
     * Given: 페이지 하나와 다음 커서 c2를 담은 200 응답
     * When: queryDataSource("Bearer tok", "ds-1", 커서 c1의 미완료 조회) 호출
     * Then: POST /v1/data_sources/ds-1/query, 노션 헤더, 미완료 조회 JSON 본문, 해석한 list·다음 쪽·페이지
     */
    @Test
    fun test_01_queryDataSourceSendsOpenQuery() = runTest {
        /** Given **/
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"object":"list","results":[$pageBody],"has_more":true,"next_cursor":"c2"}""")
        )

        /** When **/
        val result = service.queryDataSource(NotionService.authorization("tok"), "ds-1", NotionTodoMapper.openTodosQuery("c1"))

        /** Then **/
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/data_sources/ds-1/query", request.path)
        assertNotionHeaders(request)
        val expectedBody = """
            {"filter":{"property":"완료","checkbox":{"equals":false}},
             "sorts":[{"property":"기한","direction":"ascending"}],
             "page_size":100,
             "start_cursor":"c1"}
        """
        assertEquals(json(expectedBody), json(request.body.readUtf8()))
        val response = (result as NetworkResult.Success).data
        assertEquals("list", response.objectType)
        assertTrue(response.hasMore)
        assertEquals("c2", response.nextCursor)
        val page = response.results!!.single()
        assertEquals("p-1", page.id)
        assertEquals("장보기", page.properties.getValue("이름").title!!.single().plainText)
        assertEquals(true, page.properties.getValue("완료").checkbox)
    }

    /**
     * 최근 완료 조회 본문을 보내는지 검증
     *
     * Given: 결과 없는 200 응답
     * When: queryDataSource("Bearer tok", "ds-1", 2026-11-18T03:12Z 이후의 완료 조회) 호출
     * Then: 완료 true와 수정 시각 조건, 수정 시각 내림차순 정렬, page_size 100이고 start_cursor 없는 JSON 본문
     */
    @Test
    fun test_02_queryDataSourceSendsRecentlyDoneQuery() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"object":"list","results":[],"has_more":false,"next_cursor":null}"""))

        /** When **/
        service.queryDataSource(NotionService.authorization("tok"), "ds-1", NotionTodoMapper.recentlyDoneQuery(Instant.ofEpochMilli(1794971520000), null))

        /** Then **/
        val expectedBody = """
            {"filter":{"and":[
                {"property":"완료","checkbox":{"equals":true}},
                {"timestamp":"last_edited_time","last_edited_time":{"on_or_after":"2026-11-18T03:12:00.000Z"}}
             ]},
             "sorts":[{"timestamp":"last_edited_time","direction":"descending"}],
             "page_size":100}
        """
        assertEquals(json(expectedBody), json(server.takeRequest().body.readUtf8()))
    }

    /**
     * 페이지 추가를 POST로 보내고 받은 페이지를 해석하는지 검증
     *
     * Given: 페이지 p-1을 담은 200 응답, 서울 2026-11-20 09:30 시각 기한과 시각 알림의 Todo
     * When: createPage("Bearer tok", data source ds-1에 그 Todo를 추가하는 요청) 호출
     * Then: POST /v1/pages, 노션 헤더, parent와 이름·종류 Todo·완료·알림·기한(지역 시각과 time_zone) JSON 본문, 해석한 페이지 p-1
     */
    @Test
    fun test_03_createPageSendsPost() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody(pageBody))
        val todo = Todo(
            pageId = "local-1",
            title = "장보기",
            done = false,
            due = TodoDue.DateTime(Instant.ofEpochMilli(1795134600000)),
            reminder = TodoReminder.TIME,
            content = "",
            lastEditedTime = Instant.EPOCH,
            url = "",
        )

        /** When **/
        val result = service.createPage(
            NotionService.authorization("tok"),
            NotionTodoMapper.createPageRequest(todo, "ds-1", ZoneId.of("Asia/Seoul")),
        )

        /** Then **/
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/pages", request.path)
        assertNotionHeaders(request)
        val expectedBody = """
            {"parent":{"type":"data_source_id","data_source_id":"ds-1"},
             "properties":{
                "이름":{"title":[{"text":{"content":"장보기"}}]},
                "종류":{"select":{"name":"Todo"}},
                "완료":{"checkbox":false},
                "알림":{"select":{"name":"시각"}},
                "기한":{"date":{"start":"2026-11-20T09:30:00","time_zone":"Asia/Seoul"}}
             }}
        """
        assertEquals(json(expectedBody), json(request.body.readUtf8()))
        assertEquals("p-1", (result as NetworkResult.Success).data.id)
    }

    /**
     * 완료 체크를 PATCH로 보내고 받은 페이지를 해석하는지 검증
     *
     * Given: 완료된 페이지 p-1을 담은 200 응답
     * When: updatePage("Bearer tok", "p-1", 완료 true 요청) 호출
     * Then: PATCH /v1/pages/p-1, 노션 헤더, 완료 체크만 있는 JSON 본문, 해석한 페이지의 완료 true
     */
    @Test
    fun test_04_updatePageSendsPatch() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody(pageBody))

        /** When **/
        val result = service.updatePage(NotionService.authorization("tok"), "p-1", NotionTodoMapper.updateDoneRequest(true))

        /** Then **/
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/v1/pages/p-1", request.path)
        assertNotionHeaders(request)
        assertEquals(json("""{"properties":{"완료":{"checkbox":true}}}"""), json(request.body.readUtf8()))
        val page = (result as NetworkResult.Success).data
        assertEquals("p-1", page.id)
        assertEquals(true, page.properties.getValue("완료").checkbox)
    }

    /**
     * 2xx가 아닌 응답의 상태 코드와 Retry-After 헤더를 넘기는지 검증
     *
     * Given: 429(Retry-After 2)와 노션 오류 본문, 이어서 401과 노션 오류 본문
     * When: queryDataSource와 updatePage 호출
     * Then: HttpFailure(429)의 Retry-After 2, HttpFailure(401)
     */
    @Test
    fun test_05_errorStatusKeepsRetryAfter() = runTest {
        /** Given **/
        val errorBody = """{"object":"error","status":429,"code":"rate_limited"}"""
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2").setBody(errorBody))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"object":"error","status":401,"code":"unauthorized"}"""))

        /** When **/
        val rateLimited = service.queryDataSource(NotionService.authorization("tok"), "ds-1", NotionTodoMapper.openTodosQuery(null))
        val unauthorized = service.updatePage(NotionService.authorization("tok"), "p-1", NotionTodoMapper.updateDoneRequest(true))

        /** Then **/
        val rateLimitFailure = rateLimited as NetworkResult.HttpFailure
        assertEquals(429, rateLimitFailure.statusCode)
        assertEquals("2", rateLimitFailure.headers["Retry-After"])
        assertEquals(401, (unauthorized as NetworkResult.HttpFailure).statusCode)
    }

    /**
     * 2xx 응답의 본문을 해석하지 못하면 ParseFailure로 돌려주는지 검증
     *
     * Given: 닫히지 않은 JSON 본문의 200 응답
     * When: queryDataSource 호출
     * Then: SerializationException을 원인으로 담은 ParseFailure
     */
    @Test
    fun test_06_brokenBodyReturnsParseFailure() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("{"))

        /** When **/
        val result = service.queryDataSource(NotionService.authorization("tok"), "ds-1", NotionTodoMapper.openTodosQuery(null))

        /** Then **/
        assertTrue((result as NetworkResult.ParseFailure).cause is SerializationException)
    }

    /**
     * 로그를 켜도 요청과 응답 본문의 할 일 내용은 남기지 않고 Authorization 값은 가리는지 검증
     *
     * Given: Network 태그 로그 수집, 로그 켬, 이름이 장보기인 페이지를 담은 200 응답
     * When: 이름이 비밀 할 일인 Todo의 createPage를 토큰 secret-token으로 호출
     * Then: 요청 줄과 응답 상태 로그는 있고, Authorization 줄은 값이 가려지며, 요청 본문과 응답 본문의 내용과 토큰은 어느 줄에도 없음
     */
    @Test
    fun test_07_loggingOmitsTodoContent() = runTest {
        /** Given **/
        val logLines = captureDebugLogs("Network")
        Network.setLoggingEnabled(true)
        server.enqueue(MockResponse().setResponseCode(200).setBody(pageBody))
        val todo = Todo(
            pageId = "local-1",
            title = "비밀 할 일",
            done = false,
            due = null,
            reminder = TodoReminder.NONE,
            content = "",
            lastEditedTime = Instant.EPOCH,
            url = "",
        )

        /** When **/
        service.createPage(NotionService.authorization("secret-token"), NotionTodoMapper.createPageRequest(todo, "ds-1", ZoneId.of("Asia/Seoul")))

        /** Then **/
        val logs = logLines.toList()
        assertTrue(logs.any { it.contains("--> POST") })
        assertTrue(logs.any { it.contains("<-- 200") })
        assertTrue(logs.any { it.contains("Authorization: ██") })
        assertFalse(logs.any { it.contains("비밀 할 일") })
        assertFalse(logs.any { it.contains("장보기") })
        assertFalse(logs.any { it.contains("secret-token") })
    }
}
