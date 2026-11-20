package com.black.feature.dday.widget

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * 남은 날짜 칸 너비 측정
 *
 * 항목마다 `D-9`·`D-Day`처럼 글자 수가 달라 제목 시작 위치가 어긋나지 않도록, 가장 긴 라벨 기준으로 모든 항목의 칸 너비를 맞춤.
 * 라벨은 `DdayWidgetContent`에서 sans-serif 굵은 글꼴로 그리므로 같은 글꼴(`Typeface.SANS_SERIF` 굵게)로 재야 칸이 모자라 잘리지 않음.
 * 글씨 크기는 sp를 현재 화면 배율로 바꿔 재며, 결과는 dp로 올림. [labels]가 비었으면 0.dp.
 */
fun measureLabelWidth(context: Context, labels: List<String>, textSizeSp: Int): Dp {
    val displayMetrics = context.resources.displayMetrics
    val paint = TextPaint().apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat(), displayMetrics)
    }
    val widestPixels = labels.maxOfOrNull { paint.measureText(it) } ?: return 0.dp
    return ceil(widestPixels / displayMetrics.density).dp
}
