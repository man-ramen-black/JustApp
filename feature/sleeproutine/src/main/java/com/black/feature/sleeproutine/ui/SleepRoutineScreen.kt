package com.black.feature.sleeproutine.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.core.ui.component.ActionButton
import com.black.core.ui.component.AppTextFieldDefaults
import com.black.core.ui.component.PermissionStatusRow
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.SwitchRow
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents
import com.black.feature.sleeproutine.R
import java.util.Date
import kotlin.math.roundToInt

/**
 * [SleepRoutineScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유.
 * 계측 테스트가 HiltTestActivity·BaseUiTest가 있는 app 모듈 androidTest에 있어 internal 대신 public
 */
object SleepRoutineTestTags {
    const val TITLE = "sleep_routine_title"
    const val YOUTUBE_URL_INPUT = "youtube_url_input"
    const val DURATION_INPUT = "duration_input"
    const val VOLUME_SWITCH = "volume_switch"
    const val VOLUME_SLIDER = "volume_slider"
    const val VOLUME_LEVEL_TEXT = "volume_level_text"
    const val SAVE_BUTTON = "save_button"
    const val DO_NOT_DISTURB_STATUS = "do_not_disturb_status"
    const val OVERLAY_STATUS = "overlay_status"
    const val NOTIFICATION_LISTENER_STATUS = "notification_listener_status"
    const val NOTIFICATION_STATUS = "notification_status"
    const val ACCESSIBILITY_STATUS = "accessibility_status"
    const val START_BUTTON = "start_button"
    const val STOP_BUTTON = "stop_button"
    const val END_TIME_TEXT = "end_time_text"
}

/** 수면 루틴 화면 */
@Composable
fun SleepRoutineScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SleepRoutineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 설정 앱에서 권한을 바꾸고 돌아온 경우의 상태 갱신
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is SleepRoutineViewModel.EventOpenDoNotDisturbSettings -> {
                PermissionHelper.showNotificationPolicyAccessSettings(context)
            }
            is SleepRoutineViewModel.EventOpenOverlaySettings -> {
                PermissionHelper.showDrawOverlaysSetting(context)
            }
            is SleepRoutineViewModel.EventOpenNotificationListenerSettings -> {
                PermissionHelper.showNotificationListenerSettings(context)
            }
            is SleepRoutineViewModel.EventOpenNotificationSettings -> {
                PermissionHelper.showAppNotificationSettings(context)
            }
            is SleepRoutineViewModel.EventOpenAccessibilitySettings -> {
                PermissionHelper.openAccessibilityServiceDetailSetting(context)
            }
            is SleepRoutineViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    SleepRoutineContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onYoutubeUrlChange = viewModel::updateYoutubeUrlInput,
        onDurationChange = viewModel::updateDurationInput,
        onVolumeEnabledChange = viewModel::setVolumeEnabled,
        onVolumeLevelChange = viewModel::updateVolumeLevel,
        onClickSave = viewModel::save,
        onClickDoNotDisturb = viewModel::openDoNotDisturbSettings,
        onClickOverlay = viewModel::openOverlaySettings,
        onClickNotificationListener = viewModel::openNotificationListenerSettings,
        onClickNotification = viewModel::openNotificationSettings,
        onClickAccessibility = viewModel::openAccessibilitySettings,
        onClickStart = viewModel::startRoutine,
        onClickStop = viewModel::stopRoutine,
    )
}

/**
 * 수면 루틴 화면 콘텐츠
 *
 * [uiState]와 콜백만 받는 렌더링 전용.
 * 계측 테스트가 상태를 주입해 기기의 권한·서비스 실행 상태와 무관하게 상태 행·실행 섹션·설정 불러오기 전후 분기마다 검증하고,
 * 저장·시작·종료를 실제로 실행하지 않고 콜백 호출로 검증하도록 공개.
 * 계측 테스트가 app 모듈 androidTest에 있어 internal 대신 public
 */
