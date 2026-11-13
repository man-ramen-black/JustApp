package com.black.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.black.core.ui.theme.AppShapes

/**
 * 컨테이너 톤 박스 아이콘 버튼
 *
 * 보이는 박스는 [boxSize]로 두고 터치 영역은 Android 권장 최소 크기(48dp)로 넓힘.
 * 리플은 넓힌 터치 영역이 아니라 보이는 박스 안에만 표시
 */
@Composable
fun ContainerIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 40.dp,
    iconSize: Dp = 18.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ContainerIconBox(
            painter = painter,
            contentDescription = contentDescription,
            // 리플을 보이는 박스 모서리 안에만 그림(clip이 뒤의 리플·배경·아이콘을 함께 자름)
            modifier = Modifier
                .clip(AppShapes.Container)
                .indication(interactionSource, ripple()),
            boxSize = boxSize,
            iconSize = iconSize,
        )
    }
}
