package com.black.feature.todo.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.core.ui.component.ActionButton
import com.black.core.ui.component.AppTextFieldDefaults
import com.black.core.ui.component.PermissionStatusRow
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.ToolTopBar
import com.black.core.ui.component.TopBarIconButton
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography
import com.black.core.util.Extensions.activity
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents
import com.black.feature.todo.R
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.feature.todo.model.TodoSyncState
import com.black.feature.todo.model.TodoSyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [TodoScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유
 */
object TodoTestTags {
    const val SETTINGS_BUTTON = "todo_settings_button"
    const val TOKEN_FIELD = "todo_token_field"
    const val DATA_SOURCE_ID_FIELD = "todo_data_source_id_field"
    const val SAVE_CONNECTION_BUTTON = "todo_save_connection_button"
    const val SYNC_STATUS = "todo_sync_status"
    const val EMPTY_MESSAGE = "todo_empty_message"
    const val ADD_BUTTON = "todo_add_button"
    const val NEW_TITLE_FIELD = "todo_new_title_field"
    const val NEW_DUE_DATE_BUTTON = "todo_new_due_date_button"
    const val NEW_DUE_TIME_BUTTON = "todo_new_due_time_button"
    const val NEW_DUE_CLEAR_BUTTON = "todo_new_due_clear_button"
    const val ADD_CONFIRM_BUTTON = "todo_add_confirm_button"
    const val TODO_NOTIFICATIONS_ROW = "todo_notifications_row"
    const val EXACT_ALARM_PERMISSION_ROW = "todo_exact_alarm_permission_row"
    const val TASK_LIST = "todo_list"

    fun newReminder(reminder: TodoReminder): String = "todo_new_reminder_${reminder.name.lowercase()}"

    fun doneCheckbox(pageId: String): String = "todo_done_checkbox_$pageId"

    fun pendingLabel(pageId: String): String = "todo_pending_label_$pageId"

    fun sectionHeader(type: TodoSectionType): String = "todo_section_header_${type.name.lowercase()}"

    fun taskRow(pageId: String): String = "todo_row_$pageId"
}

/** 시각이 있는 기한과 마지막 동기화 시각의 표시 형식 */
private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)

/** 날짜만 있는 기한과 새 Todo 기한 날짜의 표시 형식 */
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA)

/** 새 Todo 기한 시각의 표시 형식 */
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** Todo 화면 */
@Composable
fun TodoScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 키보드가 추가 구획 입력 칸을 가리지 않도록 이 화면에서만 창 크기 조절 방식으로 바꾸고, 떠날 때 이전 값으로 복원
    DisposableEffect(context) {
        val window = context.activity()?.window
        val previousMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            if (previousMode != null) {
                window.setSoftInputMode(previousMode)
            }
        }
    }

    // 첫 진입과 다시 보일 때 오늘 날짜(자정 넘김)와 알림·정확한 알람 허용 여부를 갱신하고 노션과 동기화
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
        viewModel.refreshPermissions()
        viewModel.refresh()
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is TodoViewModel.EventOpenUrl -> openUrl(context, event.url)
            is TodoViewModel.EventOpenNotificationSettings -> PermissionHelper.showAppNotificationSettings(context)
            is TodoViewModel.EventOpenExactAlarmSettings -> PermissionHelper.showExactAlarmSetting(context)
            is TodoViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    TodoContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onToggleDoneSection = viewModel::toggleDoneSection,
        onToggleSettings = viewModel::toggleSettings,
        onTokenChange = viewModel::updateTokenInput,
        onDataSourceIdChange = viewModel::updateDataSourceIdInput,
        onClickSaveConnection = viewModel::saveConnection,
        onRefresh = viewModel::refresh,
        onClickTask = viewModel::openTask,
        onToggleAddSection = viewModel::toggleAddSection,
        onNewTitleChange = viewModel::updateNewTitle,
        onNewDueDateChange = viewModel::updateNewDueDate,
        onNewDueTimeChange = viewModel::updateNewDueTime,
        onNewReminderChange = viewModel::updateNewReminder,
        onClickAdd = viewModel::addTask,
        onToggleDone = viewModel::toggleDone,
        onClickTodoNotifications = viewModel::openNotificationSettings,
        onClickExactAlarmPermission = viewModel::openExactAlarmSettings,
    )
}

