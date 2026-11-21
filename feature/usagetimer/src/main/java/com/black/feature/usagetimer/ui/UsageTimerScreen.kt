package com.black.feature.usagetimer.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.core.ui.component.ActionButton
import com.black.core.ui.component.AppTextFieldDefaults
import com.black.core.ui.component.CollapsibleSectionHeader
import com.black.core.ui.component.OverlayPermissionDialog
import com.black.core.ui.component.OverlayPositionInput
import com.black.core.ui.component.OverlayPositionSetting
import com.black.core.ui.component.PermissionStatusRow
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.component.containerBox
import com.black.core.ui.selectapp.AppIconImage
import com.black.core.ui.selectapp.SelectAppDialogFragment
import com.black.core.ui.selectapp.SelectedAppUiItem
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography
import com.black.core.util.DataUtil
import com.black.core.util.Extensions.fragmentActivity
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents
import com.black.feature.usagetimer.R
import com.black.feature.usagetimer.overlay.UsageTimerGlobal
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * [UsageTimerScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유.
 * 계측 테스트가 다른 모듈(앱 androidTest)에 있어 public
 */
object UsageTimerTestTags {
    const val TITLE = "usage_timer_title"
    const val SHOW_BUTTON = "show_button"
    const val PAUSE_DURATION_INPUT = "pause_duration_input"
    const val PAUSE_DURATION_ERROR = "pause_duration_error"
    const val PAUSE_BUTTON = "pause_button"
    const val CANCEL_BUTTON = "cancel_button"
    const val ACCESSIBILITY_STATUS = "usage_timer_accessibility_status"
    const val OVERLAY_STATUS = "usage_timer_overlay_status"
    const val USAGE_ACCESS_STATUS = "usage_timer_usage_access_status"
    const val POSITION_HEADER = "position_header"
    const val SELECT_APP_BUTTON = "select_app_button"
    const val SELECTED_APPS_EMPTY = "selected_apps_empty"
    const val SELECTED_APPS_LIST = "selected_apps_list"
    const val PAUSE_INFO = "pause_info"
    const val USAGE_PERMISSION_HINT = "usage_permission_hint"
    const val USAGE_SESSIONS_FAILED = "usage_sessions_failed"
    const val USAGE_SESSIONS_TITLE = "usage_sessions_title"
    const val USAGE_SESSIONS_EMPTY = "usage_sessions_empty"
    const val USAGE_SESSIONS_MORE = "usage_sessions_more"

    /** 선택 앱 한 건의 최근 사용 시간 줄 */
    fun selectedAppUsage(packageName: String): String = "selected_app_usage_$packageName"

    /** 대상 앱 타일 */
    fun appTile(packageName: String): String = "app_tile_$packageName"

    /** 구간 목록의 날짜 머리 */
    fun usageDayHeader(day: UsageDay): String = "usage_day_header_${day.name.lowercase()}"

    /**
     * 구간 목록의 [index]번째 줄
     *
     * [index]는 0부터 세고, 화면에 보이는 순서(최신 구간부터).
     */
    fun usageSessionRow(index: Int): String = "usage_session_row_$index"
}

/** UsageTimer 화면 */
@Composable
fun UsageTimerScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageTimerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 설정 앱에서 권한을 바꾸고 돌아온 경우의 상태 갱신
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    // SelectAppDialog 결과 관찰 (FragmentActivity의 supportFragmentManager 기반)
    LaunchedEffect(Unit) {
        val activity = context.fragmentActivity() ?: return@LaunchedEffect
        SelectAppDialogFragment.observeSelectedApp(
            activity.supportFragmentManager,
            lifecycleOwner,
        ) { viewModel.onAppsSelected(it) }
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is UsageTimerViewModel.EventShowTimerView -> {
                UsageTimerGlobal.showPreview(context)
            }
            is UsageTimerViewModel.EventOpenOverlaySettings -> {
                PermissionHelper.showDrawOverlaysSetting(context)
            }
            is UsageTimerViewModel.EventShowToast -> {
                val message = context.getString(event.messageResId, *event.formatArgs.toTypedArray())
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            is UsageTimerViewModel.EventDetachTimerView -> {
                UsageTimerGlobal.detachAllViews()
            }
            is UsageTimerViewModel.EventOpenAccessibilitySettings -> {
                PermissionHelper.openAccessibilityServiceDetailSetting(context)
            }
            is UsageTimerViewModel.EventOpenUsageAccessSettings -> {
                PermissionHelper.showUsageAccessSettings(context)
            }
            is UsageTimerViewModel.EventOpenSelectApp -> {
                val activity = context.fragmentActivity() ?: return@CollectEvents
                SelectAppDialogFragment.newInstance(event.checkedPackageNames)
                    .show(activity.supportFragmentManager, SelectAppDialogFragment.TAG)
            }
            else -> Unit
        }
    }

    UsageTimerContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onPauseDurationChange = viewModel::updatePauseDuration,
        onClickShow = viewModel::onClickShow,
        onClickPause = viewModel::onClickPause,
        onClickCancelPause = viewModel::onClickCancelPause,
        onClickAccessibility = viewModel::onClickAccessibility,
        onClickOverlaySettings = viewModel::openOverlaySettings,
        onClickUsageAccess = viewModel::openUsageAccessSettings,
        onClickSelectApp = viewModel::onClickSelectApp,
        onClickAppTile = viewModel::showUsageSessions,
        onPositionInputChange = viewModel::updatePosition,
        onFinishTimer = viewModel::onFinishTimer,
    )

    if (uiState.showOverlayPermissionDialog) {
        OverlayPermissionDialog(
            message = stringResource(R.string.usage_timer_overlay_permission_guide),
            onConfirm = viewModel::openOverlaySettings,
            onDismiss = viewModel::dismissOverlayPermissionDialog,
        )
    }
}

