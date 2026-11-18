package com.black.app.ui.main.assistanttask

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
import com.black.app.R
import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.app.assistanttask.sync.AssistantTaskSyncState
import com.black.app.assistanttask.sync.AssistantTaskSyncStatus
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * [AssistantTaskScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유
 */
internal object AssistantTaskTestTags {
    const val SETTINGS_BUTTON = "assistant_task_settings_button"
    const val TOKEN_FIELD = "assistant_task_token_field"
    const val DATA_SOURCE_ID_FIELD = "assistant_task_data_source_id_field"
    const val SAVE_CONNECTION_BUTTON = "assistant_task_save_connection_button"
    const val SYNC_STATUS = "assistant_task_sync_status"
    const val EMPTY_MESSAGE = "assistant_task_empty_message"
    const val ADD_BUTTON = "assistant_task_add_button"
    const val NEW_TITLE_FIELD = "assistant_task_new_title_field"
    const val NEW_DUE_DATE_BUTTON = "assistant_task_new_due_date_button"
    const val NEW_DUE_TIME_BUTTON = "assistant_task_new_due_time_button"
    const val NEW_DUE_CLEAR_BUTTON = "assistant_task_new_due_clear_button"
    const val ADD_CONFIRM_BUTTON = "assistant_task_add_confirm_button"
    const val TODO_NOTIFICATIONS_ROW = "assistant_task_todo_notifications_row"
    const val EXACT_ALARM_PERMISSION_ROW = "assistant_task_exact_alarm_permission_row"
    const val TASK_LIST = "assistant_task_list"

    fun newReminder(reminder: AssistantTaskReminder): String = "assistant_task_new_reminder_${reminder.name.lowercase()}"

    fun doneCheckbox(pageId: String): String = "assistant_task_done_checkbox_$pageId"

    fun pendingLabel(pageId: String): String = "assistant_task_pending_label_$pageId"

    fun sectionHeader(type: AssistantTaskSectionType): String = "assistant_task_section_header_${type.name.lowercase()}"

    fun taskRow(pageId: String): String = "assistant_task_row_$pageId"
}

/** 시각이 있는 기한과 마지막 동기화 시각의 표시 형식 */
private const val DATE_TIME_PATTERN = "M월 d일 HH:mm"

/** 비서 할 일 화면 */
@Composable
fun AssistantTaskScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssistantTaskViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 키보드가 추가 구획 입력 칸을 가리지 않도록 이 화면에서만 창 크기 조절 방식으로 바꾸고, 떠날 때 이전 값으로 복원
    DisposableEffect(context) {
        val window = context.activity()?.window
        val previousMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            if (previousMode != null) window.setSoftInputMode(previousMode)
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
            is AssistantTaskViewModel.EventOpenUrl -> openUrl(context, event.url)
            is AssistantTaskViewModel.EventOpenNotificationSettings -> PermissionHelper.showAppNotificationSettings(context)
            is AssistantTaskViewModel.EventOpenExactAlarmSettings -> PermissionHelper.showExactAlarmSetting(context)
            is AssistantTaskViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    AssistantTaskContent(
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
        onNewDueMinutesChange = viewModel::updateNewDueMinutes,
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
        Toast.makeText(context, R.string.assistant_task_open_failed, Toast.LENGTH_SHORT).show()
    }
}

/**
 * 비서 할 일 화면 콘텐츠
 *
 * 상태를 갖지 않고 받은 [uiState]와 콜백만으로 그리는 렌더링 전용.
 * 구획 순서·완료 구획 접힘·미설정 화면이 노션 데이터와 저장된 연결로 갈려 Screen으로는 한 분기만 실행되므로,
 * 계측 테스트가 상태를 주입해 분기마다 검증하도록 `internal`로 둠.
 * 당겨서 새로고침하는 목록 전체가 [PullToRefreshBox] 안에 있어야 하므로 스크롤하는 `ToolScreenScaffold` 대신 `ToolTopBar`만 쓰고 목록이 스크롤을 맡음.
 * 연결 설정·Todo 추가 구획도 목록 앞쪽 항목으로 둬, 가로 모드·작은 화면·큰 글꼴·키보드로 높이가 모자라도 아래쪽 행(정확한 알람 행, 추가 버튼)까지 스크롤해 누를 수 있음.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssistantTaskContent(
    uiState: AssistantTaskUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onToggleDoneSection: () -> Unit,
    onToggleSettings: () -> Unit,
    onTokenChange: (String) -> Unit,
    onDataSourceIdChange: (String) -> Unit,
    onClickSaveConnection: () -> Unit,
    onRefresh: () -> Unit,
    onClickTask: (AssistantTask) -> Unit,
    onToggleAddSection: () -> Unit,
    onNewTitleChange: (String) -> Unit,
    onNewDueDateChange: (String?) -> Unit,
    onNewDueMinutesChange: (Int?) -> Unit,
    onNewReminderChange: (AssistantTaskReminder) -> Unit,
    onClickAdd: () -> Unit,
    onToggleDone: (AssistantTask) -> Unit,
    onClickTodoNotifications: () -> Unit,
    onClickExactAlarmPermission: () -> Unit,
) {
    // 표시 형식 객체는 한 번만 만들어 행마다 재사용
    val dateTimeFormat = remember { SimpleDateFormat(DATE_TIME_PATTERN, Locale.KOREA) }
    val listState = rememberLazyListState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.ScreenBackground)
            .statusBarsPadding(),
    ) {
        ToolTopBar(
            title = stringResource(R.string.fragment_name_assistant_task),
            onNavigateUp = onNavigateUp,
        ) {
            if (uiState.connectionLoaded && uiState.configured) {
                TopBarIconButton(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.assistant_task_add),
                    onClick = onToggleAddSection,
                    modifier = Modifier.testTag(AssistantTaskTestTags.ADD_BUTTON),
                )
            }
            if (uiState.connectionLoaded) {
                TopBarIconButton(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.assistant_task_settings),
                    onClick = onToggleSettings,
                    modifier = Modifier.testTag(AssistantTaskTestTags.SETTINGS_BUTTON),
                )
            }
        }
        // 연결을 읽기 전에는 settingsExpanded가 기본값 false라 미설정인데 설정이 접혀 보이지 않도록 읽은 뒤에만 본문을 그림
        if (!uiState.connectionLoaded) return@Column
        val addSectionShown = uiState.configured && uiState.addExpanded
        // 구획은 키가 있는 목록 항목보다 앞에 끼므로, 펼칠 때 스크롤하지 않으면 목록을 내려 본 상태에서는 화면 위쪽 밖에 생김
        LaunchedEffect(uiState.settingsExpanded) {
            if (uiState.settingsExpanded) listState.animateScrollToItem(0)
        }
        LaunchedEffect(addSectionShown) {
            if (addSectionShown) listState.animateScrollToItem(if (uiState.settingsExpanded) 1 else 0)
        }
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(AssistantTaskTestTags.TASK_LIST),
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
                            onNewDueMinutesChange = onNewDueMinutesChange,
                            onNewReminderChange = onNewReminderChange,
                            onClickAdd = onClickAdd,
                        )
                    }
                }
                uiState.lastSync?.let { lastSync ->
                    item {
                        SyncStatus(lastSync = lastSync, dateTimeFormat = dateTimeFormat)
                    }
                }
                for (section in uiState.taskSections) {
                    val isDoneSection = section.type == AssistantTaskSectionType.DONE
                    item(key = "header_${section.type}") {
                        SectionHeaderRow(
                            type = section.type,
                            expanded = uiState.doneExpanded.takeIf { isDoneSection },
                            onClick = onToggleDoneSection.takeIf { isDoneSection },
                        )
                    }
                    // 완료 구획은 접혀 있으면 머리만 보임
                    val expanded = section.type != AssistantTaskSectionType.DONE || uiState.doneExpanded
                    if (expanded) {
                        items(section.tasks, key = { it.pageId }) { task ->
                            TaskRow(
                                task = task,
                                dateTimeFormat = dateTimeFormat,
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
                            text = stringResource(R.string.assistant_task_empty),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AssistantTaskTestTags.EMPTY_MESSAGE),
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
    uiState: AssistantTaskUiState,
    onTokenChange: (String) -> Unit,
    onDataSourceIdChange: (String) -> Unit,
    onClickSaveConnection: () -> Unit,
    onClickTodoNotifications: () -> Unit,
    onClickExactAlarmPermission: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.assistant_task_section_connection))
        OutlinedTextField(
            value = uiState.tokenInput,
            onValueChange = onTokenChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AssistantTaskTestTags.TOKEN_FIELD),
            textStyle = AppTextFieldDefaults.InputTextStyle,
            label = { Text(text = stringResource(R.string.assistant_task_token_label), style = AppTypography.Caption) },
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
                .testTag(AssistantTaskTestTags.DATA_SOURCE_ID_FIELD),
            textStyle = AppTextFieldDefaults.InputTextStyle,
            label = { Text(text = stringResource(R.string.assistant_task_data_source_id_label), style = AppTypography.Caption) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            colors = AppTextFieldDefaults.colors(),
        )
        ActionButton(
            text = stringResource(R.string.assistant_task_save_connection),
            onClick = onClickSaveConnection,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AssistantTaskTestTags.SAVE_CONNECTION_BUTTON),
        )
        SectionHeader(title = stringResource(R.string.assistant_task_section_alerts))
        PermissionStatusRow(
            label = stringResource(R.string.assistant_task_todo_notifications),
            granted = uiState.todoNotificationsEnabled,
            onClick = onClickTodoNotifications,
            modifier = Modifier.testTag(AssistantTaskTestTags.TODO_NOTIFICATIONS_ROW),
        )
        PermissionStatusRow(
            label = stringResource(R.string.assistant_task_permission_exact_alarm),
            granted = uiState.exactAlarmPermissionGranted,
            onClick = onClickExactAlarmPermission,
            modifier = Modifier.testTag(AssistantTaskTestTags.EXACT_ALARM_PERMISSION_ROW),
        )
    }
}

/** 마지막 동기화 시각과, 성공이 아니면 결과 문구 */
@Composable
private fun SyncStatus(lastSync: AssistantTaskSyncState, dateTimeFormat: SimpleDateFormat) {
    val time = dateTimeFormat.format(Date(lastSync.timeMillis))
    Column(modifier = Modifier.testTag(AssistantTaskTestTags.SYNC_STATUS)) {
        Text(
            text = stringResource(R.string.assistant_task_last_sync, time),
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
private fun syncFailureMessageResId(status: AssistantTaskSyncStatus): Int? = when (status) {
    AssistantTaskSyncStatus.SUCCESS, AssistantTaskSyncStatus.NOT_CONFIGURED -> null
    AssistantTaskSyncStatus.AUTH_ERROR -> R.string.assistant_task_sync_auth_error
    AssistantTaskSyncStatus.NETWORK_ERROR -> R.string.assistant_task_sync_network_error
    AssistantTaskSyncStatus.SERVER_ERROR -> R.string.assistant_task_sync_server_error
    AssistantTaskSyncStatus.REQUEST_ERROR -> R.string.assistant_task_sync_request_error
    AssistantTaskSyncStatus.STORAGE_ERROR -> R.string.assistant_task_sync_storage_error
}

/**
 * 구획 머리
 *
 * 완료 구획만 [expanded]와 [onClick]을 받아 펼침·접힘 아이콘을 보이고, 눌러 펴고 접음.
 * 스크린 리더가 버튼과 현재 상태(펼침·접힘)를 읽도록 역할과 상태 설명을 붙임.
 */
@Composable
private fun SectionHeaderRow(
    type: AssistantTaskSectionType,
    expanded: Boolean?,
    onClick: (() -> Unit)?,
) {
    val toggleModifier = if (expanded != null && onClick != null) {
        val state = stringResource(if (expanded) R.string.assistant_task_expanded else R.string.assistant_task_collapsed)
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = state }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AssistantTaskTestTags.sectionHeader(type))
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
private fun sectionTitleResId(type: AssistantTaskSectionType): Int = when (type) {
    AssistantTaskSectionType.OVERDUE -> R.string.assistant_task_section_overdue
    AssistantTaskSectionType.TODAY -> R.string.assistant_task_section_today
    AssistantTaskSectionType.UPCOMING -> R.string.assistant_task_section_upcoming
    AssistantTaskSectionType.NO_DUE -> R.string.assistant_task_section_no_due
    AssistantTaskSectionType.DONE -> R.string.assistant_task_section_done
}

/**
 * 항목 행
 *
 * 앞에 완료 체크 상자, 이름 아래 줄에 기한과 알림 방식, 노션에 반영되지 않았으면 반영 대기 표시.
 * [onToggleDone]이 null이면 완료 체크 상자를 그리지 않음.
 */
@Composable
private fun TaskRow(
    task: AssistantTask,
    dateTimeFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onToggleDone: (() -> Unit)?,
) {
    val doneDescription = stringResource(R.string.assistant_task_done)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AssistantTaskTestTags.taskRow(task.pageId))
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
                    .testTag(AssistantTaskTestTags.doneCheckbox(task.pageId)),
                colors = CheckboxDefaults.colors(
                    checkedColor = AppColors.Icon,
                    uncheckedColor = AppColors.UncheckedControl,
                    checkmarkColor = AppColors.ScreenBackground,
                ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = task.title.ifBlank { stringResource(R.string.assistant_task_untitled) },
                style = AppTypography.BrightBody,
            )
            val dueText = task.due?.let { formatDue(it, dateTimeFormat) }
            val reminderResId = when (task.reminder) {
                AssistantTaskReminder.HOME_ARRIVAL -> R.string.assistant_task_reminder_home_arrival
                AssistantTaskReminder.TIME -> R.string.assistant_task_reminder_time
                AssistantTaskReminder.NONE -> null
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
                            text = stringResource(R.string.assistant_task_pending),
                            modifier = Modifier.testTag(AssistantTaskTestTags.pendingLabel(task.pageId)),
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
 * 날짜만 있으면 `M월 d일`, 시각까지 있으면 기기 기본 시간대 기준 `M월 d일 HH:mm`.
 * 날짜 형식이 틀리면 원래 문자열을 그대로 표시.
 */
private fun formatDue(due: AssistantTaskDue, dateTimeFormat: SimpleDateFormat): String = when (due) {
    is AssistantTaskDue.DateOnly -> formatDate(due.date)
    is AssistantTaskDue.DateTime -> dateTimeFormat.format(Date(due.epochMillis))
}

/** `yyyy-MM-dd`를 `M월 d일`로 바꾸고, 형식이 틀리면 [date] 그대로 반환 */
private fun formatDate(date: String): String {
    val parts = date.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull()
    val day = parts.getOrNull(2)?.toIntOrNull()
    return if (month != null && day != null) "${month}월 ${day}일" else date
}

/** 자정부터의 분을 `HH:mm`으로 변환 */
private fun formatMinutes(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

/**
 * Todo 추가 구획
 *
 * 이름 칸, 기한(날짜·시각 선택과 지우기), 알림 방식 칩, 추가 버튼.
 * 키보드는 [AssistantTaskScreen]의 `DisposableEffect`가 이 화면에서만 창 크기 조절 방식(`SOFT_INPUT_ADJUST_RESIZE`)으로 바꿔 입력 칸을 가리지 않음.
 */
@Composable
private fun AddSection(
    uiState: AssistantTaskUiState,
    onNewTitleChange: (String) -> Unit,
    onNewDueDateChange: (String?) -> Unit,
    onNewDueMinutesChange: (Int?) -> Unit,
    onNewReminderChange: (AssistantTaskReminder) -> Unit,
    onClickAdd: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.assistant_task_add))
        OutlinedTextField(
            value = uiState.newTitleInput,
            onValueChange = onNewTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AssistantTaskTestTags.NEW_TITLE_FIELD),
            textStyle = AppTextFieldDefaults.InputTextStyle,
            label = { Text(text = stringResource(R.string.assistant_task_title_label), style = AppTypography.Caption) },
            singleLine = true,
            colors = AppTextFieldDefaults.colors(),
        )
        // 기한 줄: 시각은 날짜가 있을 때만 고를 수 있음
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.assistant_task_due_label), style = AppTypography.Caption)
            val dateText = uiState.newDueDate?.let(::formatDate) ?: stringResource(R.string.assistant_task_due_date)
            ActionButton(
                text = dateText,
                onClick = { showDatePicker(context, uiState.newDueDate, onNewDueDateChange) },
                modifier = Modifier.testTag(AssistantTaskTestTags.NEW_DUE_DATE_BUTTON),
            )
            val timeText = uiState.newDueMinutes?.let(::formatMinutes) ?: stringResource(R.string.assistant_task_due_time)
            ActionButton(
                text = timeText,
                onClick = { showTimePicker(context, uiState.newDueMinutes, onNewDueMinutesChange) },
                modifier = Modifier.testTag(AssistantTaskTestTags.NEW_DUE_TIME_BUTTON),
                enabled = uiState.newDueDate != null,
            )
            if (uiState.newDueDate != null) {
                ActionButton(
                    text = stringResource(R.string.assistant_task_due_clear),
                    onClick = { onNewDueDateChange(null) },
                    modifier = Modifier.testTag(AssistantTaskTestTags.NEW_DUE_CLEAR_BUTTON),
                )
            }
        }
        // 알림 줄
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.assistant_task_reminder_label), style = AppTypography.Caption)
            for (reminder in AssistantTaskReminder.entries) {
                FilterChip(
                    selected = uiState.newReminder == reminder,
                    onClick = { onNewReminderChange(reminder) },
                    label = { Text(text = stringResource(reminderLabelResId(reminder)), style = AppTypography.Caption) },
                    modifier = Modifier.testTag(AssistantTaskTestTags.newReminder(reminder)),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AppColors.ContainerBackground,
                        selectedContainerColor = AppColors.Arrow,
                    ),
                )
            }
        }
        ActionButton(
            text = stringResource(R.string.assistant_task_add_confirm),
            onClick = onClickAdd,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AssistantTaskTestTags.ADD_CONFIRM_BUTTON),
        )
    }
}

