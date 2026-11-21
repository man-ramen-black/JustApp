package com.black.feature.usagetimer.ui

import android.content.res.Resources
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.core.ui.component.ActionButton
import com.black.core.ui.component.AppTextFieldDefaults
import com.black.core.ui.component.OverlayPermissionDialog
import com.black.core.ui.component.OverlayPositionInput
import com.black.core.ui.component.OverlayPositionSetting
import com.black.core.ui.component.PermissionStatusRow
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.ToolScreenScaffold
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
    const val SAVE_BUTTON = "save_button"
    const val PAUSE_BUTTON = "pause_button"
    const val CANCEL_BUTTON = "cancel_button"
    const val ACCESSIBILITY_STATUS = "usage_timer_accessibility_status"
    const val OVERLAY_STATUS = "usage_timer_overlay_status"
    const val USAGE_ACCESS_STATUS = "usage_timer_usage_access_status"
    const val SELECT_APP_BUTTON = "select_app_button"
    const val SELECTED_APPS_EMPTY = "selected_apps_empty"
    const val SELECTED_APPS_LIST = "selected_apps_list"
    const val PAUSE_INFO = "pause_info"

    /** 선택 앱 한 건의 최근 사용 시간 줄 */
    fun selectedAppUsage(packageName: String): String = "selected_app_usage_$packageName"
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
        onPauseDurationInputChanged = viewModel::onPauseDurationInputChanged,
        onClickShow = viewModel::onClickShow,
        onClickSave = viewModel::onClickSave,
        onClickPause = viewModel::onClickPause,
        onClickCancelPause = viewModel::onClickCancelPause,
        onClickAccessibility = viewModel::onClickAccessibility,
        onClickOverlaySettings = viewModel::openOverlaySettings,
        onClickUsageAccess = viewModel::openUsageAccessSettings,
        onClickSelectApp = viewModel::onClickSelectApp,
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
    onPauseDurationInputChanged: (String) -> Unit,
    onClickShow: () -> Unit,
    onClickSave: () -> Unit,
    onClickPause: () -> Unit,
    onClickCancelPause: () -> Unit,
    onClickAccessibility: () -> Unit,
    onClickOverlaySettings: () -> Unit,
    onClickUsageAccess: () -> Unit,
    onClickSelectApp: () -> Unit,
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

        // 타이머 표시 섹션
        SectionHeader(stringResource(R.string.usage_timer_section_overlay))
        ActionButton(
            text = stringResource(R.string.usage_timer_show),
            onClick = onClickShow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UsageTimerTestTags.SHOW_BUTTON),
        )
        // 표시 위치 섹션
        SectionHeader(stringResource(R.string.overlay_position_section_title))
        OverlayPositionSetting(
            input = uiState.positionInput,
            onInputChange = onPositionInputChange,
        )

        // 일시정지 섹션
        SectionHeader(stringResource(R.string.usage_timer_section_pause))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = uiState.pauseDurationInput,
                onValueChange = onPauseDurationInputChanged,
                label = {
                    Text(
                        text = stringResource(R.string.usage_timer_pause_duration_label),
                        style = AppTypography.Caption,
                    )
                },
                textStyle = AppTextFieldDefaults.InputTextStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = AppTextFieldDefaults.colors(),
                modifier = Modifier
                    .weight(1f)
                    .testTag(UsageTimerTestTags.PAUSE_DURATION_INPUT),
            )
            ActionButton(
                text = stringResource(R.string.usage_timer_save),
                onClick = onClickSave,
                // 옆 입력 칸과 같은 높이
                modifier = Modifier
                    .height(OutlinedTextFieldDefaults.MinHeight)
                    .testTag(UsageTimerTestTags.SAVE_BUTTON),
            )
        }

        if (uiState.pauseRemainTimeMillis > 0L) {
            PauseRemainCountDown(
                pauseRemainTimeMillis = uiState.pauseRemainTimeMillis,
                onFinishTimer = onFinishTimer,
            )
        }

        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = stringResource(R.string.usage_timer_pause),
                onClick = onClickPause,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UsageTimerTestTags.PAUSE_BUTTON),
            )
            ActionButton(
                text = stringResource(R.string.usage_timer_cancel_pause),
                onClick = onClickCancelPause,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UsageTimerTestTags.CANCEL_BUTTON),
            )
        }

        // 설정 섹션
        SectionHeader(stringResource(R.string.usage_timer_section_settings))
        ActionButton(
            text = stringResource(R.string.select_app),
            onClick = onClickSelectApp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UsageTimerTestTags.SELECT_APP_BUTTON),
        )

        // 선택한 앱 섹션
        // 사용 시간을 보여 줄 때만 기간이 드러나는 제목
        val showsUsage = uiState.recentUsageMillis != null && uiState.selectedApps.isNotEmpty()
        SectionHeader(
            if (showsUsage) {
                stringResource(
                    R.string.usage_timer_section_selected_apps_with_usage,
                    UsageTimerViewModel.RECENT_USAGE_PERIOD.inWholeHours,
                )
            } else {
                stringResource(R.string.usage_timer_section_selected_apps)
            },
        )

        if (uiState.selectedApps.isEmpty()) {
            Text(
                text = stringResource(R.string.usage_timer_selected_apps_empty),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag(UsageTimerTestTags.SELECTED_APPS_EMPTY),
                style = AppTypography.DimBody,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag(UsageTimerTestTags.SELECTED_APPS_LIST),
            ) {
                items(uiState.selectedApps, key = { it.packageName }) { item ->
                    // 키가 없는 경우(앱 선택 직후 조회 전)는 줄 숨김
                    SelectedAppItemContent(
                        item = item,
                        usageMillis = uiState.recentUsageMillis?.get(item.packageName),
                    )
                }
            }
        }
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
 * 선택된 앱 한 건 표시
 *
 * 아이콘, 라벨, 최근 사용 시간으로 구성.
 */
@Composable
private fun SelectedAppItemContent(item: SelectedAppUiItem, usageMillis: Long?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppIconImage(
            icon = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = item.label,
            style = AppTypography.Body,
        )
        if (usageMillis != null) {
            Text(
                text = formatUsageDuration(LocalContext.current.resources, usageMillis),
                style = AppTypography.Caption,
                modifier = Modifier.testTag(UsageTimerTestTags.selectedAppUsage(item.packageName)),
            )
        }
    }
}

/**
 * 사용 시간을 화면 문구로 변환
 *
 * 분 단위 내림. 0보다 크고 1분 미만은 "1분 미만", 1시간 이상은 "시간 분"으로 표시
 */
internal fun formatUsageDuration(resources: Resources, usageMillis: Long): String {
    // 분 단위 내림
    val totalMinutes = usageMillis / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours >= 1L -> resources.getString(R.string.usage_timer_usage_hours_minutes, hours, minutes)
        usageMillis in 1L..59_999L -> resources.getString(R.string.usage_timer_usage_under_minute)
        else -> resources.getString(R.string.usage_timer_usage_minutes, minutes)
    }
}
