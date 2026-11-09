package com.black.app.ui.maintab.main.appinstallnotification

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.app.R
import com.black.app.ui.common.selectapp.SelectedAppUiItem
import com.black.core.ui.component.ActionButton
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.StatusRow
import com.black.core.ui.component.SwitchRow
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppShapes
import com.black.core.ui.theme.AppTypography
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents

/**
 * [AppInstallNotificationScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유
 */
internal object AppInstallNotificationTestTags {
    const val TITLE = "app_install_notification_title"
    const val ENABLED_SWITCH = "app_install_notification_switch"
    const val SERVICE_STATUS = "service_status"
    const val BATTERY_OPTIMIZATION_STATUS = "battery_optimization_status"
    const val NOTIFICATION_STATUS = "notification_status"
    const val INSTALL_PREVIEW = "install_preview"
    const val UPDATE_PREVIEW = "update_preview"
    const val SEND_INSTALL_TEST_BUTTON = "send_install_test_button"
    const val SEND_UPDATE_TEST_BUTTON = "send_update_test_button"
    const val STATUS_BAR_ICON_PREVIEW = "status_bar_icon_preview"
}

/** 앱 설치 알림 화면 */
@Composable
fun AppInstallNotificationScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppInstallNotificationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 설정 앱에서 돌아온 경우의 상태 갱신·서비스 시작
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is AppInstallNotificationViewModel.EventOpenBatteryOptimizationSettings -> {
                PermissionHelper.showIgnoreBatteryOptimizationSettings(context, withToast = true)
            }
            is AppInstallNotificationViewModel.EventOpenNotificationSettings -> {
                Toast.makeText(context, R.string.app_install_notification_settings_guide, Toast.LENGTH_LONG).show()
                PermissionHelper.showAppNotificationSettings(context)
            }
            is AppInstallNotificationViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    AppInstallNotificationContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onEnabledChange = viewModel::setEnabled,
        onClickBatteryOptimization = viewModel::openBatteryOptimizationSettings,
        onClickNotificationSettings = viewModel::openNotificationSettings,
        onClickSendTest = viewModel::sendTestNotification,
    )
}

/**
 * 앱 설치 알림 화면 콘텐츠(상태 무관 렌더링 전용)
 *
 * 계측 테스트가 상태를 주입해 기기 상태와 무관하게 스위치·상태 행의 분기마다 검증하도록 private 대신 internal
 */
@Composable
internal fun AppInstallNotificationContent(
    uiState: AppInstallNotificationUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onClickBatteryOptimization: () -> Unit,
    onClickNotificationSettings: () -> Unit,
    onClickSendTest: (isUpdate: Boolean) -> Unit,
) {
    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_app_install_notification),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        titleModifier = Modifier.testTag(AppInstallNotificationTestTags.TITLE),
    ) {
        // 알림 설정 섹션
        SectionHeader(title = stringResource(R.string.app_install_notification_section_alert))
        SwitchRow(
            label = stringResource(R.string.app_install_notification_switch_label),
            checked = uiState.serviceActivated,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.testTag(AppInstallNotificationTestTags.ENABLED_SWITCH),
        )
        Text(
            text = stringResource(R.string.app_install_notification_switch_description),
            modifier = Modifier.padding(top = 8.dp),
            style = AppTypography.Caption.copy(lineHeight = 18.sp),
        )

        // 상태 섹션
        SectionHeader(title = stringResource(R.string.app_install_notification_section_status))
        StatusRow(
            label = stringResource(R.string.app_install_notification_status_service),
            value = if (uiState.serviceRunning) {
                stringResource(R.string.app_install_notification_status_running)
            } else {
                stringResource(R.string.app_install_notification_status_stopped)
            },
            onClick = null,
            modifier = Modifier.testTag(AppInstallNotificationTestTags.SERVICE_STATUS),
        )
        StatusRow(
            label = stringResource(R.string.app_install_notification_status_battery),
            value = if (uiState.batteryOptimizationIgnored) {
                stringResource(R.string.app_install_notification_status_allowed)
            } else {
                stringResource(R.string.app_install_notification_status_required)
            },
            onClick = if (uiState.batteryOptimizationIgnored) null else onClickBatteryOptimization,
            modifier = Modifier.testTag(AppInstallNotificationTestTags.BATTERY_OPTIMIZATION_STATUS),
        )
        StatusRow(
            label = stringResource(R.string.app_install_notification_status_notification),
            value = if (uiState.notificationsEnabled) {
                stringResource(R.string.app_install_notification_status_allowed)
            } else {
                stringResource(R.string.app_install_notification_status_blocked)
            },
            onClick = if (uiState.notificationsEnabled) null else onClickNotificationSettings,
            modifier = Modifier.testTag(AppInstallNotificationTestTags.NOTIFICATION_STATUS),
        )

        // 미리보기 섹션
        SectionHeader(title = stringResource(R.string.app_install_notification_section_preview))
        NotificationPreviewBlock(
            label = stringResource(R.string.app_install_notification_preview_install),
            previewApp = uiState.previewApp,
            notifierApp = uiState.notifierApp,
            messageResId = R.string.package_install_notification_installed,
            cardTestTag = AppInstallNotificationTestTags.INSTALL_PREVIEW,
            buttonTestTag = AppInstallNotificationTestTags.SEND_INSTALL_TEST_BUTTON,
            onClickSendTest = { onClickSendTest(false) },
        )
        NotificationPreviewBlock(
            label = stringResource(R.string.app_install_notification_preview_update),
            previewApp = uiState.previewApp,
            notifierApp = uiState.notifierApp,
            messageResId = R.string.package_install_notification_updated,
            cardTestTag = AppInstallNotificationTestTags.UPDATE_PREVIEW,
            buttonTestTag = AppInstallNotificationTestTags.SEND_UPDATE_TEST_BUTTON,
            onClickSendTest = { onClickSendTest(true) },
            modifier = Modifier.padding(top = 16.dp),
        )

        // 상단바 아이콘 섹션
        SectionHeader(title = stringResource(R.string.app_install_notification_section_icon))
        StatusBarIconPreview(modifier = Modifier.testTag(AppInstallNotificationTestTags.STATUS_BAR_ICON_PREVIEW))
        Text(
            text = stringResource(R.string.app_install_notification_icon_shared),
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = AppTypography.Caption,
        )
    }
}

