package com.black.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.black.core.R

/** [OverlayPermissionDialog] 요소의 testTag */
object OverlayPermissionDialogTestTags {
    const val DIALOG = "overlay_permission_dialog"
    const val OPEN_SETTINGS_BUTTON = "open_overlay_settings_button"
}

/**
 * 다른 앱 위에 표시 권한 안내 다이얼로그
 *
 * 이 권한은 실행 중 허용 창이 없어, 확인을 누르면 이 앱의 권한 스위치가 있는 설정 화면을 바로 염
 *
 * @param message 권한이 필요한 기능 이름을 넣은 안내 문장(기능 모듈 문자열)
 */
@Composable
fun OverlayPermissionDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        message = message,
        confirmText = stringResource(R.string.open_overlay_settings),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(OverlayPermissionDialogTestTags.DIALOG),
        confirmButtonModifier = Modifier.testTag(OverlayPermissionDialogTestTags.OPEN_SETTINGS_BUTTON),
    )
}
