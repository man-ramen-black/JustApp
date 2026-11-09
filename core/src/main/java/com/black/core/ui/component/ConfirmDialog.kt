package com.black.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.black.core.R
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppShapes

/**
 * 메시지 + 확인·취소 버튼 다이얼로그
 *
 * [modifier]는 다이얼로그, [confirmButtonModifier]는 확인 버튼에 적용돼 화면마다 testTag 지정에 사용
 */
@Composable
fun ConfirmDialog(
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButtonModifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = AppShapes.Container,
        containerColor = AppColors.ContainerBackground,
        text = {
            Text(
                text = message,
                color = AppColors.ItemName,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = confirmButtonModifier,
            ) {
                Text(confirmText, color = AppColors.Title)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = AppColors.Caption)
            }
        },
    )
}