/**
 * UsageTimer 화면 콘텐츠
 *
 * 상태 무관 렌더링 전용.
 * 권한 표시가 기기 상태로 갈려 앱 androidTest가 uiState를 주입해 검증하므로 public
 */
@Composable
fun UsageTimerContent(
    uiState: UsageTimerUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onPauseDurationChange: (String) -> Unit,
    onClickShow: () -> Unit,
    onClickPause: () -> Unit,
    onClickCancelPause: () -> Unit,
    onClickAccessibility: () -> Unit,
    onClickOverlaySettings: () -> Unit,
    onClickUsageAccess: () -> Unit,
    onClickSelectApp: () -> Unit,
    onClickAppTile: (String) -> Unit,
    onPositionInputChange: (OverlayPositionInput) -> Unit,
    onFinishTimer: () -> Unit,
) {
    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_usage_timer),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        titleModifier = Modifier.testTag(UsageTimerTestTags.TITLE),
    ) {
        // 권한 섹션
        SectionHeader(stringResource(R.string.permission_section_title))
        PermissionStatusRow(
            label = stringResource(R.string.permission_accessibility_service),
            granted = uiState.accessibilityServiceEnabled,
            onClick = onClickAccessibility,
            modifier = Modifier.testTag(UsageTimerTestTags.ACCESSIBILITY_STATUS),
        )
        PermissionStatusRow(
            label = stringResource(R.string.permission_overlay),
            granted = uiState.overlayPermissionGranted,
            onClick = onClickOverlaySettings,
            modifier = Modifier.testTag(UsageTimerTestTags.OVERLAY_STATUS),
        )
        PermissionStatusRow(
            label = stringResource(R.string.permission_usage_access),
            granted = uiState.usageAccessGranted,
            onClick = onClickUsageAccess,
            modifier = Modifier.testTag(UsageTimerTestTags.USAGE_ACCESS_STATUS),
        )

        // 일시정지 섹션. 저장된 값을 읽기 전에는 그리지 않아 일시정지 중 버튼을 잘못 보이지 않음
        SectionHeader(stringResource(R.string.usage_timer_section_pause))
        if (uiState.pauseStateLoaded) {
            PauseSection(
                uiState = uiState,
                onPauseDurationChange = onPauseDurationChange,
                onClickPause = onClickPause,
                onClickCancelPause = onClickCancelPause,
                onFinishTimer = onFinishTimer,
            )
        }

        // 표시 위치 섹션. 자주 바꾸지 않아 기본으로 접어 둠
        var positionExpanded by rememberSaveable { mutableStateOf(false) }
        CollapsibleSectionHeader(
            title = stringResource(R.string.overlay_position_section_title),
            expanded = positionExpanded,
            onToggle = { positionExpanded = !positionExpanded },
            modifier = Modifier.testTag(UsageTimerTestTags.POSITION_HEADER),
        )
        if (positionExpanded) {
            OverlayPositionSetting(
                input = uiState.positionInput,
                onInputChange = onPositionInputChange,
            )
            // 위치를 확인하는 미리보기라 위치 설정 아래에 둠
            ActionButton(
                text = stringResource(R.string.usage_timer_show),
                onClick = onClickShow,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .testTag(UsageTimerTestTags.SHOW_BUTTON),
            )
        }

        // 타이머 대상 앱 섹션. 길이가 바뀌는 구간 목록이 다른 조작을 밀어내지 않도록 맨 끝에 둠
        TargetAppsSection(
            uiState = uiState,
            onClickAppTile = onClickAppTile,
            onClickSelectApp = onClickSelectApp,
        )
    }
}