@Composable
fun SleepRoutineContent(
    uiState: SleepRoutineUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onYoutubeUrlChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onVolumeEnabledChange: (Boolean) -> Unit,
    onVolumeLevelChange: (Int) -> Unit,
    onClickSave: () -> Unit,
    onClickDoNotDisturb: () -> Unit,
    onClickOverlay: () -> Unit,
    onClickNotificationListener: () -> Unit,
    onClickNotification: () -> Unit,
    onClickAccessibility: () -> Unit,
    onClickStart: () -> Unit,
    onClickStop: () -> Unit,
) {
    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_sleep_routine),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        titleModifier = Modifier.testTag(SleepRoutineTestTags.TITLE),
    ) {
        // YouTube 섹션
        SectionHeader(title = stringResource(R.string.sleep_routine_section_youtube))
        SettingTextField(
            value = uiState.youtubeUrlInput,
            onValueChange = onYoutubeUrlChange,
            label = stringResource(R.string.sleep_routine_youtube_url_label),
            isError = uiState.youtubeUrlError,
            errorMessage = stringResource(R.string.sleep_routine_youtube_url_error),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            enabled = uiState.settingsLoaded,
            modifier = Modifier.testTag(SleepRoutineTestTags.YOUTUBE_URL_INPUT),
        )
        SettingTextField(
            value = uiState.durationInput,
            onValueChange = onDurationChange,
            label = stringResource(R.string.sleep_routine_duration_label),
            isError = uiState.durationError,
            errorMessage = stringResource(R.string.sleep_routine_duration_error),
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            enabled = uiState.settingsLoaded,
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(SleepRoutineTestTags.DURATION_INPUT),
        )

        // 미디어 볼륨 섹션
        SectionHeader(title = stringResource(R.string.sleep_routine_section_volume))
        SwitchRow(
            label = stringResource(R.string.sleep_routine_volume_switch),
            checked = uiState.volumeEnabled,
            onCheckedChange = onVolumeEnabledChange,
            modifier = Modifier.testTag(SleepRoutineTestTags.VOLUME_SWITCH),
            enabled = uiState.settingsLoaded,
        )
        VolumeLevelRow(
            volumeLevel = uiState.volumeLevel,
            maxVolumeLevel = uiState.maxVolumeLevel,
            enabled = uiState.settingsLoaded && uiState.volumeEnabled,
            onVolumeLevelChange = onVolumeLevelChange,
        )
        ActionButton(
            text = stringResource(R.string.sleep_routine_save),
            onClick = onClickSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .testTag(SleepRoutineTestTags.SAVE_BUTTON),
            enabled = uiState.settingsLoaded,
        )

        // 권한 섹션
        SectionHeader(title = stringResource(R.string.permission_section_title))
        PermissionStatusRow(
            label = stringResource(R.string.sleep_routine_permission_do_not_disturb),
            granted = uiState.doNotDisturbAccessGranted,
            onClick = onClickDoNotDisturb,
            modifier = Modifier.testTag(SleepRoutineTestTags.DO_NOT_DISTURB_STATUS),
        )
        PermissionStatusRow(
            label = stringResource(R.string.permission_overlay),
            granted = uiState.overlayGranted,
            onClick = onClickOverlay,
            modifier = Modifier.testTag(SleepRoutineTestTags.OVERLAY_STATUS),
        )
        PermissionStatusRow(
            label = stringResource(R.string.sleep_routine_permission_notification_listener),
            granted = uiState.notificationListenerEnabled,
            onClick = onClickNotificationListener,
            modifier = Modifier.testTag(SleepRoutineTestTags.NOTIFICATION_LISTENER_STATUS),
        )
        PermissionStatusRow(
            label = stringResource(R.string.sleep_routine_permission_notification),
            granted = uiState.notificationsEnabled,
            onClick = onClickNotification,
            modifier = Modifier.testTag(SleepRoutineTestTags.NOTIFICATION_STATUS),
        )
        PermissionStatusRow(
            label = stringResource(R.string.permission_accessibility_service),
            granted = uiState.accessibilityServiceEnabled,
            onClick = onClickAccessibility,
            modifier = Modifier.testTag(SleepRoutineTestTags.ACCESSIBILITY_STATUS),
        )
        Text(
            text = stringResource(R.string.sleep_routine_permission_guide),
            modifier = Modifier.padding(top = 8.dp),
            style = AppTypography.ParagraphCaption,
        )

        // 실행 섹션
        SectionHeader(title = stringResource(R.string.sleep_routine_section_run))
        if (uiState.inProgress) {
            uiState.sessionEndTimeMillis?.let { endTimeMillis ->
                EndTimeText(endTimeMillis = endTimeMillis)
            }
            ActionButton(
                text = stringResource(R.string.sleep_routine_stop),
                onClick = onClickStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SleepRoutineTestTags.STOP_BUTTON),
            )
        } else {
            ActionButton(
                text = stringResource(R.string.sleep_routine_start),
                onClick = onClickStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SleepRoutineTestTags.START_BUTTON),
                enabled = uiState.settingsLoaded,
            )
        }
        Text(
            text = stringResource(R.string.sleep_routine_shortcut_guide),
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = AppTypography.ParagraphCaption,
        )
    }
}