/** 노션 페이지를 외부 앱으로 열고, 열 앱이 없으면 토스트 표시 */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (notFound: ActivityNotFoundException) {
        Toast.makeText(context, R.string.todo_open_failed, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Todo 화면 콘텐츠
 *
 * 상태를 갖지 않고 받은 [uiState]와 콜백만으로 그리는 렌더링 전용.
 * 구획 순서·완료 구획 접힘·미설정 화면이 노션 데이터와 저장된 연결로 갈려 Screen으로는 한 분기만 실행되므로,
 * 앱 모듈의 계측 테스트가 상태를 주입해 분기마다 검증하도록 공개함.
 * 당겨서 새로고침하는 목록 전체가 [PullToRefreshBox] 안에 있어야 하므로 스크롤하는 `ToolScreenScaffold` 대신 `ToolTopBar`만 쓰고 목록이 스크롤을 맡음.
 * 연결 설정·Todo 추가 구획도 목록 앞쪽 항목으로 둬, 가로 모드·작은 화면·큰 글꼴·키보드로 높이가 모자라도 아래쪽 행(정확한 알람 행, 추가 버튼)까지 스크롤해 누를 수 있음.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoContent(
    uiState: TodoUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onToggleDoneSection: () -> Unit,
    onToggleSettings: () -> Unit,
    onTokenChange: (String) -> Unit,
    onDataSourceIdChange: (String) -> Unit,
    onClickSaveConnection: () -> Unit,
    onRefresh: () -> Unit,
    onClickTask: (Todo) -> Unit,
    onToggleAddSection: () -> Unit,
    onNewTitleChange: (String) -> Unit,
    onNewDueDateChange: (LocalDate?) -> Unit,
    onNewDueTimeChange: (LocalTime?) -> Unit,
    onNewReminderChange: (TodoReminder) -> Unit,
    onClickAdd: () -> Unit,
    onToggleDone: (Todo) -> Unit,
    onClickTodoNotifications: () -> Unit,
    onClickExactAlarmPermission: () -> Unit,
) {
    // 표시 시간대는 한 번만 읽어 행마다 재사용
    val zone = remember { ZoneId.systemDefault() }
    val listState = rememberLazyListState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.ScreenBackground)
            .statusBarsPadding(),
    ) {
        ToolTopBar(
            title = stringResource(R.string.fragment_name_todo),
            onNavigateUp = onNavigateUp,
        ) {
            if (uiState.connectionLoaded && uiState.configured) {
                TopBarIconButton(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.todo_add),
                    onClick = onToggleAddSection,
                    modifier = Modifier.testTag(TodoTestTags.ADD_BUTTON),
                )
            }
            if (uiState.connectionLoaded) {
                TopBarIconButton(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.todo_settings),
                    onClick = onToggleSettings,
                    modifier = Modifier.testTag(TodoTestTags.SETTINGS_BUTTON),
                )
            }
        }
        // 연결을 읽기 전에는 settingsExpanded가 기본값 false라 미설정인데 설정이 접혀 보이지 않도록 읽은 뒤에만 본문을 그림
        if (!uiState.connectionLoaded) {
            return@Column
        }
        val addSectionShown = uiState.configured && uiState.addExpanded
        // 구획은 키가 있는 목록 항목보다 앞에 끼므로, 펼칠 때 스크롤하지 않으면 목록을 내려 본 상태에서는 화면 위쪽 밖에 생김
        LaunchedEffect(uiState.settingsExpanded) {
            if (uiState.settingsExpanded) {
                listState.animateScrollToItem(0)
            }
        }
        LaunchedEffect(addSectionShown) {
            if (addSectionShown) {
                listState.animateScrollToItem(if (uiState.settingsExpanded) 1 else 0)
            }
        }
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TodoTestTags.TASK_LIST),
                state = listState,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                // 목록이 비면 문구를 구획 아래 남은 높이의 가운데에 둠
                verticalArrangement = if (uiState.taskSections.isEmpty()) EmptyMessageArrangement else Arrangement.Top,
            ) {
                // 연결 설정은 미설정이면 처음부터 펼쳐져 있고, 목록은 연결 여부와 상관없이 그 아래에 둠
                if (uiState.settingsExpanded) {
                    item(key = SETTINGS_SECTION_KEY) {
                        SettingsSection(
                            uiState = uiState,
                            onTokenChange = onTokenChange,
                            onDataSourceIdChange = onDataSourceIdChange,
                            onClickSaveConnection = onClickSaveConnection,
                            onClickTodoNotifications = onClickTodoNotifications,
                            onClickExactAlarmPermission = onClickExactAlarmPermission,
                        )
                    }
                }
                // Todo 추가 구획은 연결된 뒤 펼쳤을 때만 연결 설정 구획 아래, 목록 위에 둠
                if (addSectionShown) {
                    item(key = ADD_SECTION_KEY) {
                        AddSection(
                            uiState = uiState,
                            onNewTitleChange = onNewTitleChange,
                            onNewDueDateChange = onNewDueDateChange,
                            onNewDueTimeChange = onNewDueTimeChange,
                            onNewReminderChange = onNewReminderChange,
                            onClickAdd = onClickAdd,
                        )
                    }
                }
                uiState.lastSync?.let { lastSync ->
                    item {
                        SyncStatus(lastSync = lastSync, zone = zone)
                    }
                }
                for (section in uiState.taskSections) {
                    val isDoneSection = section.type == TodoSectionType.DONE
                    item(key = "header_${section.type}") {
                        SectionHeaderRow(
                            type = section.type,
                            expanded = uiState.doneExpanded.takeIf { isDoneSection },
                            onClick = onToggleDoneSection.takeIf { isDoneSection },
                        )
                    }
                    // 완료 구획은 접혀 있으면 머리만 보임
                    val expanded = section.type != TodoSectionType.DONE || uiState.doneExpanded
                    if (expanded) {
                        items(section.tasks, key = { it.pageId }) { task ->
                            TaskRow(
                                task = task,
                                zone = zone,
                                onClick = { onClickTask(task) },
                                // 완료 체크는 연결된 뒤에만 씀(연결 전에 바꾼 항목은 연결하며 저장본을 비울 때 사라짐)
                                onToggleDone = { onToggleDone(task) }.takeIf { uiState.configured },
                            )
                        }
                    }
                }
                // 빈 목록 문구를 목록 영역 가운데에 두어 Todo가 표시될 자리를 드러냄
                if (uiState.taskSections.isEmpty()) {
                    item(key = EMPTY_MESSAGE_KEY) {
                        Text(
                            text = stringResource(R.string.todo_empty),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TodoTestTags.EMPTY_MESSAGE),
                            style = AppTypography.DimBody,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 연결 설정 구획 목록 항목 키 */
private const val SETTINGS_SECTION_KEY = "settings_section"

/** Todo 추가 구획 목록 항목 키 */
private const val ADD_SECTION_KEY = "add_section"

/** 빈 목록 문구 목록 항목 키 */
private const val EMPTY_MESSAGE_KEY = "empty_message"

/**
 * 빈 목록 문구를 마지막 항목으로 둔 목록의 세로 배치
 *
 * 앞 항목(연결 설정·Todo 추가 구획, 동기화 상태)은 위에서부터 쌓고, 마지막 항목은 남은 높이의 가운데에 둠.
 * 항목이 목록 높이를 넘으면 [LazyColumn]이 배치를 쓰지 않고 차례로 쌓으므로, 문구는 구획 뒤에 붙어 스크롤로 보임.
 */
private object EmptyMessageArrangement : Arrangement.Vertical {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        var position = 0
        sizes.forEachIndexed { index, size ->
            val spare = if (index == sizes.lastIndex) (totalSize - position - size).coerceAtLeast(0) else 0
            outPositions[index] = position + spare / 2
            position += size
        }
    }
}

/**
 * 연결 설정 구획
 *
 * 구획 머리 아래에 토큰·데이터 소스 ID 입력 칸과 연결 확인 버튼, 그 아래 알림 구획에 Todo 알림 행과 정확한 알람 행.
 */
@Composable
private fun SettingsSection(
    uiState: TodoUiState,
    onTokenChange: (String) -> Unit,
    onDataSourceIdChange: (String) -> Unit,
    onClickSaveConnection: () -> Unit,
    onClickTodoNotifications: () -> Unit,
    onClickExactAlarmPermission: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.todo_section_connection))
        OutlinedTextField(
            value = uiState.tokenInput,
            onValueChange = onTokenChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TodoTestTags.TOKEN_FIELD),
            textStyle = AppTextFieldDefaults.InputTextStyle,
            label = { Text(text = stringResource(R.string.todo_token_label), style = AppTypography.Caption) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = AppTextFieldDefaults.colors(),
        )
        OutlinedTextField(
            value = uiState.dataSourceIdInput,
            onValueChange = onDataSourceIdChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TodoTestTags.DATA_SOURCE_ID_FIELD),
            textStyle = AppTextFieldDefaults.InputTextStyle,
            label = { Text(text = stringResource(R.string.todo_data_source_id_label), style = AppTypography.Caption) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            colors = AppTextFieldDefaults.colors(),
        )
        ActionButton(
            text = stringResource(R.string.todo_save_connection),
            onClick = onClickSaveConnection,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TodoTestTags.SAVE_CONNECTION_BUTTON),
        )
        SectionHeader(title = stringResource(R.string.todo_section_alerts))
        PermissionStatusRow(
            label = stringResource(R.string.todo_notifications),
            granted = uiState.todoNotificationsEnabled,
            onClick = onClickTodoNotifications,
            modifier = Modifier.testTag(TodoTestTags.TODO_NOTIFICATIONS_ROW),
        )
        PermissionStatusRow(
            label = stringResource(R.string.todo_permission_exact_alarm),
            granted = uiState.exactAlarmPermissionGranted,
            onClick = onClickExactAlarmPermission,
            modifier = Modifier.testTag(TodoTestTags.EXACT_ALARM_PERMISSION_ROW),
        )
    }
}

