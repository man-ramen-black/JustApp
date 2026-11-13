package com.black.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.black.core.R
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppShapes
import com.black.core.ui.theme.AppTypography

/**
 * 메시지 + 확인·취소 버튼 다이얼로그
 *
 * [modifier]는 다이얼로그, [confirmButtonModifier]는 확인 버튼에 적용돼 화면마다 testTag 지정에 사용
 *
 * @param title 메시지 위 제목(없으면 미표시)
 */
@Composable
fun ConfirmDialog(
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButtonModifier: Modifier = Modifier,
    title: String? = null,
) {
    ConfirmDialog(
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        confirmButtonModifier = confirmButtonModifier,
        title = title,
    ) {
        Text(
            text = message,
            style = AppTypography.BrightBody,
        )
    }
}

/**
 * 본문 자리에 [content]를 그리는 확인·취소 버튼 다이얼로그(입력 칸 등)
 *
 * [modifier]는 다이얼로그, [confirmButtonModifier]는 확인 버튼에 적용돼 화면마다 testTag 지정에 사용
 *
 * @param title 본문 위 제목(없으면 미표시)
 * @param confirmEnabled false면 확인 버튼을 누를 수 없고 흐린 색으로 표시
 */
@Composable
fun ConfirmDialog(
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButtonModifier: Modifier = Modifier,
    title: String? = null,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = AppShapes.Container,
        containerColor = AppColors.ContainerBackground,
        title = if (title != null) {
            {
                Text(
                    text = title,
                    // Material 다이얼로그 제목(headlineSmall)과 같은 크기·굵기·줄 높이
                    style = AppTypography.TopBarTitle.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 32.sp,
                    ),
                )
            }
        } else {
            null
        },
        text = content,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = confirmButtonModifier,
                enabled = confirmEnabled,
            ) {
                Text(
                    text = confirmText,
                    style = buttonTextStyle(if (confirmEnabled) AppColors.Title else AppColors.Arrow),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = buttonTextStyle(AppColors.Caption),
                )
            }
        },
    )
}

/** 버튼 글자 스타일(Material 다이얼로그 버튼과 같은 Medium 굵기) */
private fun buttonTextStyle(color: Color): TextStyle =
    AppTypography.Body.copy(color = color, fontWeight = FontWeight.Medium)
