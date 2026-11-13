package com.black.app.ui.common.selectapp

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * 앱 아이콘 [Drawable] 표시
 *
 * 비트맵 변환을 [icon] 기준으로 기억해, 같은 패키지라도 아이콘이 바뀌면(미설치 대체 아이콘에서 실제 아이콘으로) 다시 그림
 */
@Composable
fun AppIconImage(
    icon: Drawable,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val iconBitmap = remember(icon) { icon.toBitmap().asImageBitmap() }
    Image(
        bitmap = iconBitmap,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