/** 마지막 동기화 시각과, 성공이 아니면 결과 문구 */
@Composable
private fun SyncStatus(lastSync: TodoSyncState, zone: ZoneId) {
    val time = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(lastSync.timeMillis).atZone(zone))
    Column(modifier = Modifier.testTag(TodoTestTags.SYNC_STATUS)) {
        Text(
            text = stringResource(R.string.todo_last_sync, time),
            style = AppTypography.Caption,
        )
        syncFailureMessageResId(lastSync.status)?.let { messageResId ->
            Text(
                text = stringResource(messageResId),
                modifier = Modifier.padding(top = 4.dp),
                style = AppTypography.ErrorCaption,
            )
        }
    }
}

/**
 * 동기화 결과에 대응하는 문구 리소스
 *
 * 성공과 미설정은 문구 없이 null.
 */
@StringRes
private fun syncFailureMessageResId(status: TodoSyncStatus): Int? = when (status) {
    TodoSyncStatus.SUCCESS, TodoSyncStatus.NOT_CONFIGURED -> null
    TodoSyncStatus.AUTH_ERROR -> R.string.todo_sync_auth_error
    TodoSyncStatus.NETWORK_ERROR -> R.string.todo_sync_network_error
    TodoSyncStatus.SERVER_ERROR -> R.string.todo_sync_server_error
    TodoSyncStatus.REQUEST_ERROR -> R.string.todo_sync_request_error
    TodoSyncStatus.STORAGE_ERROR -> R.string.todo_sync_storage_error
}

