package com.black.app.assistanttask.notion

import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskKind
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.core.util.JsonUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.TimeZone

/** 노션 데이터베이스 조회 응답 한 쪽 */
data class NotionQueryPage(
    val tasks: List<AssistantTask>,
    val hasMore: Boolean,
    val nextCursor: String?,
)

/** 노션 데이터베이스 조회·페이지 추가·완료 체크 요청 본문 생성과 응답 해석 */
object NotionTaskFormat {
    const val PAGE_SIZE = 100

    /** 미완료 할 일을 기한 오름차순으로 조회하는 요청 본문 */
    fun openTasksQuery(cursor: String?): String = JsonUtil.kotlinSerialization.encodeToString(
        QueryRequestDto(
            filter = FilterDto(
                property = NotionTaskProperties.DONE,
                checkbox = CheckboxFilterDto(equals = false),
            ),
            sorts = listOf(SortDto(property = NotionTaskProperties.DUE, direction = "ascending")),
            startCursor = cursor,
        )
    )

    /** [sinceMillis] 이후 수정된 완료 항목을 최근 수정 순으로 조회하는 요청 본문 */
    fun recentlyDoneQuery(sinceMillis: Long, cursor: String?): String = JsonUtil.kotlinSerialization.encodeToString(
        QueryRequestDto(
            filter = FilterDto(
                and = listOf(
                    FilterDto(
                        property = NotionTaskProperties.DONE,
                        checkbox = CheckboxFilterDto(equals = true),
                    ),
                    FilterDto(
                        timestamp = "last_edited_time",
                        lastEditedTime = DateFilterDto(onOrAfter = NotionDateParser.formatTimestamp(sinceMillis)),
                    ),
                ),
            ),
            sorts = listOf(SortDto(timestamp = "last_edited_time", direction = "descending")),
            startCursor = cursor,
        )
    )

    /** 조회 응답 해석. 해석에 실패하면 null */
    fun parseQueryResponse(body: String): NotionQueryPage? {
        val response = try {
            JsonUtil.kotlinSerialization.decodeFromString<QueryResponseDto>(body)
        } catch (exception: IllegalArgumentException) {
            // SerializationException 포함
            return null
        }
        // 오류 본문(`object: error`)이나 빈 객체는 조회 결과가 아님
        val results = response.results ?: return null
        if (response.objectType != "list") return null
        val tasks = results
            .filterNot { it.inTrash || it.archived }
            .mapNotNull { it.toTask() }
        return NotionQueryPage(tasks, response.hasMore, response.nextCursor)
    }

    /**
     * [dataSourceId]에 [task]를 추가하는 요청 본문
     *
     * 시각 기한은 오프셋 없는 지역 시각과 [zone]의 ID로 보냄. 기한이 없으면 `기한` 속성을 보내지 않음.
     */
    fun createPageBody(task: AssistantTask, dataSourceId: String, zone: TimeZone): String =
        JsonUtil.kotlinSerialization.encodeToString(
            CreatePageRequestDto(
                parent = ParentDto(dataSourceId = dataSourceId),
                properties = buildMap {
                    put(
                        NotionTaskProperties.TITLE,
                        PropertyDto(title = listOf(RichTextDto(text = TextDto(content = task.title)))),
                    )
                    put(NotionTaskProperties.KIND, PropertyDto(select = SelectDto(name = task.kind.notionName())))
                    put(NotionTaskProperties.DONE, PropertyDto(checkbox = task.done))
                    put(NotionTaskProperties.REMINDER, PropertyDto(select = SelectDto(name = task.reminder.notionName())))
                    task.due?.let { due -> put(NotionTaskProperties.DUE, PropertyDto(date = due.toDateDto(zone))) }
                },
            )
        )

    /** 페이지의 `완료` 체크 상자를 [done]으로 바꾸는 요청 본문 */
    fun updateDoneBody(done: Boolean): String = JsonUtil.kotlinSerialization.encodeToString(
        UpdatePageRequestDto(mapOf(NotionTaskProperties.DONE to PropertyDto(checkbox = done)))
    )

    /** 페이지 추가·수정 응답 해석. 페이지가 아니거나 휴지통에 있으면 null */
    fun parsePageResponse(body: String): AssistantTask? {
        val page = try {
            JsonUtil.kotlinSerialization.decodeFromString<PageDto>(body)
        } catch (exception: IllegalArgumentException) {
            // SerializationException 포함
            return null
        }
        if (page.objectType != "page" || page.inTrash || page.archived) return null
        return page.toTask()
    }

    private fun AssistantTaskKind.notionName(): String = when (this) {
        AssistantTaskKind.TASK -> NotionTaskProperties.KIND_TASK
        AssistantTaskKind.MEMO -> NotionTaskProperties.KIND_MEMO
    }