/**
 * 세션 종료 예정 시각 문구
 *
 * 앱 업데이트·강제 중지로 서비스가 멈춰 종료 시각이 지난 세션만 남았으면 지난 시각 대신 종료로 마무리하라는 안내 표시.
 * 서비스가 실행 중이면 종료 시각에 세션을 지우므로 지난 시각 안내는 서비스가 멈춘 경우에만 남음
 */
@Composable
private fun EndTimeText(endTimeMillis: Long) {
    val context = LocalContext.current
    val text = if (endTimeMillis <= System.currentTimeMillis()) {
        stringResource(R.string.sleep_routine_end_time_passed)
    } else {
        stringResource(
            R.string.sleep_routine_end_time,
            DateFormat.getTimeFormat(context).format(Date(endTimeMillis)),
        )
    }
    Text(
        text = text,
        modifier = Modifier
            .padding(bottom = 12.dp)
            .testTag(SleepRoutineTestTags.END_TIME_TEXT),
        style = AppTypography.BrightBody,
    )
}

/**
 * 한 줄 설정 입력 칸
 *
 * [isError]일 때만 칸 아래에 [errorMessage] 표시
 */
@Composable
private fun SettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorMessage: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(text = label, style = AppTypography.Caption) },
        supportingText = if (isError) {
            { Text(text = errorMessage, style = AppTextFieldDefaults.ErrorMessageTextStyle) }
        } else {
            null
        },
        isError = isError,
        textStyle = AppTextFieldDefaults.InputTextStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        singleLine = true,
        colors = AppTextFieldDefaults.colors(),
    )
}

/**
 * 미디어 볼륨 단계 슬라이더 + 현재 단계 문구
 *
 * [enabled]가 false면 슬라이더를 막고 단계 문구를 흐리게 표시
 */
@Composable
private fun VolumeLevelRow(
    volumeLevel: Int,
    maxVolumeLevel: Int,
    enabled: Boolean,
    onVolumeLevelChange: (Int) -> Unit,
) {
    val volumeDescription = stringResource(R.string.sleep_routine_section_volume)
    val volumeLevelText = stringResource(R.string.sleep_routine_volume_level, volumeLevel, maxVolumeLevel)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = volumeLevel.toFloat(),
            onValueChange = { onVolumeLevelChange(it.roundToInt()) },
            modifier = Modifier
                .weight(1f)
                // TalkBack이 기본 백분율 대신 화면 문구와 같은 단계로 읽도록 상태 설명 지정
                .semantics {
                    contentDescription = volumeDescription
                    stateDescription = volumeLevelText
                }
                .testTag(SleepRoutineTestTags.VOLUME_SLIDER),
            enabled = enabled,
            valueRange = 0f..maxVolumeLevel.toFloat(),
            // 0부터 최대까지 정수 단계마다 멈추도록 양 끝 사이 지점 수 지정
            steps = (maxVolumeLevel - 1).coerceAtLeast(0),
            // 테마 primary(#121212)가 검은 배경과 구분되지 않아 토큰 색 지정
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Title,
                activeTrackColor = AppColors.Title,
                activeTickColor = AppColors.ScreenBackground,
                inactiveTrackColor = AppColors.ContainerBackground,
                inactiveTickColor = AppColors.UncheckedControl,
                disabledThumbColor = AppColors.UncheckedControl,
                disabledActiveTrackColor = AppColors.UncheckedControl,
                disabledActiveTickColor = AppColors.ContainerBackground,
                disabledInactiveTrackColor = AppColors.ContainerBackground,
                disabledInactiveTickColor = AppColors.UncheckedControl,
            ),
        )
        Text(
            text = volumeLevelText,
            modifier = Modifier
                .padding(start = 12.dp)
                .testTag(SleepRoutineTestTags.VOLUME_LEVEL_TEXT),
            style = if (enabled) AppTypography.Body else AppTypography.DimBody,
        )
    }
}