/**
 * 구획 머리
 *
 * 완료 구획만 [expanded]와 [onClick]을 받아 펼침·접힘 아이콘을 보이고, 눌러 펴고 접음.
 * 스크린 리더가 버튼과 현재 상태(펼침·접힘)를 읽도록 역할과 상태 설명을 붙임.
 */
@Composable
private fun SectionHeaderRow(
    type: TodoSectionType,
    expanded: Boolean?,
    onClick: (() -> Unit)?,
) {
    val toggleModifier = if (expanded != null && onClick != null) {
        val state = stringResource(if (expanded) R.string.todo_expanded else R.string.todo_collapsed)
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = state }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TodoTestTags.sectionHeader(type))
            .then(toggleModifier),
    ) {
        SectionHeader(
            title = stringResource(sectionTitleResId(type)),
            trailing = expanded?.let { isExpanded ->
                {
                    // 상태는 머리의 stateDescription이 읽으므로 아이콘 설명은 비움
                    Icon(
                        painter = painterResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = AppColors.Caption,
                    )
                }
            },
        )
    }
}

@StringRes
private fun sectionTitleResId(type: TodoSectionType): Int = when (type) {
    TodoSectionType.OVERDUE -> R.string.todo_section_overdue
    TodoSectionType.TODAY -> R.string.todo_section_today
    TodoSectionType.UPCOMING -> R.string.todo_section_upcoming
    TodoSectionType.NO_DUE -> R.string.todo_section_no_due
    TodoSectionType.DONE -> R.string.todo_section_done
}

