package com.black.feature.todo.data.network

import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 노션 `Todo` 데이터베이스의 요청·응답 DTO와 [Todo] 사이의 변환
 *
 * 앱은 속성을 이름으로 찾으므로 노션에서 속성 이름을 바꾸면 `PROPERTY_` 상수도 함께 바꿈.
 * 노션 데이터베이스에는 메모도 있지만 앱은 Todo만 다루므로 종류가 [KIND_MEMO]인 페이지는 [toTodo]가 null로 거름.
 */
object NotionTodoMapper {

    // 노션 `Todo` 데이터베이스의 속성 이름
    const val PROPERTY_TITLE = "이름"
    const val PROPERTY_KIND = "종류"
    const val PROPERTY_DONE = "완료"
    const val PROPERTY_DUE = "기한"
    const val PROPERTY_REMINDER = "알림"
    const val PROPERTY_CONTENT = "내용"

    /**
     * 앱이 추가하는 페이지의 종류 선택 값
     *
     * ChatGPT, 노션 앱, 디스코드 비서가 새 항목에 쓰는 `Todo`와 맞춤(예전 값 `할 일`로 등록된 항목도 메모가 아니면 Todo로 읽음).
     */
    const val KIND_TODO = "Todo"

    /** 앱이 다루지 않는 메모의 종류 선택 값 */
    const val KIND_MEMO = "메모"

    // 알림 속성의 선택 값
    const val REMINDER_HOME_ARRIVAL = "집 도착"
    const val REMINDER_TIME = "시각"
    const val REMINDER_NONE = "없음"

    private const val OBJECT_LIST = "list"
    private const val OBJECT_PAGE = "page"
    private const val TIMESTAMP_LAST_EDITED_TIME = "last_edited_time"

    /** `yyyy-MM-dd` 문자열 길이 */
    private const val DATE_LENGTH = 10

