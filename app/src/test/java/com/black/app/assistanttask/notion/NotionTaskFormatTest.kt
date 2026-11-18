package com.black.app.assistanttask.notion

import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskKind
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.core.util.JsonUtil
import com.black.test.BaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.TimeZone

/** [NotionTaskFormat]의 노션 응답 해석과 요청 본문 생성 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotionTaskFormatTest : BaseTest() {

    private fun response(pages: String, hasMore: Boolean = false, nextCursor: String? = null): String {
        val cursor = if (nextCursor == null) "null" else "\"$nextCursor\""
        return """{"object":"list","results":[$pages],"has_more":$hasMore,"next_cursor":$cursor}"""
    }

    private fun page(properties: String, extra: String = ""): String =
        """{"object":"page","id":"page-1","last_edited_time":"2026-11-18T03:12:00.000Z",""" +
            """"url":"https://www.notion.so/page-1",$extra"properties":{$properties}}"""

    private fun titleProperty(text: String) =
        """"이름":{"type":"title","title":[{"plain_text":"$text"}]}"""

    private fun requestRoot(body: String): JsonObject = JsonUtil.kotlinSerialization.parseToJsonElement(body).jsonObject

    /**
     * 모든 속성이 있는 페이지가 올바르게 해석되는지 검증
     *
     * Given: 이름·종류·완료·기한·알림·내용이 모두 있는 페이지
     * When: parseQueryResponse 호출
     * Then: 속성이 매핑된 AssistantTask 하나, 다음 쪽 없음
     */
    @Test
    fun test_01_parsesFullPage() = runTest {
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
        val result = NotionTaskFormat.parseQueryResponse(response(page(properties)))

        /** Then **/
        val expected = AssistantTask(
            pageId = "page-1",
            title = "장보기",
            kind = AssistantTaskKind.TASK,
            done = false,
            due = AssistantTaskDue.DateOnly("2026-11-20"),
            reminder = AssistantTaskReminder.HOME_ARRIVAL,
            content = "우유",
            lastEditedTime = 1794971520000,
            url = "https://www.notion.so/page-1",
        )
        assertEquals(listOf(expected), result?.tasks)
        assertFalse(result!!.hasMore)
        assertNull(result.nextCursor)
    }

    /**
     * 없는 속성에 기본값이 적용되는지 검증
     *
     * Given: 이름만 있는 페이지
     * When: parseQueryResponse 호출
     * Then: TASK, 미완료, 기한 없음, NONE, 빈 내용
     */
    @Test
    fun test_02_appliesDefaultsForMissingProperties() = runTest {
        /** Given / When **/
        val task = NotionTaskFormat.parseQueryResponse(response(page(titleProperty("이름만"))))!!.tasks.single()

        /** Then **/
        assertEquals(AssistantTaskKind.TASK, task.kind)
        assertFalse(task.done)
        assertNull(task.due)
        assertEquals(AssistantTaskReminder.NONE, task.reminder)
        assertEquals("", task.content)
    }

    /**
     * 알림이 비고 기한에 시각이 있으면 TIME이 되는지 검증
     *
     * Given: 알림 select null, 시각 포함 기한
     * When: parseQueryResponse 호출
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
        val task = NotionTaskFormat.parseQueryResponse(response(page(properties)))!!.tasks.single()

        /** Then **/
        assertEquals(AssistantTaskReminder.TIME, task.reminder)
    }

    /**
     * 알림이 비고 기한이 날짜뿐이면 NONE이 되는지 검증
     *
     * Given: 알림 select null, 날짜만 있는 기한
     * When: parseQueryResponse 호출
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
        val task = NotionTaskFormat.parseQueryResponse(response(page(properties)))!!.tasks.single()

        /** Then **/
        assertEquals(AssistantTaskReminder.NONE, task.reminder)
    }

    /**
     * 모르는 select 값이 기본값으로 처리되는지 검증
     *
     * Given: 종류 "기타", 알림 "나중에"
     * When: parseQueryResponse 호출
     * Then: TASK, NONE
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
        val task = NotionTaskFormat.parseQueryResponse(response(page(properties)))!!.tasks.single()

        /** Then **/
        assertEquals(AssistantTaskKind.TASK, task.kind)
        assertEquals(AssistantTaskReminder.NONE, task.reminder)
    }

    /**
     * 휴지통 페이지가 결과에서 빠지는지 검증
     *
     * Given: in_trash true 페이지와 일반 페이지
     * When: parseQueryResponse 호출
     * Then: tasks 크기 1
     */
    @Test
    fun test_06_skipsTrashedPages() = runTest {
        /** Given **/
        val trashed = page(titleProperty("버림"), extra = """"in_trash":true,""")
        val normal = page(titleProperty("남김"))

        /** When **/
        val result = NotionTaskFormat.parseQueryResponse(response("$trashed,$normal"))

        /** Then **/
        assertEquals(1, result!!.tasks.size)
        assertEquals("남김", result.tasks.single().title)
    }

    /**
     * 페이지 넘김 정보를 읽는지 검증
     *
     * Given: has_more true, next_cursor "abc"
     * When: parseQueryResponse 호출
     * Then: hasMore true, nextCursor "abc"
     */
    @Test
    fun test_07_readsPagination() = runTest {
        /** Given / When **/
        val result = NotionTaskFormat.parseQueryResponse(response("", hasMore = true, nextCursor = "abc"))!!

        /** Then **/
        assertTrue(result.hasMore)
        assertEquals("abc", result.nextCursor)
    }

    /**
     * 깨진 JSON이 null이 되는지 검증
     *
     * Given: 닫히지 않은 JSON
     * When: parseQueryResponse 호출
     * Then: null
     */
    @Test
    fun test_08_returnsNullForBrokenJson() = runTest {
        /** Given / When / Then **/
        assertNull(NotionTaskFormat.parseQueryResponse("{"))
    }

    /**
     * 미완료 조회 요청 본문 검증
     *
     * Given: cursor null, cursor "abc"
     * When: openTasksQuery 호출
     * Then: 완료 false 필터, 기한 오름차순 정렬, page_size 100, cursor가 있을 때만 start_cursor
     */
    @Test
    fun test_09_buildsOpenTasksQuery() = runTest {
        /** Given / When **/
        val withoutCursor = requestRoot(NotionTaskFormat.openTasksQuery(null))
        val withCursor = requestRoot(NotionTaskFormat.openTasksQuery("abc"))

        /** Then **/
        val filter = withoutCursor.getValue("filter").jsonObject
        assertEquals("완료", filter.getValue("property").jsonPrimitive.content)
        assertFalse(filter.getValue("checkbox").jsonObject.getValue("equals").jsonPrimitive.boolean)
        assertEquals("기한", withoutCursor.getValue("sorts").jsonArray[0].jsonObject.getValue("property").jsonPrimitive.content)
        assertEquals(100, withoutCursor.getValue("page_size").jsonPrimitive.int)
        assertFalse(withoutCursor.containsKey("start_cursor"))
        assertEquals("abc", withCursor.getValue("start_cursor").jsonPrimitive.content)
    }

    /**
     * 최근 완료 조회 요청 본문 검증
     *
     * Given: since 1794971520000, cursor null
     * When: recentlyDoneQuery 호출
     * Then: 완료 true 조건과 on_or_after 시각 조건
     */
    @Test
    fun test_10_buildsRecentlyDoneQuery() = runTest {
        /** Given / When **/
        val root = requestRoot(NotionTaskFormat.recentlyDoneQuery(1794971520000, null))

        /** Then **/
        val conditions = root.getValue("filter").jsonObject.getValue("and").jsonArray
        assertTrue(conditions[0].jsonObject.getValue("checkbox").jsonObject.getValue("equals").jsonPrimitive.boolean)
        assertEquals(
            "2026-11-18T03:12:00.000Z",
            conditions[1].jsonObject.getValue("last_edited_time").jsonObject.getValue("on_or_after").jsonPrimitive.content,
        )
        assertFalse(root.containsKey("start_cursor"))
    }

    /**
     * 노션 오류 본문이 null이 되는지 검증
     *
     * Given: object가 error인 본문
     * When: parseQueryResponse 호출
     * Then: null
     */
    @Test
    fun test_11_returnsNullForErrorBody() = runTest {
        /** Given / When / Then **/
        assertNull(NotionTaskFormat.parseQueryResponse("""{"object":"error","status":401,"code":"unauthorized"}"""))
    }

    /**
     * 빈 객체가 null이 되는지 검증
     *
     * Given: `{}`
     * When: parseQueryResponse 호출
     * Then: null
     */
    @Test
    fun test_12_returnsNullForEmptyObject() = runTest {
        /** Given / When / Then **/
        assertNull(NotionTaskFormat.parseQueryResponse("{}"))
    }

    private fun task(
        due: AssistantTaskDue?,
        reminder: AssistantTaskReminder,
        done: Boolean = false,
        kind: AssistantTaskKind = AssistantTaskKind.TASK,
    ) = AssistantTask(
        pageId = "local-1",
        title = "장보기",
        kind = kind,
        done = done,
        due = due,
        reminder = reminder,
        content = "",
        lastEditedTime = 0L,
        url = "",
    )

    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    /**
     * 날짜만 있는 기한의 추가 요청 본문 검증
     *
     * Given: 기한 DateOnly, 알림 HOME_ARRIVAL, 미완료 항목
     * When: createPageBody 호출
     * Then: parent·이름·종류·완료·알림·기한이 노션 형식이고 기한에 time_zone 없음
     */
    @Test
    fun test_13_buildsCreateBodyWithDateOnly() = runTest {
        /** Given **/
        val task = task(AssistantTaskDue.DateOnly("2026-11-20"), AssistantTaskReminder.HOME_ARRIVAL)

        /** When **/
        val root = requestRoot(NotionTaskFormat.createPageBody(task, "ds-1", seoul))

        /** Then **/
        val parent = root["parent"]!!.jsonObject
        assertEquals("data_source_id", parent["type"]?.jsonPrimitive?.content)
        assertEquals("ds-1", parent["data_source_id"]?.jsonPrimitive?.content)
        val properties = root["properties"]!!.jsonObject
        val titleText = properties["이름"]!!.jsonObject["title"]!!.jsonArray[0].jsonObject["text"]!!.jsonObject
        assertEquals("장보기", titleText["content"]?.jsonPrimitive?.content)
        assertEquals("Todo", properties["종류"]!!.jsonObject["select"]!!.jsonObject["name"]?.jsonPrimitive?.content)
        assertFalse(properties["완료"]!!.jsonObject["checkbox"]!!.jsonPrimitive.boolean)
        assertEquals("집 도착", properties["알림"]!!.jsonObject["select"]!!.jsonObject["name"]?.jsonPrimitive?.content)
        val date = properties["기한"]!!.jsonObject["date"]!!.jsonObject
        assertEquals("2026-11-20", date["start"]?.jsonPrimitive?.content)
        assertFalse(date.containsKey("time_zone"))
    }

    /**
     * 시각이 있는 기한의 추가 요청 본문 검증
     *
     * Given: 기한 DateTime(서울 2026-11-20 09:30), 알림 TIME
     * When: createPageBody 호출
     * Then: 기한 start는 오프셋 없는 지역 시각, time_zone은 Asia/Seoul, 알림은 시각
     */
    @Test
    fun test_14_buildsCreateBodyWithDateTime() = runTest {
        /** Given **/
        val task = task(AssistantTaskDue.DateTime(1795134600000), AssistantTaskReminder.TIME)

        /** When **/
        val properties = requestRoot(NotionTaskFormat.createPageBody(task, "ds-1", seoul))["properties"]!!.jsonObject

        /** Then **/
        val date = properties["기한"]!!.jsonObject["date"]!!.jsonObject
        assertEquals("2026-11-20T09:30:00", date["start"]?.jsonPrimitive?.content)
        assertEquals("Asia/Seoul", date["time_zone"]?.jsonPrimitive?.content)
        assertEquals("시각", properties["알림"]!!.jsonObject["select"]!!.jsonObject["name"]?.jsonPrimitive?.content)
    }

    /**
     * 기한이 없는 추가 요청 본문 검증
     *
     * Given: 기한 null, 알림 NONE
     * When: createPageBody 호출
     * Then: properties에 기한 키 없음, 알림은 없음
     */
    @Test
    fun test_15_omitsDueWhenNull() = runTest {
        /** Given **/
        val task = task(null, AssistantTaskReminder.NONE)

        /** When **/
        val properties = requestRoot(NotionTaskFormat.createPageBody(task, "ds-1", seoul))["properties"]!!.jsonObject

        /** Then **/
        assertFalse(properties.containsKey("기한"))
        assertEquals("없음", properties["알림"]!!.jsonObject["select"]!!.jsonObject["name"]?.jsonPrimitive?.content)
    }

    /**
     * 완료 체크 요청 본문 검증
     *
     * Given: done true
     * When: updateDoneBody 호출
     * Then: properties.완료.checkbox만 있는 본문
     */
    @Test
    fun test_16_buildsUpdateDoneBody() = runTest {
        /** Given / When **/
        val root = requestRoot(NotionTaskFormat.updateDoneBody(true))

        /** Then **/
        assertEquals(requestRoot("""{"properties":{"완료":{"checkbox":true}}}"""), root)
    }

    /**
     * page 응답이 조회 결과와 같은 규칙으로 해석되는지 검증
     *
     * Given: object가 page인 본문
     * When: parsePageResponse 호출
     * Then: 같은 본문을 목록으로 감싼 조회 결과의 첫 항목과 같음
     */
    @Test
    fun test_17_parsesPageResponse() = runTest {
        /** Given **/
        val body = page(titleProperty("장보기"))

        /** When **/
        val task = NotionTaskFormat.parsePageResponse(body)

        /** Then **/
        assertEquals(NotionTaskFormat.parseQueryResponse(response(body))!!.tasks.single(), task)
        assertEquals("page-1", task?.pageId)
    }

    /**
     * page가 아닌 응답이 null이 되는지 검증
     *
     * Given: error 본문, JSON 아님, id 없음, in_trash true
     * When: parsePageResponse 호출
     * Then: 모두 null
     */
    @Test
    fun test_18_returnsNullForNonPageResponse() = runTest {
        /** Given / When / Then **/
        assertNull(NotionTaskFormat.parsePageResponse("""{"object":"error","status":400,"code":"validation_error"}"""))
        assertNull(NotionTaskFormat.parsePageResponse("not json"))
        assertNull(NotionTaskFormat.parsePageResponse("""{"object":"page","properties":{}}"""))
        assertNull(NotionTaskFormat.parsePageResponse(page(titleProperty("장보기"), extra = """"in_trash":true,""")))
    }

    /**
     * id 없는 페이지가 조회 결과에서 빠지는지 검증
     *
     * Given: id 없는 페이지와 정상 페이지
     * When: parseQueryResponse 호출
     * Then: 정상 페이지만 남음
     */
    @Test
    fun test_19_dropsPageWithoutId() = runTest {
        /** Given **/
        val noId = """{"object":"page","properties":{${titleProperty("없음")}}}"""

        /** When **/
        val result = NotionTaskFormat.parseQueryResponse(response("$noId,${page(titleProperty("있음"))}"))

        /** Then **/
        assertEquals(listOf("있음"), result?.tasks?.map { it.title })
    }

    /**
     * 종류 값 Todo, 예전 값 할 일, 메모가 각각 해석되는지 검증
     *
     * Given: 종류가 "Todo", "할 일", "메모"인 페이지 셋
     * When: parseQueryResponse 호출
     * Then: TASK, TASK, MEMO
     */
    @Test
    fun test_20_parsesCurrentAndLegacyKindValues() = runTest {
        /** Given **/
        val pages = listOf("Todo", "할 일", "메모").joinToString(",") { kind ->
            page("""${titleProperty(kind)},"종류":{"select":{"name":"$kind"}}""")
        }

        /** When **/
        val result = NotionTaskFormat.parseQueryResponse(response(pages))

        /** Then **/
        assertEquals(
            listOf(AssistantTaskKind.TASK, AssistantTaskKind.TASK, AssistantTaskKind.MEMO),
            result?.tasks?.map { it.kind },
        )
    }
}
