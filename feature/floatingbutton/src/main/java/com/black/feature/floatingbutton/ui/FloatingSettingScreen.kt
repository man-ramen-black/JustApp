package com.black.feature.floatingbutton.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.core.ui.component.ConfirmDialog
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.StatusRow
import com.black.core.ui.component.SwitchRow
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.theme.AppTypography
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.service.FloatingAccessibilityService

/**
 * [FloatingSettingScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유.
 * 계측 테스트가 HiltTestActivity·BaseUiTest가 있는 app 모듈 androidTest에 있어 internal 대신 public
 */
object FloatingSettingTestTags {
    const val TITLE = "floating_title"
    const val RUNNING_SWITCH = "floating_running_switch"
    const val OVERLAY_PERMISSION_STATUS = "overlay_permission_status"
    const val ACCESSIBILITY_STATUS = "accessibility_status"
    const val OVERLAY_PERMISSION_DIALOG = "overlay_permission_dialog"
    const val OPEN_OVERLAY_SETTINGS_BUTTON = "open_overlay_settings_button"
}

/** 플로팅 버튼 설정 화면 */
@Composable
fun FloatingSettingScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FloatingSettingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 설정 앱에서 권한·접근성 서비스를 켜고 돌아온 경우의 상태 갱신
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is FloatingSettingViewModel.EventOpenOverlaySettings -> {
                PermissionHelper.showDrawOverlaysSetting(context)
            }
            is FloatingSettingViewModel.EventOpenAccessibilitySettings -> {
                PermissionHelper.openAccessibilityServiceDetailSetting(context, FloatingAccessibilityService::class.java)
            }
            else -> Unit
        }
    }

    FloatingSettingContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onRunningChange = viewModel::setRunning,
        onClickOverlayPermission = viewModel::showOverlayPermissionDialog,
        onClickAccessibility = viewModel::openAccessibilitySettings,
    )

    if (uiState.showOverlayPermissionDialog) {
        OverlayPermissionDialog(
            onConfirm = viewModel::openOverlaySettings,
            onDismiss = viewModel::dismissOverlayPermissionDialog,
        )
    }
}

/** 플로팅 버튼 설정 화면 콘텐츠(상태 무관 렌더링 전용) */
@Composable
private fun FloatingSettingContent(
    uiState: FloatingSettingUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onRunningChange: (Boolean) -> Unit,
    onClickOverlayPermission: () -> Unit,
    onClickAccessibility: () -> Unit,
) {
    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_floating),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        titleModifier = Modifier.testTag(FloatingSettingTestTags.TITLE),
    ) {
        // 버튼 설정 섹션
        SectionHeader(title = stringResource(R.string.floating_setting_section_setting))
        SwitchRow(
            label = stringResource(R.string.floating_setting_running_label),
            checked = uiState.running,
            onCheckedChange = onRunningChange,
            modifier = Modifier.testTag(FloatingSettingTestTags.RUNNING_SWITCH),
        )
        Text(
            text = stringResource(R.string.floating_setting_running_description),
            modifier = Modifier.padding(top = 8.dp),
            style = AppTypography.Caption.copy(lineHeight = 18.sp),
        )

        // 상태 섹션
        SectionHeader(title = stringResource(R.string.floating_setting_section_status))
        StatusRow(
            label = stringResource(R.string.floating_setting_status_overlay),
            value = if (uiState.overlayPermissionGranted) {
                stringResource(R.string.floating_setting_status_allowed)
            } else {
                stringResource(R.string.floating_setting_status_required)
            },
            onClick = if (uiState.overlayPermissionGranted) null else onClickOverlayPermission,
            modifier = Modifier.testTag(FloatingSettingTestTags.OVERLAY_PERMISSION_STATUS),
        )
        StatusRow(
            label = stringResource(R.string.floating_setting_status_accessibility),
            value = if (uiState.accessibilityServiceEnabled) {
                stringResource(R.string.floating_setting_status_enabled)
            } else {
                stringResource(R.string.floating_setting_status_disabled)
            },
            onClick = if (uiState.accessibilityServiceEnabled) null else onClickAccessibility,
            modifier = Modifier.testTag(FloatingSettingTestTags.ACCESSIBILITY_STATUS),
        )
        Text(
            text = stringResource(R.string.floating_setting_accessibility_description),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            style = AppTypography.Caption.copy(lineHeight = 18.sp),
        )
    }
}

/**
 * 다른 앱 위에 표시 권한 안내 다이얼로그
 *
 * 이 권한은 실행 중 허용 창이 없어, 확인을 누르면 이 앱의 권한 스위치가 있는 설정 화면을 바로 염
 */
@Composable
private fun OverlayPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        message = stringResource(R.string.floating_setting_overlay_permission_guide),
        confirmText = stringResource(R.string.floating_setting_open_overlay_settings),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(FloatingSettingTestTags.OVERLAY_PERMISSION_DIALOG),
        confirmButtonModifier = Modifier.testTag(FloatingSettingTestTags.OPEN_OVERLAY_SETTINGS_BUTTON),
    )
}