/**
 * 항목 행
 *
 * 앞에 완료 체크 상자, 이름 아래 줄에 기한과 알림 방식, 노션에 반영되지 않았으면 반영 대기 표시.
 * [onToggleDone]이 null이면 완료 체크 상자를 그리지 않음.
 */
@Composable
private fun TaskRow(
    task: Todo,
    zone: ZoneId,
    onClick: () -> Unit,
    onToggleDone: (() -> Unit)?,
) {
    val doneDescription = stringResource(R.string.todo_done)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TodoTestTags.taskRow(task.pageId))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleDone != null) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier
                    .padding(end = 4.dp)
                    .semantics { contentDescription = doneDescription }
                    .testTag(TodoTestTags.doneCheckbox(task.pageId)),
                colors = CheckboxDefaults.colors(
                    checkedColor = AppColors.Icon,
                    uncheckedColor = AppColors.UncheckedControl,
                    checkmarkColor = AppColors.ScreenBackground,
                ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = task.title.ifBlank { stringResource(R.string.todo_untitled) },
                style = AppTypography.BrightBody,
            )
            val dueText = task.due?.let { formatDue(it, zone) }
            val reminderResId = when (task.reminder) {
                TodoReminder.HOME_ARRIVAL -> R.string.todo_reminder_home_arrival
                TodoReminder.TIME -> R.string.todo_reminder_time
                TodoReminder.NONE -> null
            }
            if (dueText != null || reminderResId != null || task.pendingSync) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dueText != null) {
                        Text(text = dueText, style = AppTypography.Caption)
                    }
                    if (reminderResId != null) {
                        Text(text = stringResource(reminderResId), style = AppTypography.Caption)
                    }
                    if (task.pendingSync) {
                        Text(
                            text = stringResource(R.string.todo_pending),
                            modifier = Modifier.testTag(TodoTestTags.pendingLabel(task.pageId)),
                            style = AppTypography.Caption,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 기한 표시 문자열
 *
 * 날짜만 있으면 `M월 d일`, 시각까지 있으면 [zone] 기준 `M월 d일 HH:mm`.
 */
private fun formatDue(due: TodoDue, zone: ZoneId): String = when (due) {
    is TodoDue.Date -> DATE_FORMATTER.format(due.date)
    is TodoDue.DateTime -> DATE_TIME_FORMATTER.format(due.instant.atZone(zone))
}

/**
 * Todo 추가 구획
 *
 * 이름 칸, 기한(날짜·시각 선택과 지우기), 알림 방식 칩, 추가 버튼.
 * 키보드는 [TodoScreen]의 `DisposableEffect`가 이 화면에서만 창 크기 조절 방식(`SOFT_INPUT_ADJUST_RESIZE`)으로 바꿔 입력 칸을 가리지 않음.
 */
@Composable
private fun AddSection(
    uiState: TodoUiState,
    onNewTitleChange: (String) -> Unit,
    onNewDueDateChange: (LocalDate?) -> Unit,
    onNewDueTimeChange: (LocalTime?) -> Unit,
    onNewReminderChange: (TodoReminder) -> Unit,
    onClickAdd: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.todo_add))
        OutlinedTextField(
            value = uiState.newTitleInput,
            onValueChange = onNewTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TodoTestTags.NEW_TITLE_FIELD),
            textStyle = AppTextFieldDefaults.InputTextStyle,
            label = { Text(text = stringResource(R.string.todo_title_label), style = AppTypography.Caption) },
            singleLine = true,
            colors = AppTextFieldDefaults.colors(),
        )
        // 기한 줄: 시각은 날짜가 있을 때만 고를 수 있음
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.todo_due_label), style = AppTypography.Caption)
            val dateText = uiState.newDueDate?.let(DATE_FORMATTER::format) ?: stringResource(R.string.todo_due_date)
            ActionButton(
                text = dateText,
                onClick = { showDatePicker(context, uiState.newDueDate, onNewDueDateChange) },
                modifier = Modifier.testTag(TodoTestTags.NEW_DUE_DATE_BUTTON),
            )
            val timeText = uiState.newDueTime?.let(TIME_FORMATTER::format) ?: stringResource(R.string.todo_due_time)
            ActionButton(
                text = timeText,
                onClick = { showTimePicker(context, uiState.newDueTime, onNewDueTimeChange) },
                modifier = Modifier.testTag(TodoTestTags.NEW_DUE_TIME_BUTTON),
                enabled = uiState.newDueDate != null,
            )
            if (uiState.newDueDate != null) {
                ActionButton(
                    text = stringResource(R.string.todo_due_clear),
                    onClick = { onNewDueDateChange(null) },
                    modifier = Modifier.testTag(TodoTestTags.NEW_DUE_CLEAR_BUTTON),
                )
            }
        }
        // 알림 줄
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.todo_reminder_label), style = AppTypography.Caption)
            for (reminder in TodoReminder.entries) {
                FilterChip(
                    selected = uiState.newReminder == reminder,
                    onClick = { onNewReminderChange(reminder) },
                    label = { Text(text = stringResource(reminderLabelResId(reminder)), style = AppTypography.Caption) },
                    modifier = Modifier.testTag(TodoTestTags.newReminder(reminder)),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AppColors.ContainerBackground,
                        selectedContainerColor = AppColors.Arrow,
                    ),
                )
            }
        }
        ActionButton(
            text = stringResource(R.string.todo_add_confirm),
            onClick = onClickAdd,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TodoTestTags.ADD_CONFIRM_BUTTON),
        )
    }
}