/** 알림 종류 캡션 + 알림 모형 + 테스트 알림 버튼 */
@Composable
private fun NotificationPreviewBlock(
    label: String,
    previewApp: SelectedAppUiItem?,
    notifierApp: SelectedAppUiItem?,
    @StringRes messageResId: Int,
    cardTestTag: String,
    buttonTestTag: String,
    onClickSendTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            modifier = Modifier.padding(bottom = 8.dp),
            style = AppTypography.Caption,
        )
        NotificationPreviewCard(
            previewApp = previewApp,
            notifierApp = notifierApp,
            message = stringResource(messageResId),
            modifier = Modifier.testTag(cardTestTag),
        )
        ActionButton(
            text = stringResource(R.string.app_install_notification_send_test),
            onClick = onClickSendTest,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag(buttonTestTag),
            letterSpacing = 0.sp,
        )
    }
}

/**
 * Android 11 이상 대화 알림 1:1 레이아웃 근사 모형
 *
 * 왼쪽은 예시 앱 아이콘과 알림을 게시한 앱(JustApp) 아이콘 배지, 오른쪽은 앱 이름·메시지.
 * One UI 실제 모양과 다를 수 있어 정확한 모양은 테스트 알림 보내기로 띄운 실제 알림으로 확인
 */
@Composable
private fun NotificationPreviewCard(
    previewApp: SelectedAppUiItem?,
    notifierApp: SelectedAppUiItem?,
    message: String,
    modifier: Modifier = Modifier,
) {
    val shape = AppShapes.NotificationPreview
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.ContainerBackground)
            .border(1.dp, AppColors.ContainerBorder, shape)
            // 앱 이름·메시지를 한 노드로 검증하도록 병합
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationIcon(previewApp = previewApp, notifierApp = notifierApp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = previewApp?.label.orEmpty(),
                    color = AppColors.ItemName,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = " · " + stringResource(R.string.app_install_notification_preview_time),
                    style = AppTypography.Caption,
                )
            }
            Text(
                text = message,
                modifier = Modifier.padding(top = 2.dp),
                style = AppTypography.Body,
            )
        }
    }
}

/**
 * 대화 아이콘(원형 예시 앱 아이콘 40dp + 오른쪽 아래 게시 앱 아이콘 배지 16dp)
 *
 * 대화 알림의 배지는 small icon이 아니라 알림을 게시한 앱(JustApp)의 아이콘
 */
@Composable
private fun ConversationIcon(
    previewApp: SelectedAppUiItem?,
    notifierApp: SelectedAppUiItem?,
) {
    Box(modifier = Modifier.size(40.dp)) {
        if (previewApp != null) {
            val iconBitmap = remember(previewApp.packageName) { previewApp.icon.toBitmap().asImageBitmap() }
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        }
        if (notifierApp != null) {
            val badgeBitmap = remember(notifierApp.packageName) { notifierApp.icon.toBitmap().asImageBitmap() }
            // 예시 앱 아이콘과 겹치는 경계가 보이도록 카드 배경색 테두리
            Image(
                bitmap = badgeBitmap,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(1.dp, AppColors.ContainerBackground, CircleShape),
            )
        }
    }
}

/** 상단바 small icon 미리보기(어두운·밝은 상단바 실제 크기 24dp + 확대 64dp) */
@Composable
private fun StatusBarIconPreview(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        StatusBarIconTile(
            caption = stringResource(R.string.app_install_notification_icon_dark),
            background = AppColors.ContainerBackground,
            tint = AppColors.Title,
            tileSize = 56.dp,
            iconSize = 24.dp,
        )
        StatusBarIconTile(
            caption = stringResource(R.string.app_install_notification_icon_light),
            background = AppColors.LightPreviewBackground,
            tint = AppColors.LightPreviewContent,
            tileSize = 56.dp,
            iconSize = 24.dp,
        )
        StatusBarIconTile(
            caption = stringResource(R.string.app_install_notification_icon_enlarged),
            background = AppColors.ContainerBackground,
            tint = AppColors.Title,
            tileSize = 96.dp,
            iconSize = 64.dp,
        )
    }
}

/** 배경 타일 위 ic_app_install + 아래 크기 캡션 */
@Composable
private fun StatusBarIconTile(
    caption: String,
    background: Color,
    tint: Color,
    tileSize: Dp,
    iconSize: Dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(tileSize)
                .background(background, AppShapes.Container)
                .border(1.dp, AppColors.ContainerBorder, AppShapes.Container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_app_install),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
        Text(
            text = caption,
            modifier = Modifier.padding(top = 6.dp),
            style = AppTypography.Caption.copy(fontSize = 11.sp),
        )
    }
}
