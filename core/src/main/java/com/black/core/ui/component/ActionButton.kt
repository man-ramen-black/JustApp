package com.black.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppShapes
import com.black.core.ui.theme.AppTypography

/**
 * 화면 공용 액션 버튼
 *
 * 컨테이너 톤 박스 + ExtraBold 라벨 형태
 * 기본 자간은 영문 대문자 라벨용, 한글 라벨은 자간을 줄여 전달
 *
 * [enabled]가 false면 흐리게 보이고 눌리지 않음.
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = AppTypography.ButtonLabel.letterSpacing,
    enabled: Boolean = true,
) {
    val shape = AppShapes.Container
    Box(
        // 투명도가 배경·테두리까지 흐리게 하도록 그리기 Modifier보다 앞에 둠
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(AppColors.ContainerBackground)
            .border(1.dp, AppColors.ContainerBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppTypography.ButtonLabel,
            letterSpacing = letterSpacing,
        )
    }
}

/** 비활성 버튼의 투명도 */
private const val DISABLED_ALPHA = 0.4f