/**
 * 일시정지 섹션 본문
 *
 * 일시정지 중이 아니면 [onClickPause] 버튼만, 일시정지 중이면 남은 시간과 해제 버튼만 표시.
 */
@Composable
private fun PauseSection(
    uiState: UsageTimerUiState,
    onPauseDurationChange: (String) -> Unit,
    onClickPause: () -> Unit,
    onClickCancelPause: () -> Unit,
    onFinishTimer: () -> Unit,
) {
    OutlinedTextField(
        value = uiState.pauseDurationInput,
        onValueChange = onPauseDurationChange,
        label = {
            Text(
                text = stringResource(R.string.usage_timer_pause_duration_label),
                style = AppTypography.Caption,
            )
        },
        isError = uiState.showsPauseDurationError,
        supportingText = if (uiState.showsPauseDurationError) {
            {
                Text(
                    text = stringResource(R.string.usage_timer_pause_duration_error),
                    style = AppTextFieldDefaults.ErrorMessageTextStyle,
                    modifier = Modifier.testTag(UsageTimerTestTags.PAUSE_DURATION_ERROR),
                )
            }
        } else {
            null
        },
        singleLine = true,
        textStyle = AppTextFieldDefaults.InputTextStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = AppTextFieldDefaults.colors(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UsageTimerTestTags.PAUSE_DURATION_INPUT),
    )
    if (uiState.pauseRemainTimeMillis > 0L) {
        PauseRemainCountDown(
            pauseRemainTimeMillis = uiState.pauseRemainTimeMillis,
            onFinishTimer = onFinishTimer,
        )
        ActionButton(
            text = stringResource(R.string.usage_timer_cancel_pause),
            onClick = onClickCancelPause,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UsageTimerTestTags.CANCEL_BUTTON),
        )
    } else {
        ActionButton(
            text = stringResource(R.string.usage_timer_pause),
            onClick = onClickPause,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .testTag(UsageTimerTestTags.PAUSE_BUTTON),
        )
    }
}