    /** 완료 조회의 수정 시각 조건에 싣는 UTC 시각 형식 */
    private val TIMESTAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).withZone(ZoneOffset.UTC)

    /** 시각 기한에 싣는 오프셋 없는 지역 시각 형식 */
    private val LOCAL_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    /** 저장본이 epoch 밀리초로 담을 수 있는 시각 범위 */
    private val STORABLE_INSTANTS: ClosedRange<Instant> = Instant.ofEpochMilli(Long.MIN_VALUE)..Instant.ofEpochMilli(Long.MAX_VALUE)

    /** 미완료 Todo를 기한 오름차순으로 조회하는 요청 */
    fun openTodosQuery(cursor: String?): NotionQueryRequest = NotionQueryRequest(
        filter = NotionFilter(property = PROPERTY_DONE, checkbox = NotionCheckboxFilter(equals = false)),
        sorts = listOf(NotionSort(property = PROPERTY_DUE, direction = "ascending")),
        startCursor = cursor,
    )

    /** [since] 이후 수정된 완료 Todo를 최근 수정 순으로 조회하는 요청 */
    fun recentlyDoneQuery(since: Instant, cursor: String?): NotionQueryRequest = NotionQueryRequest(
        filter = NotionFilter(
            and = listOf(
                NotionFilter(property = PROPERTY_DONE, checkbox = NotionCheckboxFilter(equals = true)),
                NotionFilter(
                    timestamp = TIMESTAMP_LAST_EDITED_TIME,
                    lastEditedTime = NotionDateFilter(onOrAfter = TIMESTAMP_FORMAT.format(since)),
                ),
            ),
        ),
        sorts = listOf(NotionSort(timestamp = TIMESTAMP_LAST_EDITED_TIME, direction = "descending")),
        startCursor = cursor,
    )

    /**
     * [dataSourceId]에 [todo]를 추가하는 요청
     *
     * 종류는 늘 [KIND_TODO]로 보냄. 시각 기한은 [zone] 기준 오프셋 없는 지역 시각과 그 시간대 ID로 보내고, 기한이 없으면 기한 속성을 보내지 않음.
     */
    fun createPageRequest(todo: Todo, dataSourceId: String, zone: ZoneId): NotionCreatePageRequest = NotionCreatePageRequest(
        parent = NotionParent(dataSourceId = dataSourceId),
        properties = buildMap {
            put(PROPERTY_TITLE, NotionPropertyValue(title = listOf(NotionTextValue(NotionText(todo.title)))))
            put(PROPERTY_KIND, NotionPropertyValue(select = NotionSelectValue(KIND_TODO)))
            put(PROPERTY_DONE, NotionPropertyValue(checkbox = todo.done))
            put(PROPERTY_REMINDER, NotionPropertyValue(select = NotionSelectValue(todo.reminder.notionName())))
            todo.due?.let { due -> put(PROPERTY_DUE, NotionPropertyValue(date = due.toDateValue(zone))) }
        },
    )

    /** 페이지의 완료 체크 상자를 [done]으로 바꾸는 요청 */
    fun updateDoneRequest(done: Boolean): NotionUpdatePageRequest =
        NotionUpdatePageRequest(mapOf(PROPERTY_DONE to NotionPropertyValue(checkbox = done)))

    /**
     * 조회 응답 [response]의 Todo 목록
     *
     * 조회 결과가 아니면(object가 list가 아니거나 results 없음) null.
     * 목록에서 빼는 페이지와 [defaultZone]의 쓰임은 [toTodo] 참고.
     */
    fun toTodos(response: NotionQueryResponse, defaultZone: ZoneId): List<Todo>? {
        // 노션 오류 본문이나 빈 객체는 조회 결과가 아님
        if (response.objectType != OBJECT_LIST) {
            return null
        }
        val results = response.results ?: return null
        return results.mapNotNull { page -> toTodo(page, defaultZone) }
    }

    /**
     * 페이지 추가·수정 응답 [page]가 저장본에 반영할 수 있는 페이지인지 여부
     *
     * object가 page이고 id가 있으며 휴지통·보관 페이지가 아니어야 함.
     * 종류는 보지 않으므로 메모 페이지도 true이며, 메모는 [toTodo]가 null로 거름.
     */
    fun isUsablePage(page: NotionPage): Boolean =
        page.objectType == OBJECT_PAGE && page.id != null && !page.inTrash && !page.archived

    /**
     * [page]를 Todo로 바꿈
     *
     * id가 없거나 휴지통·보관 페이지이거나 종류가 메모면 null이며, 종류가 비거나 모르는 값이면 Todo로 봄.
     * 오프셋 없는 시각 기한은 `time_zone` 기준이고, `time_zone`이 없거나 모르는 시간대면 [defaultZone] 기준.
     * 알림이 없거나 모르는 값이면 시각 기한은 [TodoReminder.TIME], 그 밖은 [TodoReminder.NONE].
     * 해석할 수 없는 기한은 기한 없음, 해석할 수 없는 수정 시각은 [Instant.EPOCH].
     */
    fun toTodo(page: NotionPage, defaultZone: ZoneId): Todo? {
        // 쓸 수 없는 페이지와 메모 제외
        val pageId = page.id ?: return null
        if (page.inTrash || page.archived) {
            return null
        }
        val properties = page.properties
        if (properties[PROPERTY_KIND]?.select?.name == KIND_MEMO) {
            return null
        }

        // 속성 변환
        val date = properties[PROPERTY_DUE]?.date
        val due = parseDue(date?.start, date?.timeZone, defaultZone)
        return Todo(
            pageId = pageId,
            title = properties[PROPERTY_TITLE]?.title.plainText(),
            done = properties[PROPERTY_DONE]?.checkbox ?: false,
            due = due,
            reminder = reminderOf(properties[PROPERTY_REMINDER]?.select?.name, due),
            content = properties[PROPERTY_CONTENT]?.richText.plainText(),
            // desugaring의 Instant.parse(ISO_INSTANT)는 `Z`만 받으므로 `+09:00` 같은 오프셋도 받는 OffsetDateTime으로 해석
            lastEditedTime = page.lastEditedTime
                ?.let { text -> parseOrNull { OffsetDateTime.parse(text).toInstant() }?.storableOrNull() }
                ?: Instant.EPOCH,
            url = page.url.orEmpty(),
        )
    }

    /**
     * 기한 `start` 값 해석
     *
     * 날짜만 있으면 실제 있는 날짜일 때만 쓰고, 해석할 수 없으면 null.
     */
    private fun parseDue(start: String?, timeZoneId: String?, defaultZone: ZoneId): TodoDue? {
        if (start.isNullOrEmpty()) {
            return null
        }
        // 날짜만 있는 기한
        if (start.length == DATE_LENGTH) {
            return parseOrNull { LocalDate.parse(start) }?.let { TodoDue.Date(it) }
        }
        // 오프셋이 있는 시각
        val offsetInstant = parseOrNull { OffsetDateTime.parse(start).toInstant() }
        if (offsetInstant != null) {
            return offsetInstant.storableOrNull()?.let { TodoDue.DateTime(it) }
        }
        // 오프셋이 없는 지역 시각
        val zone = timeZoneId?.let { id -> parseOrNull { ZoneId.of(id) } } ?: defaultZone
        return parseOrNull { LocalDateTime.parse(start).atZone(zone).toInstant() }?.storableOrNull()?.let { TodoDue.DateTime(it) }
    }

    /** [parse]의 결과이며 형식이 틀렸거나 없는 날짜·시간대면 null */
    private inline fun <T> parseOrNull(parse: () -> T): T? = try {
        parse()
    } catch (exception: DateTimeException) {
        null
    }

    /**
     * 저장본이 epoch 밀리초로 담을 수 있는 시각이면 그대로, 아니면 null
     *
     * 범위를 벗어난 연도는 밀리초로 바꿀 때 [ArithmeticException]이 나 저장본에 쓰지 못하므로 해석할 수 없는 값으로 봄.
     */
    private fun Instant.storableOrNull(): Instant? = takeIf { it in STORABLE_INSTANTS }

    private fun reminderOf(name: String?, due: TodoDue?): TodoReminder = when (name) {
        REMINDER_HOME_ARRIVAL -> TodoReminder.HOME_ARRIVAL
        REMINDER_TIME -> TodoReminder.TIME
        REMINDER_NONE -> TodoReminder.NONE
        else -> if (due is TodoDue.DateTime) TodoReminder.TIME else TodoReminder.NONE
    }

    private fun TodoReminder.notionName(): String = when (this) {
        TodoReminder.HOME_ARRIVAL -> REMINDER_HOME_ARRIVAL
        TodoReminder.TIME -> REMINDER_TIME
        TodoReminder.NONE -> REMINDER_NONE
    }

    private fun TodoDue.toDateValue(zone: ZoneId): NotionDateValue = when (this) {
        is TodoDue.Date -> NotionDateValue(start = DateTimeFormatter.ISO_LOCAL_DATE.format(date))
        is TodoDue.DateTime -> NotionDateValue(
            start = LOCAL_DATE_TIME_FORMAT.format(instant.atZone(zone)),
            timeZone = zone.id,
        )
    }

    private fun List<NotionRichText>?.plainText(): String = this?.joinToString("") { it.plainText.orEmpty() }.orEmpty()
}