@StringRes
private fun reminderLabelResId(reminder: TodoReminder): Int = when (reminder) {
    TodoReminder.NONE -> R.string.todo_reminder_none
    TodoReminder.HOME_ARRIVAL -> R.string.todo_reminder_home_arrival
    TodoReminder.TIME -> R.string.todo_reminder_time
}

/**
 * 날짜 선택 다이얼로그 표시
 *
 * 초기값은 [current]이고, 없으면 기기 시간대 기준 오늘.
 * [DatePickerDialog]의 월은 0부터 세므로 [LocalDate]의 월과 1 차이.
 */
private fun showDatePicker(context: Context, current: LocalDate?, onPicked: (LocalDate?) -> Unit) {
    val initial = current ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, pickedYear, pickedMonth, pickedDay -> onPicked(LocalDate.of(pickedYear, pickedMonth + 1, pickedDay)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

/**
 * 24시간 시각 선택 다이얼로그 표시
 *
 * 초기값은 [current]이고, 없으면 09:00.
 */
private fun showTimePicker(context: Context, current: LocalTime?, onPicked: (LocalTime?) -> Unit) {
    val initial = current ?: DEFAULT_PICKER_TIME
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        true,
    ).show()
}

/** 시각 선택 다이얼로그의 기본 시각(09:00) */
private val DEFAULT_PICKER_TIME: LocalTime = LocalTime.of(9, 0)
