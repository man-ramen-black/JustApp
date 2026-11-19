package com.black.feature.todo.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import com.black.app.testutil.BaseUiTest
import com.black.core.ui.theme.BlackTheme
import com.black.feature.todo.R
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.feature.todo.model.TodoSyncState
import com.black.feature.todo.model.TodoSyncStatus
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * [TodoScreen]이 연결 설정·구획·동기화 상태를 표시하는지 검증하는 Compose UI 테스트
 *
 * 구획과 연결 상태는 노션 데이터와 저장된 연결로 갈려 Screen으로는 한 분기만 실행되므로
 * [TodoContent]에 테스트가 고른 가짜 상태를 주입해 분기마다 고정된 기대값으로 검증.
 * 실제 노션 API는 부르지 않음
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoScreenTest : BaseUiTest() {

    /** [hostTodoContent]로 호스팅한 콘텐츠에 주입하는 화면 상태 */
    private val contentUiState = mutableStateOf(TodoUiState())

    /** [contentUiState]를 주입한 TodoContent를 HiltTestActivity에 setContent로 호스팅 */
    private fun hostTodoContent(
        onToggleDoneSection: () -> Unit = {},
        onToggleSettings: () -> Unit = {},
        onClickTask: (Todo) -> Unit = {},
        onToggleAddSection: () -> Unit = {},
        onNewDueDateChange: (LocalDate?) -> Unit = {},
        onNewReminderChange: (TodoReminder) -> Unit = {},
        onClickAdd: () -> Unit = {},
        onToggleDone: (Todo) -> Unit = {},
        onClickTodoNotifications: () -> Unit = {},
        onClickExactAlarmPermission: () -> Unit = {},
    ) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    TodoContent(
                        uiState = contentUiState.value,
                        onNavigateUp = {},
                        onToggleDoneSection = onToggleDoneSection,
                        onToggleSettings = onToggleSettings,
                        onTokenChange = {},
                        onDataSourceIdChange = {},
                        onClickSaveConnection = {},
                        onRefresh = {},
                        onClickTask = onClickTask,
                        onToggleAddSection = onToggleAddSection,
                        onNewTitleChange = {},
                        onNewDueDateChange = onNewDueDateChange,
                        onNewDueTimeChange = {},
                        onNewReminderChange = onNewReminderChange,
                        onClickAdd = onClickAdd,
                        onToggleDone = onToggleDone,
                        onClickTodoNotifications = onClickTodoNotifications,
                        onClickExactAlarmPermission = onClickExactAlarmPermission,
                    )
                }
            }
        }
    }

    private fun task(
        pageId: String,
        title: String,
        done: Boolean = false,
        due: TodoDue? = null,
        pendingSync: Boolean = false,
    ) = Todo(
        pageId = pageId,
        title = title,
        done = done,
        due = due,
        reminder = TodoReminder.NONE,
        content = "",
        lastEditedTime = Instant.EPOCH,
        url = "https://www.notion.so/$pageId",
        pendingSync = pendingSync,
    )

    /**
     * 기한 없는 항목 [LONG_LIST_SIZE]개를 담은 구획 목록
     *
     * 구획을 펼 때의 스크롤을 검증하려고 목록을 첫 행이 화면 밖으로 나갈 만큼 내릴 때 씀.
     */
    private fun longTaskSections() = listOf(
        TodoSection(TodoSectionType.NO_DUE, List(LONG_LIST_SIZE) { index -> task("long$index", "긴 목록 일 $index") }),
    )

    /** [longTaskSections]의 마지막 행이 보이도록 목록을 내리고 보이는지 확인 */
    private fun scrollToLastLongTask() {
        val lastRow = TodoTestTags.taskRow("long${LONG_LIST_SIZE - 1}")
        scrollListToTag(TodoTestTags.TASK_LIST, lastRow)
        assertTagDisplayed(lastRow)
    }

    /**
     * 연결을 읽기 전에는 본문이 비고, 읽은 뒤 미설정이면 연결 설정 구획과 빈 목록, 설정 아이콘이 함께 표시되는지 검증
     *
     * Given:
     * - connectionLoaded=false, configured=false 상태 주입
     * When:
     * - 화면 호스팅, 이어서 connectionLoaded=true, settingsExpanded=true 상태 주입
     * Then:
     * - 읽기 전 token_field, 빈 목록 문구, 설정 아이콘 없음
     * - 읽은 뒤 token_field, "Todo가 없습니다.", 설정 아이콘 표시
     */
    @Test
    fun test_01_notConfiguredShowsConnectionSectionAndEmptyList() {
        /** Given **/
        contentUiState.value = TodoUiState(connectionLoaded = false, configured = false)
        hostTodoContent()
        assertTagNotExists(TodoTestTags.TOKEN_FIELD)
        assertTagNotExists(TodoTestTags.EMPTY_MESSAGE)
        assertTagNotExists(TodoTestTags.SETTINGS_BUTTON)

        /** When **/
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = false, settingsExpanded = true)

        /** Then **/
        assertTagDisplayed(TodoTestTags.TOKEN_FIELD)
        // 연결 설정 구획과 같은 목록에 있어 화면 높이가 모자라면 스크롤해야 보임
        assertTagDisplayed(TodoTestTags.EMPTY_MESSAGE, scrollTo = true)
        assertTextDisplayed("Todo가 없습니다.")
        assertTagDisplayed(TodoTestTags.SETTINGS_BUTTON)
    }

    /**
     * 구획 머리가 지난 기한, 오늘, 기한 없음 순서로 위에서 아래로 표시되는지 검증
     *
     * Given:
     * - OVERDUE·TODAY·NO_DUE 구획 상태 주입
     * When:
     * - 화면 호스팅
     * Then:
     * - 머리 세 개의 세로 위치가 그 순서
     */
    @Test
    fun test_02_sectionsInOrder() {
        /** Given **/
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            configured = true,
            taskSections = listOf(
                TodoSection(TodoSectionType.OVERDUE, listOf(task("a", "지난 일", due = TodoDue.Date(LocalDate.of(2026, 11, 1))))),
                TodoSection(TodoSectionType.TODAY, listOf(task("b", "오늘 일", due = TodoDue.Date(LocalDate.of(2026, 11, 18))))),
                TodoSection(TodoSectionType.NO_DUE, listOf(task("c", "기한 없는 일"))),
            ),
        )

        /** When **/
        hostTodoContent()

        /** Then **/
        val overdueTop = tagTop(TodoTestTags.sectionHeader(TodoSectionType.OVERDUE))
        val todayTop = tagTop(TodoTestTags.sectionHeader(TodoSectionType.TODAY))
        val noDueTop = tagTop(TodoTestTags.sectionHeader(TodoSectionType.NO_DUE))
        assertTrue(overdueTop < todayTop)
        assertTrue(todayTop < noDueTop)
    }

    /**
     * 완료 구획이 기본으로 접혀 있고 머리를 누르면 펴기 콜백이 호출되며, 펼친 상태면 완료 행이 보이는지 검증
     *
     * Given:
     * - doneExpanded=false, DONE 구획 상태 주입
     * When:
     * - DONE 구획 머리 클릭, 이어서 doneExpanded=true 상태 주입
     * Then:
     * - 접힘 상태 설명과 행 미표시, onToggleDoneSection 1회 호출
     * - 펼친 뒤 펼침 상태 설명과 done1 행 표시
     */
    @Test
    fun test_03_doneSectionCollapsedByDefault() {
        /** Given **/
        var toggleCount = 0
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            configured = true,
            taskSections = listOf(
                TodoSection(TodoSectionType.DONE, listOf(task("done1", "끝난 일", done = true))),
            ),
        )
        hostTodoContent(onToggleDoneSection = { toggleCount++ })
        val doneHeader = TodoTestTags.sectionHeader(TodoSectionType.DONE)
        assertTagDisplayed(doneHeader)
        assertTagMatches(doneHeader, SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "접힘"))
        assertTagNotExists(TodoTestTags.taskRow("done1"))

        /** When **/
        clickTag(doneHeader)

        /** Then **/
        assertEquals(1, toggleCount)
        assertTagNotExists(TodoTestTags.taskRow("done1"))

        /** When **/
        contentUiState.value = contentUiState.value.copy(doneExpanded = true)

        /** Then **/
        assertTagMatches(doneHeader, SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "펼침"))
        assertTagDisplayed(TodoTestTags.taskRow("done1"))
    }

    /**
     * 행을 누르면 항목과 함께 클릭 콜백이 호출되는지 검증
     *
     * Given:
     * - 항목 하나가 있는 구획 상태 주입
     * When:
     * - 그 항목 행 클릭
     * Then:
     * - onClickTask가 그 항목으로 호출
     */
    @Test
    fun test_04_rowClickCallsOnClickTask() {
        /** Given **/
        var clickedTask: Todo? = null
        val target = task("row1", "눌러볼 일")
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            configured = true,
            taskSections = listOf(TodoSection(TodoSectionType.NO_DUE, listOf(target))),
        )
        hostTodoContent(onClickTask = { clickedTask = it })

        /** When **/
        clickTag(TodoTestTags.taskRow("row1"))

        /** Then **/
        assertEquals(target, clickedTask)
    }

    /**
     * 마지막 동기화가 인증 오류면 그 안내 문구가 표시되는지 검증
     *
     * Given:
     * - lastSync가 AUTH_ERROR인 상태 주입
     * When:
     * - 화면 호스팅
     * Then:
     * - sync_status와 "토큰이나 데이터베이스 연결을 확인해 주세요." 표시
     */
    @Test
    fun test_05_authErrorStatusShown() {
        /** Given **/
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            configured = true,
            lastSync = TodoSyncState(timeMillis = 1_700_000_000_000L, status = TodoSyncStatus.AUTH_ERROR),
        )

        /** When **/
        hostTodoContent()

        /** Then **/
        assertTagDisplayed(TodoTestTags.SYNC_STATUS)
        assertTextDisplayed("토큰이나 데이터베이스 연결을 확인해 주세요.")
    }

    /**
     * 설정됨이면 목록이 보이고 설정 아이콘 클릭이 콜백을 부르는지 검증
     *
     * Given:
     * - connectionLoaded=true, configured=true, 구획 없는 상태 주입
     * When:
     * - 화면 호스팅, settings_button 클릭
     * Then:
     * - 빈 목록 문구 표시, token_field 미표시, onToggleSettings 1회 호출
     */
    @Test
    fun test_06_configuredShowsListAndSettingsToggle() {
        /** Given **/
        var toggleCount = 0
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = true)
        hostTodoContent(onToggleSettings = { toggleCount++ })
        assertTagDisplayed(TodoTestTags.EMPTY_MESSAGE)
        assertTagNotExists(TodoTestTags.TOKEN_FIELD)

        /** When **/
        clickTag(TodoTestTags.SETTINGS_BUTTON)

        /** Then **/
        assertEquals(1, toggleCount)
    }

    /**
     * 설정됨에서 설정을 펼치면 연결 설정 구획이 목록보다 위에 있는지 검증
     *
     * Given:
     * - connectionLoaded=true, configured=true, settingsExpanded=true, 구획 없는 상태 주입
     * When:
     * - 화면 호스팅
     * Then:
     * - token_field의 세로 위치가 빈 목록 문구보다 위
     */
    @Test
    fun test_07_expandedSettingsAboveList() {
        /** Given **/
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = true, settingsExpanded = true)

        /** When **/
        hostTodoContent()

        /** Then **/
        val tokenTop = tagTop(TodoTestTags.TOKEN_FIELD)
        val listTop = tagTop(TodoTestTags.EMPTY_MESSAGE)
        assertTrue(tokenTop < listTop)
    }

    /**
     * 추가 아이콘이 설정됨일 때만 표시되고 누르면 구획 펴기 콜백이 호출되는지 검증
     *
     * Given:
     * - connectionLoaded=true, configured=false 상태 주입
     * When:
     * - 화면 호스팅, 이어서 configured=true 상태 주입 후 add_button 클릭
     * Then:
     * - 미설정이면 add_button 없음
     * - 설정됨이면 add_button 표시, onToggleAddSection 1회 호출
     */
    @Test
    fun test_08_addButtonOnlyWhenConfigured() {
        /** Given **/
        var toggleCount = 0
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = false)
        hostTodoContent(onToggleAddSection = { toggleCount++ })
        assertTagNotExists(TodoTestTags.ADD_BUTTON)

        /** When **/
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = true)
        assertTagDisplayed(TodoTestTags.ADD_BUTTON)
        clickTag(TodoTestTags.ADD_BUTTON)

        /** Then **/
        assertEquals(1, toggleCount)
    }

    /**
     * 추가 구획에서 날짜가 없으면 시각 버튼이 비활성이고 추가 버튼이 콜백을 부르는지 검증
     *
     * Given:
     * - configured=true, addExpanded=true 상태 주입
     * When:
     * - 화면 호스팅, add_confirm_button 클릭
     * Then:
     * - new_title_field 표시, new_due_time_button 비활성, onClickAdd 1회 호출
     */
    @Test
    fun test_09_addSectionTimeDisabledWithoutDate() {
        /** Given **/
        var addCount = 0
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = true, addExpanded = true)
        hostTodoContent(onClickAdd = { addCount++ })
        assertTagDisplayed(TodoTestTags.NEW_TITLE_FIELD)
        assertTagMatches(TodoTestTags.NEW_DUE_TIME_BUTTON, isNotEnabled())

        /** When **/
        clickTag(TodoTestTags.ADD_CONFIRM_BUTTON, scrollTo = true)

        /** Then **/
        assertEquals(1, addCount)
    }

    /**
     * 알림 칩을 누르면 그 알림 방식으로 콜백이 호출되는지 검증
     *
     * Given:
     * - configured=true, addExpanded=true 상태 주입
     * When:
     * - 집 도착 칩 클릭
     * Then:
     * - onNewReminderChange가 HOME_ARRIVAL로 호출
     */
    @Test
    fun test_10_reminderChipCallsChange() {
        /** Given **/
        var selected: TodoReminder? = null
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = true, addExpanded = true)
        hostTodoContent(onNewReminderChange = { selected = it })

        /** When **/
        clickTag(TodoTestTags.newReminder(TodoReminder.HOME_ARRIVAL), scrollTo = true)

        /** Then **/
        assertEquals(TodoReminder.HOME_ARRIVAL, selected)
    }

    /**
     * 고른 기한이 버튼 글자로 표시되고 기한 지우기가 null로 콜백하는지 검증
     *
     * Given:
     * - newDueDate 2026-11-20, newDueTime 09:30 상태 주입
     * When:
     * - 화면 호스팅, new_due_clear_button 클릭
     * Then:
     * - "11월 20일"·"09:30" 표시, new_due_time_button 활성, onNewDueDateChange(null) 1회 호출
     */
    @Test
    fun test_11_dueShownAndClear() {
        /** Given **/
        val dateChanges = mutableListOf<LocalDate?>()
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            configured = true,
            addExpanded = true,
            newDueDate = LocalDate.of(2026, 11, 20),
            newDueTime = LocalTime.of(9, 30),
        )
        hostTodoContent(onNewDueDateChange = { dateChanges.add(it) })
        assertTextDisplayed("11월 20일")
        assertTextDisplayed("09:30")
        assertTagMatches(TodoTestTags.NEW_DUE_TIME_BUTTON, isEnabled())

        /** When **/
        clickTag(TodoTestTags.NEW_DUE_CLEAR_BUTTON, scrollTo = true)

        /** Then **/
        assertEquals(listOf<LocalDate?>(null), dateChanges)
    }

    /**
     * 체크 상자를 누르면 그 항목으로 완료 토글 콜백이 호출되는지 검증
     *
     * Given:
     * - 항목 t1이 있는 구획 상태 주입
     * When:
     * - done_checkbox_t1 클릭
     * Then:
     * - onToggleDone이 t1로 호출, onClickTask는 호출되지 않음
     */
    @Test
    fun test_12_checkboxCallsToggleDone() {
        /** Given **/
        var toggled: Todo? = null
        val target = task("t1", "체크할 일")
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            configured = true,
            taskSections = listOf(TodoSection(TodoSectionType.NO_DUE, listOf(target))),
        )
        var rowClickCount = 0
        hostTodoContent(onToggleDone = { toggled = it }, onClickTask = { rowClickCount++ })

        /** When **/
        clickTag(TodoTestTags.doneCheckbox("t1"))

        /** Then **/
        assertEquals(target, toggled)
        assertEquals(0, rowClickCount)
    }

    /**
     * 반영 대기 항목에만 반영 대기 표시가 나오는지 검증
     *
     * Given:
     * - pendingSync인 p1과 아닌 p2 상태 주입
     * When:
     * - 화면 호스팅
     * Then:
     * - p1에 pending_label과 "반영 대기" 표시, p2에는 pending_label 없음
     */
    @Test
    fun test_13_pendingLabelOnlyOnPendingRows() {
        /** Given **/
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            configured = true,
            taskSections = listOf(
                TodoSection(
                    TodoSectionType.NO_DUE,
                    listOf(task("p1", "대기 일", pendingSync = true), task("p2", "반영된 일")),
                ),
            ),
        )

        /** When **/
        hostTodoContent()

        /** Then **/
        // 클릭 가능한 행이 자손 시맨틱을 병합하므로 병합되지 않은 트리에서 확인
        assertTagText(TodoTestTags.pendingLabel("p1"), "반영 대기", useUnmergedTree = true)
        assertTagNotExists(TodoTestTags.pendingLabel("p2"), useUnmergedTree = true)
    }

    /**
     * 연결되지 않은 상태에서는 항목 행에 완료 체크 상자가 없는지 검증
     *
     * Given:
     * - configured=false, 항목 t1이 있는 구획 상태 주입
     * When:
     * - 화면 호스팅
     * Then:
     * - t1 행은 있고 done_checkbox_t1은 없음
     */
    @Test
    fun test_14_checkboxHiddenWhenNotConfigured() {
        /** Given **/
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            configured = false,
            taskSections = listOf(TodoSection(TodoSectionType.NO_DUE, listOf(task("t1", "연결 전 일")))),
        )

        /** When **/
        hostTodoContent()

        /** Then **/
        assertTagExists(TodoTestTags.taskRow("t1"))
        assertTagNotExists(TodoTestTags.doneCheckbox("t1"), useUnmergedTree = true)
    }

    /**
     * 설정 구획을 펴면 Todo 알림 행이 보이고, 꺼져 있을 때 누르면 콜백이 한 번 불리는지 검증
     *
     * Given:
     * - settingsExpanded=true, todoNotificationsEnabled=false 상태 주입
     * When:
     * - 화면 호스팅 후 Todo 알림 행 클릭
     * Then:
     * - "Todo 알림" 행에 "허용 필요" 표시, 콜백 1회
     */
    @Test
    fun test_15_todoNotificationsRowClickableWhenDisabled() {
        /** Given **/
        var clickCount = 0
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            settingsExpanded = true,
            todoNotificationsEnabled = false,
        )
        hostTodoContent(onClickTodoNotifications = { clickCount++ })
        assertTagDisplayed(TodoTestTags.TODO_NOTIFICATIONS_ROW, scrollTo = true)
        assertTagTextContains(
            TodoTestTags.TODO_NOTIFICATIONS_ROW,
            context.getString(R.string.todo_notifications),
            context.getString(R.string.permission_status_required),
        )

        /** When **/
        clickTag(TodoTestTags.TODO_NOTIFICATIONS_ROW, scrollTo = true)

        /** Then **/
        assertEquals(1, clickCount)
    }

    /**
     * Todo 알림이 켜져 있으면 행을 눌러도 콜백이 불리지 않고, 설정 구획을 접으면 행이 없는지 검증
     *
     * Given:
     * - settingsExpanded=true, todoNotificationsEnabled=true 상태 주입
     * When:
     * - 행 클릭, 이어서 settingsExpanded=false 주입
     * Then:
     * - "Todo 알림" 행에 "허용됨" 표시, 콜백 0회, 접은 뒤 행 없음
     */
    @Test
    fun test_16_todoNotificationsRowNotClickableWhenEnabled() {
        /** Given **/
        var clickCount = 0
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            settingsExpanded = true,
            todoNotificationsEnabled = true,
        )
        hostTodoContent(onClickTodoNotifications = { clickCount++ })

        /** When **/
        clickTag(TodoTestTags.TODO_NOTIFICATIONS_ROW, scrollTo = true)

        /** Then **/
        assertTagTextContains(
            TodoTestTags.TODO_NOTIFICATIONS_ROW,
            context.getString(R.string.todo_notifications),
            context.getString(R.string.permission_status_allowed),
        )
        assertEquals(0, clickCount)
        contentUiState.value = contentUiState.value.copy(settingsExpanded = false)
        assertTagNotExists(TodoTestTags.TODO_NOTIFICATIONS_ROW)
    }

    /**
     * 설정 구획을 펴면 Todo 알림 행 아래에 정확한 알람 행이 보이고, 미허용일 때 누르면 콜백이 한 번 불리는지 검증
     *
     * Given:
     * - settingsExpanded=true, exactAlarmPermissionGranted=false 상태 주입
     * When:
     * - 화면 호스팅 후 정확한 알람 행 클릭
     * Then:
     * - "정확한 알람" 행이 Todo 알림 행 아래에 "허용 필요"로 표시, 콜백 1회
     */
    @Test
    fun test_17_exactAlarmPermissionRowClickableWhenDenied() {
        /** Given **/
        var clickCount = 0
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            settingsExpanded = true,
            exactAlarmPermissionGranted = false,
        )
        hostTodoContent(onClickExactAlarmPermission = { clickCount++ })
        assertTagDisplayed(TodoTestTags.EXACT_ALARM_PERMISSION_ROW, scrollTo = true)
        assertTagTextContains(
            TodoTestTags.EXACT_ALARM_PERMISSION_ROW,
            context.getString(R.string.todo_permission_exact_alarm),
            context.getString(R.string.permission_status_required),
        )
        val todoNotificationsRowTop = tagTop(TodoTestTags.TODO_NOTIFICATIONS_ROW)
        val exactAlarmRowTop = tagTop(TodoTestTags.EXACT_ALARM_PERMISSION_ROW)
        assertTrue(todoNotificationsRowTop < exactAlarmRowTop)

        /** When **/
        clickTag(TodoTestTags.EXACT_ALARM_PERMISSION_ROW, scrollTo = true)

        /** Then **/
        assertEquals(1, clickCount)
    }

    /**
     * 정확한 알람이 허용돼 있으면 행을 눌러도 콜백이 불리지 않고, 설정 구획을 접으면 행이 없는지 검증
     *
     * Given:
     * - settingsExpanded=true, exactAlarmPermissionGranted=true 상태 주입
     * When:
     * - 행 클릭, 이어서 settingsExpanded=false 주입
     * Then:
     * - "정확한 알람" 행에 "허용됨" 표시, 콜백 0회, 접은 뒤 행 없음
     */
    @Test
    fun test_18_exactAlarmPermissionRowNotClickableWhenGranted() {
        /** Given **/
        var clickCount = 0
        contentUiState.value = TodoUiState(
            connectionLoaded = true,
            settingsExpanded = true,
            exactAlarmPermissionGranted = true,
        )
        hostTodoContent(onClickExactAlarmPermission = { clickCount++ })

        /** When **/
        clickTag(TodoTestTags.EXACT_ALARM_PERMISSION_ROW, scrollTo = true)

        /** Then **/
        assertTagTextContains(
            TodoTestTags.EXACT_ALARM_PERMISSION_ROW,
            context.getString(R.string.todo_permission_exact_alarm),
            context.getString(R.string.permission_status_allowed),
        )
        assertEquals(0, clickCount)
        contentUiState.value = contentUiState.value.copy(settingsExpanded = false)
        assertTagNotExists(TodoTestTags.EXACT_ALARM_PERMISSION_ROW)
    }

    /**
     * 목록을 내린 상태에서 연결 설정 구획을 펴면 구획으로 스크롤해 구획 첫 요소가 보이는지 검증
     *
     * Given:
     * - configured=true, settingsExpanded=false, 항목 40개 구획 상태 주입
     * - 목록을 마지막 행이 보이도록 내림
     * When:
     * - settingsExpanded=true 주입
     * Then:
     * - "노션 연결" 구획 머리와 token_field 표시
     */
    @Test
    fun test_19_expandingSettingsScrollsToSection() {
        /** Given **/
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = true, taskSections = longTaskSections())
        hostTodoContent()
        scrollToLastLongTask()

        /** When **/
        contentUiState.value = contentUiState.value.copy(settingsExpanded = true)

        /** Then **/
        assertTextDisplayed(context.getString(R.string.todo_section_connection))
        assertTagDisplayed(TodoTestTags.TOKEN_FIELD)
    }

    /**
     * 목록을 내린 상태에서 Todo 추가 구획을 펴면 구획으로 스크롤해 구획 첫 요소가 보이는지 검증
     *
     * Given:
     * - configured=true, settingsExpanded=false, addExpanded=false, 항목 40개 구획 상태 주입
     * - 목록을 마지막 행이 보이도록 내림
     * When:
     * - addExpanded=true 주입
     * Then:
     * - "Todo 추가" 구획 머리와 new_title_field 표시
     */
    @Test
    fun test_20_expandingAddSectionScrollsToSection() {
        /** Given **/
        contentUiState.value = TodoUiState(connectionLoaded = true, configured = true, taskSections = longTaskSections())
        hostTodoContent()
        scrollToLastLongTask()

        /** When **/
        contentUiState.value = contentUiState.value.copy(addExpanded = true)

        /** Then **/
        assertTextDisplayed(context.getString(R.string.todo_add))
        assertTagDisplayed(TodoTestTags.NEW_TITLE_FIELD)
    }

    private companion object {
        /** [longTaskSections]의 항목 수이며, 큰 화면에서도 첫 행이 화면 밖으로 나갈 만큼 둠 */
        const val LONG_LIST_SIZE = 40
    }
}
