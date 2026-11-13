package com.black.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppShapes

/**
 * 컨테이너 톤 상자(배경 + 1dp 컨테이너 테두리 + [shape] 모서리)
 *
 * @param background 상자 배경색(밝은 미리보기 타일처럼 테두리만 같은 상자에서 변경)
 * @param clip true면 뒤에 오는 수정자(클릭 리플 등)와 내용도 [shape] 밖을 자름
 */
fun Modifier.containerBox(
    shape: Shape = AppShapes.Container,
    background: Color = AppColors.ContainerBackground,
    clip: Boolean = false,
): Modifier = then(if (clip) Modifier.clip(shape) else Modifier)
    .background(background, shape)
    .border(1.dp, AppColors.ContainerBorder, shape)

/**
 * 누를 수 없는 컨테이너 톤 아이콘 상자(메인 목록 항목 아이콘 등 장식용)
 *
 * 누르는 아이콘은 터치 영역을 넓힌 [ContainerIconButton] 사용
 *
 * @param background 상자 배경색(밝은 미리보기 타일처럼 테두리만 같은 상자에서 변경)
 */
@Composable
fun ContainerIconBox(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    boxSize: Dp = 40.dp,
    iconSize: Dp = 18.dp,
    tint: Color = AppColors.Icon,
    background: Color = AppColors.ContainerBackground,
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .containerBox(background = background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = tint,
        )
    }
}
