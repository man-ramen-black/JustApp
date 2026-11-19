package com.black.feature.todo.data.network

import com.black.core.util.JsonUtil
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** [NotionTodoMapper]의 요청 생성, 조회·페이지 응답 변환, 날짜·시각·시간대 해석 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotionTodoMapperTest : BaseTest() {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val utc: ZoneId = ZoneOffset.UTC

    /** 페이지 JSON 목록 [pages]를 담은 조회 응답 */
    private fun response(pages: String): NotionQueryResponse = JsonUtil.kotlinSerialization.decodeFromString(
        NotionQueryResponse.serializer(),
        """{"object":"list","results":[$pages],"has_more":false,"next_cursor":null}""",
    )

    private fun page(properties: String, extra: String = ""): String =
        """{"object":"page","id":"page-1","last_edited_time":"2026-11-18T03:12:00.000Z",""" +
            """"url":"https://www.notion.so/page-1",$extra"properties":{$properties}}"""

    private fun decodePage(json: String): NotionPage = JsonUtil.kotlinSerialization.decodeFromString(NotionPage.serializer(), json)

    private fun titleProperty(text: String) =
        """"이름":{"type":"title","title":[{"plain_text":"$text"}]}"""

    /** 기한 [start]와 [timeZone]만 있는 페이지를 [defaultZone] 기준으로 바꾼 Todo의 기한 */
    private fun dueOf(start: String?, timeZone: String? = null, defaultZone: ZoneId = seoul): TodoDue? {
        val page = NotionPage(
            objectType = "page",
            id = "page-1",
            properties = mapOf(NotionTodoMapper.PROPERTY_DUE to NotionProperty(date = NotionDate(start, timeZone))),
        )
        return NotionTodoMapper.toTodo(page, defaultZone)!!.due
    }

    private fun json(text: String): JsonElement = JsonUtil.kotlinSerialization.parseToJsonElement(text)

    private fun encode(request: NotionQueryRequest): JsonObject =
        JsonUtil.kotlinSerialization.encodeToJsonElement(NotionQueryRequest.serializer(), request).jsonObject

    private fun encode(request: NotionCreatePageRequest): JsonObject =
        JsonUtil.kotlinSerialization.encodeToJsonElement(NotionCreatePageRequest.serializer(), request).jsonObject

    private fun todo(due: TodoDue?, reminder: TodoReminder, done: Boolean = false) = Todo(
        pageId = "local-1",
        title = "장보기",
        done = done,
        due = due,
        reminder = reminder,
        content = "",
        lastEditedTime = Instant.EPOCH,
        url = "",
    )

    /**
     * 모든 속성이 있는 페이지가 올바르게 변환되는지 검증
     *
     * Given: 이름, 예전 종류 값 할 일, 완료, 기한, 알림, 내용이 모두 있는 페이지
     * When: toTodos 호출
     * Then: 속성이 매핑된 Todo 하나
     */
    @Test
    fun test_01_convertsFullPage() = runTest {
        /** Given **/
        val properties = listOf(
            titleProperty("장보기"),
            """"종류":{"select":{"name":"할 일"}}""",
            """"완료":{"checkbox":false}""",
            """"기한":{"date":{"start":"2026-11-20","end":null,"time_zone":null}}""",
            """"알림":{"select":{"name":"집 도착"}}""",
            """"내용":{"rich_text":[{"plain_text":"우"},{"plain_text":"유"}]}""",
        ).joinToString(",")

        /** When **/
        val todos = NotionTodoMapper.toTodos(response(page(properties)), seoul)

        /** Then **/
        val expected = Todo(
            pageId = "page-1",
            title = "장보기",
            done = false,
            due = TodoDue.Date(LocalDate.of(2026, 11, 20)),
            reminder = TodoReminder.HOME_ARRIVAL,
            content = "우유",
            lastEditedTime = Instant.ofEpochMilli(1794971520000),
            url = "https://www.notion.so/page-1",
        )
        assertEquals(listOf(expected), todos)
    }

    /**
     * 없는 속성에 기본값이 적용되는지 검증
     *
     * Given: 이름만 있는 페이지
     * When: toTodos 호출
     * Then: 미완료, 기한 없음, NONE, 빈 내용
     */
    @Test
    fun test_02_appliesDefaultsForMissingProperties() = runTest {
        /** Given / When **/
        val todo = NotionTodoMapper.toTodos(response(page(titleProperty("이름만"))), seoul)!!.single()

        /** Then **/
        assertFalse(todo.done)
        assertNull(todo.due)
        assertEquals(TodoReminder.NONE, todo.reminder)
        assertEquals("", todo.content)
    }

    /**
     * 알림이 비고 기한에 시각이 있으면 TIME이 되는지 검증
     *
     * Given: 알림 select null, 시각 포함 기한
     * When: toTodos 호출
     * Then: reminder TIME
     */
    @Test
    fun test_03_emptyReminderWithDateTimeBecomesTime() = runTest {
        /** Given **/
        val properties = """
            ${titleProperty("시각")},
            "알림":{"select":null},
            "기한":{"date":{"start":"2026-11-18T09:30:00.000+09:00","end":null,"time_zone":null}}
        """.trimIndent()

        /** When **/
        val todo = NotionTodoMapper.toTodos(response(page(properties)), seoul)!!.single()

        /** Then **/
        assertEquals(TodoReminder.TIME, todo.reminder)
    }

    /**
     * 알림이 비고 기한이 날짜뿐이면 NONE이 되는지 검증
     *
     * Given: 알림 select null, 날짜만 있는 기한
     * When: toTodos 호출
     * Then: reminder NONE
     */
    @Test
    fun test_04_emptyReminderWithDateOnlyBecomesNone() = runTest {
        /** Given **/
        val properties = """
            ${titleProperty("날짜")},
            "알림":{"select":null},
            "기한":{"date":{"start":"2026-11-18","end":null,"time_zone":null}}
        """.trimIndent()

        /** When **/
        val todo = NotionTodoMapper.toTodos(response(page(properties)), seoul)!!.single()

        /** Then **/
        assertEquals(TodoReminder.NONE, todo.reminder)
    }

    /**
     * 모르는 선택 값이 기본값으로 처리되는지 검증
     *
     * Given: 종류 "기타", 알림 "나중에"인 페이지
     * When: toTodos 호출
     * Then: Todo로 남고 reminder NONE
     */
    @Test
    fun test_05_unknownSelectValues() = runTest {
        /** Given **/
        val properties = """
            ${titleProperty("모름")},
            "종류":{"select":{"name":"기타"}},
            "알림":{"select":{"name":"나중에"}}
        """.trimIndent()

        /** When **/
        val todos = NotionTodoMapper.toTodos(response(page(properties)), seoul)

        /** Then **/
        assertEquals(TodoReminder.NONE, todos!!.single().reminder)
    }

    /**
     * 휴지통·보관 페이지가 결과에서 빠지는지 검증
     *
     * Given: in_trash true 페이지, archived true 페이지, 일반 페이지
     * When: toTodos 호출
     * Then: 일반 페이지만 남음
     */
    @Test
    fun test_06_skipsTrashedAndArchivedPages() = runTest {
        /** Given **/
        val trashed = page(titleProperty("버림"), extra = """"in_trash":true,""")
        val archived = page(titleProperty("보관"), extra = """"archived":true,""")
        val normal = page(titleProperty("남김"))

        /** When **/
        val todos = NotionTodoMapper.toTodos(response("$trashed,$archived,$normal"), seoul)

        /** Then **/
        assertEquals(listOf("남김"), todos?.map { it.title })
    }

    /**
     * id 없는 페이지가 결과에서 빠지는지 검증
     *
     * Given: id 없는 페이지와 정상 페이지
     * When: toTodos 호출
     * Then: 정상 페이지만 남음
     */
    @Test
    fun test_07_dropsPageWithoutId() = runTest {
        /** Given **/
        val noId = """{"object":"page","properties":{${titleProperty("없음")}}}"""

        /** When **/
        val todos = NotionTodoMapper.toTodos(response("$noId,${page(titleProperty("있음"))}"), seoul)

        /** Then **/
        assertEquals(listOf("있음"), todos?.map { it.title })
    }

    /**
     * 종류 값 Todo와 예전 값 할 일은 Todo로 남고 메모는 걸러지는지 검증
     *
     * Given: 종류가 "Todo", "할 일", "메모"인 페이지 셋
     * When: toTodos와 메모 페이지의 toTodo 호출
     * Then: Todo·할 일 두 항목만 남고, 메모 페이지의 toTodo는 null
     */
    @Test
    fun test_08_filtersMemoKind() = runTest {
        /** Given **/
        val pages = listOf("Todo", "할 일", "메모").map { kind ->
            page("""${titleProperty(kind)},"종류":{"select":{"name":"$kind"}}""")
        }

        /** When **/
        val todos = NotionTodoMapper.toTodos(response(pages.joinToString(",")), seoul)
        val memo = NotionTodoMapper.toTodo(decodePage(pages[2]), seoul)

        /** Then **/
        assertEquals(listOf("Todo", "할 일"), todos?.map { it.title })
        assertNull(memo)
    }

    /**
     * 조회 결과가 아닌 본문이 null이 되는지 검증
     *
     * Given: object가 error인 본문, 빈 객체, results 없는 list
     * When: toTodos 호출
     * Then: 모두 null
     */
    @Test
    fun test_09_returnsNullForNonListResponse() = runTest {
        /** Given **/
        val bodies = listOf("""{"object":"error","status":401,"code":"unauthorized"}""", "{}", """{"object":"list"}""")

        /** When **/
        val results = bodies.map { body ->
            NotionTodoMapper.toTodos(JsonUtil.kotlinSerialization.decodeFromString(NotionQueryResponse.serializer(), body), seoul)
        }

        /** Then **/
        assertEquals(listOf(null, null, null), results)
    }

    /**
     * 미완료 조회 요청 검증
     *
     * Given: cursor null, cursor "abc"
     * When: openTodosQuery 호출
     * Then: 완료 false 필터, 기한 오름차순 정렬, page_size 100, cursor가 있을 때만 start_cursor
     */
    @Test
    fun test_10_buildsOpenTodosQuery() = runTest {
        /** Given / When **/
        val withoutCursor = encode(NotionTodoMapper.openTodosQuery(null))
        val withCursor = encode(NotionTodoMapper.openTodosQuery("abc"))

        /** Then **/
        val expected = """
            {"filter":{"property":"완료","checkbox":{"equals":false}},
             "sorts":[{"property":"기한","direction":"ascending"}],
             "page_size":100}
        """
        assertEquals(json(expected), withoutCursor)
        assertEquals(JsonObject(json(expected).jsonObject + ("start_cursor" to JsonPrimitive("abc"))), withCursor)
    }

    /**
     * 최근 완료 조회 요청 검증
     *
     * Given: since 1794971520000, cursor null
     * When: recentlyDoneQuery 호출
     * Then: 완료 true 조건과 UTC 밀리초 형식의 on_or_after 수정 시각 조건, 수정 시각 내림차순 정렬, page_size 100, start_cursor 없음
     */
    @Test
    fun test_11_buildsRecentlyDoneQuery() = runTest {
        /** Given / When **/
        val root = encode(NotionTodoMapper.recentlyDoneQuery(Instant.ofEpochMilli(1794971520000), null))

        /** Then **/
        val expected = """
            {"filter":{"and":[
                {"property":"완료","checkbox":{"equals":true}},
                {"timestamp":"last_edited_time","last_edited_time":{"on_or_after":"2026-11-18T03:12:00.000Z"}}
             ]},
             "sorts":[{"timestamp":"last_edited_time","direction":"descending"}],
             "page_size":100}
        """
        assertEquals(json(expected), root)
    }

    /**
     * 날짜만 있는 기한의 추가 요청 검증
     *
     * Given: 기한 Date(2026-11-20), 알림 HOME_ARRIVAL, 미완료 항목
     * When: createPageRequest 호출
     * Then: parent·이름·종류 Todo·완료·알림·기한이 노션 형식이고 기한에 time_zone 없음
     */
    @Test
    fun test_12_buildsCreateRequestWithDateOnly() = runTest {
        /** Given **/
        val todo = todo(TodoDue.Date(LocalDate.of(2026, 11, 20)), TodoReminder.HOME_ARRIVAL)

        /** When **/
        val root = encode(NotionTodoMapper.createPageRequest(todo, "ds-1", seoul))

        /** Then **/
        val expected = """
            {"parent":{"type":"data_source_id","data_source_id":"ds-1"},
             "properties":{
                "이름":{"title":[{"text":{"content":"장보기"}}]},
                "종류":{"select":{"name":"Todo"}},
                "완료":{"checkbox":false},
                "알림":{"select":{"name":"집 도착"}},
                "기한":{"date":{"start":"2026-11-20"}}
             }}
        """
        assertEquals(json(expected), root)
    }

    /**
     * 시각이 있는 기한의 추가 요청 검증
     *
     * Given: 기한 DateTime(서울 2026-11-20 09:30), 알림 TIME, 완료 항목
     * When: createPageRequest 호출
     * Then: 기한 start는 초까지 있고 오프셋 없는 지역 시각, time_zone은 Asia/Seoul, 알림은 시각, 완료 true
     */
    @Test
    fun test_13_buildsCreateRequestWithDateTime() = runTest {
        /** Given **/
        val todo = todo(TodoDue.DateTime(Instant.ofEpochMilli(1795134600000)), TodoReminder.TIME, done = true)

        /** When **/
        val properties = encode(NotionTodoMapper.createPageRequest(todo, "ds-1", seoul)).getValue("properties").jsonObject

        /** Then **/
        assertEquals(json("""{"date":{"start":"2026-11-20T09:30:00","time_zone":"Asia/Seoul"}}"""), properties["기한"])
        assertEquals(json("""{"select":{"name":"시각"}}"""), properties["알림"])
        assertEquals(json("""{"checkbox":true}"""), properties["완료"])
    }

    /**
     * 기한이 없는 추가 요청 검증
     *
     * Given: 기한 null, 알림 NONE
     * When: createPageRequest 호출
     * Then: properties에 기한 키 없음, 알림은 없음
     */
    @Test
    fun test_14_omitsDueWhenNull() = runTest {
        /** Given **/
        val todo = todo(null, TodoReminder.NONE)

        /** When **/
        val properties = encode(NotionTodoMapper.createPageRequest(todo, "ds-1", seoul)).getValue("properties").jsonObject

        /** Then **/
        assertFalse(properties.containsKey("기한"))
        assertEquals(json("""{"select":{"name":"없음"}}"""), properties["알림"])
    }

    /**
     * 완료 체크 요청 검증
     *
     * Given: done true
     * When: updateDoneRequest 호출
     * Then: properties.완료.checkbox만 있는 본문
     */
    @Test
    fun test_15_buildsUpdateDoneRequest() = runTest {
        /** Given / When **/
        val root = JsonUtil.kotlinSerialization.encodeToJsonElement(NotionUpdatePageRequest.serializer(), NotionTodoMapper.updateDoneRequest(true))

        /** Then **/
        assertEquals(json("""{"properties":{"완료":{"checkbox":true}}}"""), root)
    }

    /**
     * 페이지 응답이 조회 결과와 같은 규칙으로 변환되는지 검증
     *
     * Given: object가 page인 본문
     * When: toTodo 호출
     * Then: 같은 본문을 목록으로 감싼 toTodos의 첫 항목과 같음
     */
    @Test
    fun test_16_convertsPageResponseLikeQuery() = runTest {
        /** Given **/
        val body = page(titleProperty("장보기"))

        /** When **/
        val todo = NotionTodoMapper.toTodo(decodePage(body), seoul)

        /** Then **/
        assertEquals(NotionTodoMapper.toTodos(response(body), seoul)!!.single(), todo)
        assertEquals("page-1", todo?.pageId)
    }

    /**
     * 페이지 추가·수정 응답이 쓸 수 있는 페이지인지 가리는지 검증
     *
     * Given: 일반 페이지, 메모 페이지, error 본문, id 없는 페이지, 휴지통 페이지, 보관 페이지
     * When: isUsablePage 호출
     * Then: 일반·메모 페이지만 true
     */
    @Test
    fun test_17_checksUsablePage() = runTest {
        /** Given **/
        val normal = decodePage(page(titleProperty("장보기")))
        val memo = decodePage(page("""${titleProperty("메모")},"종류":{"select":{"name":"메모"}}"""))
        val error = decodePage("""{"object":"error","status":400,"code":"validation_error"}""")
        val noId = decodePage("""{"object":"page","properties":{}}""")
        val trashed = decodePage(page(titleProperty("장보기"), extra = """"in_trash":true,"""))
        val archived = decodePage(page(titleProperty("장보기"), extra = """"archived":true,"""))

        /** When / Then **/
        assertTrue(NotionTodoMapper.isUsablePage(normal))
        assertTrue(NotionTodoMapper.isUsablePage(memo))
        listOf(error, noId, trashed, archived).forEach { page ->
            assertFalse("page=$page", NotionTodoMapper.isUsablePage(page))
        }
    }

    /**
     * 날짜만 있는 기한이 그 날짜의 Date가 되는지 검증
     *
     * Given: start "2026-11-18"
     * When: toTodo 호출
     * Then: Date(2026-11-18)
     */
    @Test
    fun test_18_parsesDateOnly() = runTest {
        /** Given / When / Then **/
        assertEquals(TodoDue.Date(LocalDate.of(2026, 11, 18)), dueOf("2026-11-18"))
    }

    /**
     * 오프셋이 있는 시각이 time_zone과 상관없이 그 오프셋 기준으로 해석되는지 검증
     *
     * Given: +09:00 오프셋 시각, Z 시각, +09:00 오프셋 시각과 America/New_York time_zone
     * When: toTodo 호출
     * Then: 모두 2026-11-18T00:30Z
     */
    @Test
    fun test_19_parsesDateTimeWithOffset() = runTest {
        /** Given / When / Then **/
        val expected = TodoDue.DateTime(Instant.ofEpochMilli(1794961800000))
        assertEquals(expected, dueOf("2026-11-18T09:30:00.000+09:00"))
        assertEquals(expected, dueOf("2026-11-18T00:30:00Z"))
        assertEquals(expected, dueOf("2026-11-18T09:30:00.000+09:00", timeZone = "America/New_York"))
    }

    /**
     * 오프셋이 없으면 time_zone 필드를 쓰는지 검증
     *
     * Given: 오프셋 없는 시각, time_zone Asia/Seoul, 기본 시간대 UTC
     * When: toTodo 호출
     * Then: 서울 기준 DateTime
     */
    @Test
    fun test_20_usesTimeZoneFieldWithoutOffset() = runTest {
        /** Given / When / Then **/
        assertEquals(TodoDue.DateTime(Instant.ofEpochMilli(1794961800000)), dueOf("2026-11-18T09:30:00.000", timeZone = "Asia/Seoul", defaultZone = utc))
    }

    /**
     * 오프셋과 time_zone이 모두 없으면 기본 시간대를 쓰는지 검증
     *
     * Given: 오프셋·time_zone 없는 시각, 기본 시간대 UTC
     * When: toTodo 호출
     * Then: UTC 기준 DateTime
     */
    @Test
    fun test_21_usesDefaultZoneWithoutOffsetAndTimeZone() = runTest {
        /** Given / When / Then **/
        assertEquals(TodoDue.DateTime(Instant.ofEpochMilli(1794994200000)), dueOf("2026-11-18T09:30:00", defaultZone = utc))
    }

    /**
     * 모르는 time_zone이 기본 시간대로 해석되는지 검증
     *
     * Given: 오프셋 없는 시각, 모르는 time_zone과 빈 time_zone, 기본 시간대 서울
     * When: toTodo 호출
     * Then: 모두 서울 기준 DateTime
     */
    @Test
    fun test_22_unknownTimeZoneFallsBackToDefaultZone() = runTest {
        /** Given / When / Then **/
        assertEquals(TodoDue.DateTime(Instant.ofEpochMilli(1794961800000)), dueOf("2026-11-18T09:30:00", timeZone = "Mars/Base"))
        assertEquals(TodoDue.DateTime(Instant.ofEpochMilli(1794961800000)), dueOf("2026-11-18T09:30:00", timeZone = ""))
    }

    /**
     * 형식이 맞지 않는 기한이 기한 없음이 되는지 검증
     *
     * Given: 한글, null, 빈 문자열, 한 자리 월, 25시, 날짜 뒤 T만 있는 값
     * When: toTodo 호출
     * Then: 모두 null
     */
    @Test
    fun test_23_returnsNullForMalformedDue() = runTest {
        /** Given / When / Then **/
        listOf("내일", null, "", "2026-1-18", "2026-11-18T25:00:00", "2026-11-18T").forEach { start ->
            assertNull("start=$start", dueOf(start))
        }
    }

    /**
     * 존재하지 않는 날짜가 기한 없음이 되는지 검증
     *
     * Given: 2월 30일 날짜, 2월 30일 시각
     * When: toTodo 호출
     * Then: 모두 null
     */
    @Test
    fun test_24_returnsNullForNonexistentDate() = runTest {
        /** Given / When / Then **/
        assertNull(dueOf("2026-02-30"))
        assertNull(dueOf("2026-02-30T09:00:00+09:00"))
    }

    /**
     * 수정 시각을 읽고 읽을 수 없으면 Instant.EPOCH가 되는지 검증
     *
     * Given: UTC 시각 문자열, 잘못된 문자열, 수정 시각 없음
     * When: toTodo 호출
     * Then: 2026-11-18T03:12:00Z(1794971520000), EPOCH, EPOCH
     */
    @Test
    fun test_25_parsesLastEditedTime() = runTest {
        /** Given **/
        val pages = listOf("2026-11-18T03:12:00.000Z", "x", null).map { lastEditedTime ->
            NotionPage(objectType = "page", id = "page-1", lastEditedTime = lastEditedTime)
        }

        /** When **/
        val lastEditedTimes = pages.map { page -> NotionTodoMapper.toTodo(page, seoul)!!.lastEditedTime }

        /** Then **/
        assertEquals(listOf(Instant.ofEpochMilli(1794971520000), Instant.EPOCH, Instant.EPOCH), lastEditedTimes)
    }

    /**
     * 저장본이 epoch 밀리초로 담을 수 없는 연도의 기한과 수정 시각을 해석할 수 없는 값으로 보는지 검증
     *
     * Given: +999999999년의 오프셋 있는 시각 기한, 오프셋 없는 시각 기한, 수정 시각
     * When: toTodo 호출
     * Then: 두 기한은 null, 수정 시각은 Instant.EPOCH
     */
    @Test
    fun test_26_rejectsInstantOutsideStorableRange() = runTest {
        /** Given **/
        val farFuture = "+999999999-01-01T00:00:00"
        val page = NotionPage(objectType = "page", id = "page-1", lastEditedTime = "${farFuture}Z")

        /** When **/
        val lastEditedTime = NotionTodoMapper.toTodo(page, seoul)!!.lastEditedTime

        /** Then **/
        assertNull(dueOf("${farFuture}Z"))
        assertNull(dueOf(farFuture))
        assertEquals(Instant.EPOCH, lastEditedTime)
    }

    /**
     * `Z` 대신 오프셋이 붙은 수정 시각을 같은 순간으로 읽는지 검증
     *
     * Given: 2026-11-18T12:12:00.000+09:00 수정 시각
     * When: toTodo 호출
     * Then: 2026-11-18T03:12:00Z(1794971520000)
     */
    @Test
    fun test_27_parsesLastEditedTimeWithOffset() = runTest {
        /** Given **/
        val page = NotionPage(objectType = "page", id = "page-1", lastEditedTime = "2026-11-18T12:12:00.000+09:00")

        /** When **/
        val lastEditedTime = NotionTodoMapper.toTodo(page, seoul)!!.lastEditedTime

        /** Then **/
        assertEquals(Instant.ofEpochMilli(1794971520000), lastEditedTime)
    }
}