/** 일시정지 잔여 시간 카운트다운(1초 틱, 종료 시 onFinishTimer 호출) */
@Composable
private fun PauseRemainCountDown(
    pauseRemainTimeMillis: Long,
    onFinishTimer: () -> Unit,
) {
    var remainTimeMillis by remember(pauseRemainTimeMillis) {
        mutableLongStateOf(pauseRemainTimeMillis)
    }

    LaunchedEffect(pauseRemainTimeMillis) {
        val endElapsedRealtime = SystemClock.elapsedRealtime() + pauseRemainTimeMillis
        while (true) {
            val remain = endElapsedRealtime - SystemClock.elapsedRealtime()
            if (remain <= 0L) {
                onFinishTimer()
                break
            }
            remainTimeMillis = remain
            delay(1.seconds)
        }
    }

    Column(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .testTag(UsageTimerTestTags.PAUSE_INFO),
    ) {
        Text(
            text = stringResource(R.string.usage_timer_pause_remain),
            style = AppTypography.Caption,
        )
        Text(
            text = DataUtil.milliSecondsToTimeString("mm:ss", remainTimeMillis),
            style = AppTypography.Body.copy(
                color = AppColors.Title,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

/**
 * 타일 격자의 칸
 *
 * 대상 앱 타일과 격자 끝의 앱 선택 타일을 같은 목록으로 나누기 위한 구분.
 */
private sealed interface AppTileCell {
    data class App(val item: SelectedAppUiItem) : AppTileCell
    data object SelectApp : AppTileCell
}

/**
 * 타이머 대상 앱 섹션
 *
 * 사용 기록을 보여 줄 수 있으면 제목에 기간을 붙이고, 타일 격자 아래에 기록을 볼 앱의 구간 목록이나 상태 안내를 표시.
 */
@Composable
private fun TargetAppsSection(
    uiState: UsageTimerUiState,
    onClickAppTile: (String) -> Unit,
    onClickSelectApp: () -> Unit,
) {
    // 조회에 실패하면 기간 동안의 사용 시간을 보여 주지 못하므로 제목에서 기간을 뺌
    val usageAvailable = uiState.usageAccessGranted && !uiState.recentUsageUnavailable
    val periodHours = UsageTimerViewModel.RECENT_USAGE_PERIOD.inWholeHours
    SectionHeader(
        if (usageAvailable && uiState.selectedApps.isNotEmpty()) {
            stringResource(R.string.usage_timer_section_target_apps_with_period, periodHours)
        } else {
            stringResource(R.string.usage_timer_section_target_apps)
        },
    )
    val recentUsage = uiState.recentUsage
    val displays = remember(recentUsage) {
        recentUsage?.let { usage ->
            usage.sessions.mapValues { (_, sessions) -> buildAppUsageDisplay(sessions, usage.queriedAtMillis) }
        }
    }
    val resources = LocalContext.current.resources
    val loadingText = stringResource(R.string.usage_timer_usage_loading)
    AppTileGrid(
        apps = uiState.selectedApps,
        viewingPackageName = uiState.usageSessionsApp?.packageName,
        // 사용 시간 줄은 권한이 있는 동안 늘 자리를 차지하고, 조회 중이거나 조회에 실패해 결과가 없는 앱은 "–" 표시
        usageTextOf = if (uiState.usageAccessGranted) {
            { packageName ->
                displays?.get(packageName)?.let { formatUsageMinutes(resources, it.totalMinutes, it.hasSessions) } ?: loadingText
            }
        } else {
            null
        },
        onClickAppTile = onClickAppTile,
        onClickSelectApp = onClickSelectApp,
    )
    val viewingApp = uiState.usageSessionsApp
    when {
        viewingApp == null -> StatusText(
            text = stringResource(R.string.usage_timer_selected_apps_empty),
            tag = UsageTimerTestTags.SELECTED_APPS_EMPTY,
        )
        !uiState.usageAccessGranted -> StatusText(
            text = stringResource(R.string.usage_timer_usage_permission_hint),
            tag = UsageTimerTestTags.USAGE_PERMISSION_HINT,
        )
        uiState.recentUsageUnavailable -> StatusText(
            text = stringResource(R.string.usage_timer_sessions_failed),
            tag = UsageTimerTestTags.USAGE_SESSIONS_FAILED,
        )
        // 조회 중이면 아무것도 그리지 않음(거짓 "기록 없음" 방지)
        else -> displays?.get(viewingApp.packageName)?.let { display ->
            UsageSessionList(
                packageName = viewingApp.packageName,
                appLabel = viewingApp.label,
                display = display,
                periodHours = periodHours,
            )
        }
    }
}

/** 섹션 안 흐린 안내 문구 */
@Composable
private fun StatusText(text: String, tag: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(top = 12.dp, bottom = 24.dp)
            .testTag(tag),
        style = AppTypography.DimBody,
    )
}

/**
 * 같은 크기 타일 격자
 *
 * 열 수는 너비를 [MIN_TILE_WIDTH]로 나눠 정하고, 모든 칸에 같은 너비를 줌. 마지막 칸은 앱 선택 타일.
 * 칸 너비를 weight로 나누면 남는 픽셀을 일부 칸에 더해 칸끼리 1픽셀 어긋날 수 있어 너비를 직접 계산.
 * [usageTextOf]가 null이면 모든 타일에서 사용 시간 줄을 빼 크기를 서로 같게 유지.
 */
@Composable
private fun AppTileGrid(
    apps: List<SelectedAppUiItem>,
    viewingPackageName: String?,
    usageTextOf: ((String) -> String)?,
    onClickAppTile: (String) -> Unit,
    onClickSelectApp: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UsageTimerTestTags.SELECTED_APPS_LIST),
    ) {
        val columns = maxOf(1, ((maxWidth + TILE_SPACING) / (MIN_TILE_WIDTH + TILE_SPACING)).toInt())
        val tileWidth = (maxWidth - TILE_SPACING * (columns - 1)) / columns
        val cells: List<AppTileCell> = apps.map { AppTileCell.App(it) } + AppTileCell.SelectApp
        Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
            cells.chunked(columns).forEach { rowCells ->
                // 글꼴을 키워 사용 시간이 줄바꿈돼도 같은 줄 타일 높이를 가장 높은 타일에 맞춤
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                ) {
                    rowCells.forEach { cell ->
                        val cellModifier = Modifier
                            .width(tileWidth)
                            .fillMaxHeight()
                        when (cell) {
                            is AppTileCell.App -> AppTile(
                                app = cell.item,
                                usageText = usageTextOf?.invoke(cell.item.packageName),
                                viewing = cell.item.packageName == viewingPackageName,
                                onClick = { onClickAppTile(cell.item.packageName) },
                                modifier = cellModifier,
                            )
                            AppTileCell.SelectApp -> SelectAppTile(
                                showsUsageLine = usageTextOf != null,
                                onClick = onClickSelectApp,
                                modifier = cellModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 대상 앱 타일
 *
 * 이름은 두 줄 자리를 늘 차지하고 넘치면 말줄임, 사용 시간은 말줄임하지 않음(잘리면 다른 값으로 읽힘).
 * 기록을 보는 앱은 밝은 테두리와 탭 선택 상태로 표시.
 */
@Composable
private fun AppTile(
    app: SelectedAppUiItem,
    usageText: String?,
    viewing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .containerBox(borderColor = if (viewing) AppColors.Icon else AppColors.ContainerBorder, clip = true)
            .selectable(selected = viewing, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp)
            .testTag(UsageTimerTestTags.appTile(app.packageName)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIconImage(
            icon = app.icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        TileLabel(app.label)
        if (usageText != null) {
            Text(
                text = usageText,
                style = AppTypography.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(UsageTimerTestTags.selectedAppUsage(app.packageName)),
            )
        }
    }
}

/**
 * 앱 선택 타일
 *
 * 대상 앱 타일과 같은 크기를 갖도록 [showsUsageLine]이면 사용 시간 줄 자리를 보이지 않는 글자로 채움.
 */
@Composable
private fun SelectAppTile(showsUsageLine: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .containerBox(clip = true)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp)
            .testTag(UsageTimerTestTags.SELECT_APP_BUTTON),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp),
            tint = AppColors.Icon,
        )
        TileLabel(stringResource(R.string.select_app))
        if (showsUsageLine) {
            // 빈 글자는 줄 높이가 사용 시간 줄과 다를 수 있어 같은 스타일의 실제 문구를 투명하게 그리고, 버튼 문구에 섞이지 않도록 의미 정보에서 뺌
            Text(
                text = stringResource(R.string.usage_timer_usage_minutes, 0),
                style = AppTypography.Caption,
                modifier = Modifier
                    .alpha(0f)
                    .clearAndSetSemantics {},
            )
        }
    }
}

/**
 * 타일 이름
 *
 * 한 줄 이름도 두 줄 이름과 높이가 같도록 두 줄 자리를 늘 차지.
 */
@Composable
private fun TileLabel(text: String) {
    Text(
        text = text,
        style = AppTypography.BrightBody.copy(fontSize = 13.sp, textAlign = TextAlign.Center),
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 한 앱의 사용 구간 목록
 *
 * 최신 [VISIBLE_SESSION_COUNT]줄만 보여 주고 나머지는 더 보기로 펼침. 펼침 상태는 앱을 바꾸면 접힌 상태로 돌아감.
 */
@Composable
private fun UsageSessionList(
    packageName: String,
    appLabel: String,
    display: AppUsageDisplay,
    periodHours: Long,
) {
    var expanded by rememberSaveable(packageName) { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    Text(
        text = stringResource(R.string.usage_timer_sessions_title, appLabel),
        style = AppTypography.BrightBody.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .padding(top = 16.dp)
            .testTag(UsageTimerTestTags.USAGE_SESSIONS_TITLE),
    )
    if (!display.hasSessions) {
        StatusText(
            text = stringResource(R.string.usage_timer_sessions_empty, periodHours),
            tag = UsageTimerTestTags.USAGE_SESSIONS_EMPTY,
        )
        return
    }
    val groups = if (expanded) display.groups else display.visibleGroups(VISIBLE_SESSION_COUNT)
    var rowIndex = 0
    groups.forEach { group ->
        Text(
            text = stringResource(
                R.string.usage_timer_day_total,
                stringResource(dayLabelResId(group.day)),
                formatUsageMinutes(resources, group.totalMinutes, hasSessions = true),
            ),
            style = AppTypography.Caption,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .testTag(UsageTimerTestTags.usageDayHeader(group.day)),
        )
        group.rows.forEach { row ->
            UsageSessionRowContent(
                row = row,
                modifier = Modifier.testTag(UsageTimerTestTags.usageSessionRow(rowIndex)),
            )
            rowIndex++
        }
    }
    val hiddenCount = display.rowCount - VISIBLE_SESSION_COUNT
    if (hiddenCount > 0) {
        Text(
            text = if (expanded) {
                stringResource(R.string.usage_timer_sessions_collapse)
            } else {
                stringResource(R.string.usage_timer_sessions_more, hiddenCount)
            },
            style = AppTypography.ButtonLabel.copy(color = AppColors.Icon),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .wrapContentHeight(Alignment.CenterVertically)
                .testTag(UsageTimerTestTags.USAGE_SESSIONS_MORE),
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
}

/**
 * 구간 한 줄
 *
 * 왼쪽에 시각 범위, 오른쪽 끝에 사용 시간 표시.
 */
@Composable
private fun UsageSessionRowContent(row: UsageSessionRow, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            // 시각 범위와 사용 시간을 한 노드로 검증·낭독하도록 병합
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = sessionRangeText(row),
            style = AppTypography.Body,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatUsageMinutes(LocalContext.current.resources, row.minutes, hasSessions = true),
            style = AppTypography.BrightBody.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * 구간 시각 범위 문구
 *
 * 기간 경계에서 잘린 시작은 "이전"을 붙이고, 진행 중이면 끝 대신 "사용 중", 같은 분 안이면 시각 하나만 씀.
 */
@Composable
private fun sessionRangeText(row: UsageSessionRow): String {
    val startText = if (row.startsBeforePeriod) {
        stringResource(R.string.usage_timer_session_start_before, row.startText)
    } else {
        row.startText
    }
    val endText = when {
        row.inProgress -> stringResource(R.string.usage_timer_session_in_progress)
        row.endText != null -> row.endText
        else -> return startText
    }
    return stringResource(R.string.usage_timer_session_range, startText, endText)
}

/** 날짜 묶음 머리에 쓰는 날짜 이름 */
@StringRes
private fun dayLabelResId(day: UsageDay): Int = when (day) {
    UsageDay.TODAY -> R.string.usage_timer_day_today
    UsageDay.YESTERDAY -> R.string.usage_timer_day_yesterday
}

/**
 * 타일 최소 너비
 *
 * 기본 글꼴에서 "23시간 59분"이 한 줄에 들어가는 너비.
 */
private val MIN_TILE_WIDTH = 96.dp

/** 타일 사이 간격 */
private val TILE_SPACING = 8.dp

/** 구간 목록에서 펼치기 전에 보여 주는 줄 수 */
private const val VISIBLE_SESSION_COUNT = 5
