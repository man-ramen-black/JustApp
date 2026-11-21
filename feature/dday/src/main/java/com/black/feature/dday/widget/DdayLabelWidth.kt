package com.black.feature.dday.widget

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.black.feature.dday.R
import com.black.feature.dday.model.DdayItem
import com.black.feature.dday.model.DdayStyle
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
    val paint = widgetTextPaint(context, textSizeSp, Typeface.BOLD)
    val widestPixels = labels.maxOfOrNull { paint.measureText(it) } ?: return 0.dp
    return ceil(widestPixels / displayMetrics.density).dp
}

/** 모든 항목의 제목 칸 너비를 맞추는 데 쓰는 가장 넓은 제목과 날짜 문구 */
data class DdayWidestText(val title: String, val date: String)

/**
 * 가장 넓은 제목과 날짜 고르기
 *
 * 위젯은 이 문구를 보이지 않게 겹쳐 모든 항목의 제목 칸 너비를 맞춤.
 * 제목은 `DdayWidgetContent`처럼 sans-serif 보통 굵기로 재고, 제목이 없으면 제목 없음 문구로 잼. [items]가 비었으면 빈 문구.
 */
fun findWidestText(context: Context, items: List<DdayItem>, style: DdayStyle): DdayWidestText {
    val untitled = context.getString(R.string.dday_untitled)
    val titlePaint = widgetTextPaint(context, style.titleTextSize, Typeface.NORMAL)
    val datePaint = widgetTextPaint(context, style.dateTextSize, Typeface.NORMAL)
    return DdayWidestText(
        title = items.map { it.title ?: untitled }.maxByOrNull { titlePaint.measureText(it) }.orEmpty(),
        date = items.map { it.dateText }.maxByOrNull { datePaint.measureText(it) }.orEmpty(),
    )
}

/** 위젯 글꼴(sans-serif [typefaceStyle])과 sp를 현재 화면 배율로 바꾼 글씨 크기의 측정용 [TextPaint] */
private fun widgetTextPaint(context: Context, textSizeSp: Int, typefaceStyle: Int): TextPaint = TextPaint().apply {
    typeface = Typeface.create(Typeface.SANS_SERIF, typefaceStyle)
    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat(), context.resources.displayMetrics)
}