@StringRes
private fun reminderLabelResId(reminder: AssistantTaskReminder): Int = when (reminder) {
    AssistantTaskReminder.NONE -> R.string.assistant_task_reminder_none
    AssistantTaskReminder.HOME_ARRIVAL -> R.string.assistant_task_reminder_home_arrival
    AssistantTaskReminder.TIME -> R.string.assistant_task_reminder_time
}

/**
 * 날짜 선택 다이얼로그 표시
 *
 * 초기값은 `yyyy-MM-dd` 형식의 [current]이고, 없으면 오늘.
 */
private fun showDatePicker(context: Context, current: String?, onPicked: (String?) -> Unit) {
    val parts = current?.split("-")?.mapNotNull { it.toIntOrNull() }
    val calendar = Calendar.getInstance()
    val year = parts?.getOrNull(0) ?: calendar.get(Calendar.YEAR)
    val month = parts?.getOrNull(1)?.minus(1) ?: calendar.get(Calendar.MONTH)
    val day = parts?.getOrNull(2) ?: calendar.get(Calendar.DAY_OF_MONTH)
    DatePickerDialog(
        context,
        { _, pickedYear, pickedMonth, pickedDay ->
            onPicked(String.format(Locale.US, "%04d-%02d-%02d", pickedYear, pickedMonth + 1, pickedDay))
        },
        year,
        month,
        day,
    ).show()
}

/**
 * 24시간 시각 선택 다이얼로그 표시
 *
 * 초기값은 [currentMinutes]이고, 없으면 09:00.
 */
private fun showTimePicker(context: Context, currentMinutes: Int?, onPicked: (Int?) -> Unit) {
    val minutes = currentMinutes ?: DEFAULT_PICKER_MINUTES
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        minutes / 60,
        minutes % 60,
        true,
    ).show()
}

/** 시각 선택 다이얼로그의 기본 시각(09:00) */
private const val DEFAULT_PICKER_MINUTES = 9 * 60