    private fun AssistantTaskReminder.notionName(): String = when (this) {
        AssistantTaskReminder.HOME_ARRIVAL -> NotionTaskProperties.REMINDER_HOME_ARRIVAL
        AssistantTaskReminder.TIME -> NotionTaskProperties.REMINDER_TIME
        AssistantTaskReminder.NONE -> NotionTaskProperties.REMINDER_NONE
    }

    private fun AssistantTaskDue.toDateDto(zone: TimeZone): DateDto = when (this) {
        is AssistantTaskDue.DateOnly -> DateDto(start = date)
        is AssistantTaskDue.DateTime -> DateDto(
            start = NotionDateParser.formatLocalDateTime(epochMillis, zone),
            timeZone = zone.id,
        )
    }

    private fun PageDto.toTask(): AssistantTask? {
        val pageId = id ?: return null
        val kind = when (properties[NotionTaskProperties.KIND]?.select?.name) {
            NotionTaskProperties.KIND_MEMO -> AssistantTaskKind.MEMO
            else -> AssistantTaskKind.TASK
        }
        val dateDto = properties[NotionTaskProperties.DUE]?.date
        val due = NotionDateParser.parseDue(dateDto?.start, dateDto?.timeZone)
        val reminder = when (properties[NotionTaskProperties.REMINDER]?.select?.name) {
            NotionTaskProperties.REMINDER_HOME_ARRIVAL -> AssistantTaskReminder.HOME_ARRIVAL
            NotionTaskProperties.REMINDER_TIME -> AssistantTaskReminder.TIME
            NotionTaskProperties.REMINDER_NONE -> AssistantTaskReminder.NONE
            else -> if (due is AssistantTaskDue.DateTime) AssistantTaskReminder.TIME else AssistantTaskReminder.NONE
        }
        return AssistantTask(
            pageId = pageId,
            title = properties[NotionTaskProperties.TITLE]?.title.plainText(),
            kind = kind,
            done = properties[NotionTaskProperties.DONE]?.checkbox ?: false,
            due = due,
            reminder = reminder,
            content = properties[NotionTaskProperties.CONTENT]?.richText.plainText(),
            lastEditedTime = NotionDateParser.parseTimestamp(lastEditedTime) ?: 0L,
            url = url.orEmpty(),
        )
    }

    private fun List<RichTextDto>?.plainText(): String = this?.joinToString("") { it.plainText.orEmpty() }.orEmpty()

    @Serializable
    private data class QueryResponseDto(
        @SerialName("object") val objectType: String? = null,
        val results: List<PageDto>? = null,
        @SerialName("has_more") val hasMore: Boolean = false,
        @SerialName("next_cursor") val nextCursor: String? = null,
    )

    @Serializable
    private data class PageDto(
        @SerialName("object") val objectType: String? = null,
        val id: String? = null,
        @SerialName("last_edited_time") val lastEditedTime: String? = null,
        val url: String? = null,
        @SerialName("in_trash") val inTrash: Boolean = false,
        val archived: Boolean = false,
        val properties: Map<String, PropertyDto> = emptyMap(),
    )

    @Serializable
    private data class PropertyDto(
        val title: List<RichTextDto>? = null,
        @SerialName("rich_text") val richText: List<RichTextDto>? = null,
        val select: SelectDto? = null,
        val checkbox: Boolean? = null,
        val date: DateDto? = null,
    )

    @Serializable
    private data class RichTextDto(
        @SerialName("plain_text") val plainText: String? = null,
        val text: TextDto? = null,
    )

    @Serializable
    private data class TextDto(val content: String)

    @Serializable
    private data class ParentDto(
        val type: String = "data_source_id",
        @SerialName("data_source_id") val dataSourceId: String,
    )

    @Serializable
    private data class CreatePageRequestDto(val parent: ParentDto, val properties: Map<String, PropertyDto>)

    @Serializable
    private data class UpdatePageRequestDto(val properties: Map<String, PropertyDto>)

    @Serializable
    private data class SelectDto(val name: String? = null)

    @Serializable
    private data class DateDto(
        val start: String? = null,
        @SerialName("time_zone") val timeZone: String? = null,
    )

    @Serializable
    private data class QueryRequestDto(
        val filter: FilterDto,
        val sorts: List<SortDto>,
        @SerialName("page_size") val pageSize: Int = PAGE_SIZE,
        @SerialName("start_cursor") val startCursor: String? = null,
    )

    @Serializable
    private data class FilterDto(
        val property: String? = null,
        val checkbox: CheckboxFilterDto? = null,
        val and: List<FilterDto>? = null,
        val timestamp: String? = null,
        @SerialName("last_edited_time") val lastEditedTime: DateFilterDto? = null,
    )

    @Serializable
    private data class CheckboxFilterDto(val equals: Boolean)

    @Serializable
    private data class DateFilterDto(@SerialName("on_or_after") val onOrAfter: String)

    @Serializable
    private data class SortDto(
        val property: String? = null,
        val timestamp: String? = null,
        val direction: String,
    )
}
