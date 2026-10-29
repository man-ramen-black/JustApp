package com.black.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.black.app.ui.theme.BoldColors
import com.black.app.ui.theme.BoldShapes
import com.black.app.ui.theme.BoldTypography

/**
 * Bold 디자인 언어 액션 버튼(컨테이너 톤 박스 + ExtraBold 라벨)
 *
 * 기본 자간은 영문 대문자 라벨용, 한글 라벨은 자간을 줄여 전달
 */
@Composable
internal fun BoldActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = BoldTypography.ButtonLabel.letterSpacing,
) {
    val shape = BoldShapes.Container
    Box(
        modifier = modifier
            .clip(shape)
            .background(BoldColors.ContainerBackground)
            .border(1.dp, BoldColors.ContainerBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BoldTypography.ButtonLabel,
            letterSpacing = letterSpacing,
        )
    }
}
