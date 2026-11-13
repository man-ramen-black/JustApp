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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.core.ui.component.OverlayPermissionDialog
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
            message = stringResource(R.string.floating_setting_overlay_permission_guide),
            onConfirm = viewModel::openOverlaySettings,
            onDismiss = viewModel::dismissOverlayPermissionDialog,
        )
    }
}

/**
 * 플로팅 버튼 설정 화면 콘텐츠(상태 무관 렌더링 전용)
 *
 * 계측 테스트가 상태를 주입해 기기 상태와 무관하게 스위치·상태 행의 분기마다 검증하도록 공개.
 * 계측 테스트가 app 모듈 androidTest에 있어 internal 대신 public
 */
@Composable
fun FloatingSettingContent(
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
            style = AppTypography.ParagraphCaption,
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
            style = AppTypography.ParagraphCaption,
        )
    }
}
