package com.black.feature.dday.model

/**
 * 위젯 스타일 조절 항목 하나
 *
 * [min]·[max]는 허용 범위, [default]는 초기값. 글씨 크기는 sp, 간격과 여백은 dp, [ITEM_COUNT]는 위젯에 보이는 일정 수.
 */
enum class DdayStyleField(val min: Int, val max: Int, val default: Int) {
    LABEL_TEXT_SIZE(8, 48, 20),
    TITLE_TEXT_SIZE(8, 48, 16),
    DATE_TEXT_SIZE(8, 48, 12),
    LABEL_GAP(0, 40, 12),
    TITLE_DATE_GAP(0, 40, 2),
    ITEM_GAP(0, 40, 8),
    PADDING(0, 40, 12),
    ITEM_COUNT(1, DDAY_MAX_ITEMS, 3),
}

/** 위젯 스타일과 표시 일정 수 모음, 기본값은 각 [DdayStyleField.default] */
data class DdayStyle(
    val labelTextSize: Int = DdayStyleField.LABEL_TEXT_SIZE.default,
    val titleTextSize: Int = DdayStyleField.TITLE_TEXT_SIZE.default,
    val dateTextSize: Int = DdayStyleField.DATE_TEXT_SIZE.default,
    val labelGap: Int = DdayStyleField.LABEL_GAP.default,
    val titleDateGap: Int = DdayStyleField.TITLE_DATE_GAP.default,
    val itemGap: Int = DdayStyleField.ITEM_GAP.default,
    val padding: Int = DdayStyleField.PADDING.default,
    val itemCount: Int = DdayStyleField.ITEM_COUNT.default,
) {
    operator fun get(field: DdayStyleField): Int = when (field) {
        DdayStyleField.LABEL_TEXT_SIZE -> labelTextSize
        DdayStyleField.TITLE_TEXT_SIZE -> titleTextSize
        DdayStyleField.DATE_TEXT_SIZE -> dateTextSize
        DdayStyleField.LABEL_GAP -> labelGap
        DdayStyleField.TITLE_DATE_GAP -> titleDateGap
        DdayStyleField.ITEM_GAP -> itemGap
        DdayStyleField.PADDING -> padding
        DdayStyleField.ITEM_COUNT -> itemCount
    }

    /** [field]를 [value]로 바꾼 사본, 범위 밖 값은 [DdayStyleField.min]..[DdayStyleField.max]로 맞춤 */
    fun with(field: DdayStyleField, value: Int): DdayStyle {
        val clamped = value.coerceIn(field.min, field.max)
        return when (field) {
            DdayStyleField.LABEL_TEXT_SIZE -> copy(labelTextSize = clamped)
            DdayStyleField.TITLE_TEXT_SIZE -> copy(titleTextSize = clamped)
            DdayStyleField.DATE_TEXT_SIZE -> copy(dateTextSize = clamped)
            DdayStyleField.LABEL_GAP -> copy(labelGap = clamped)
            DdayStyleField.TITLE_DATE_GAP -> copy(titleDateGap = clamped)
            DdayStyleField.ITEM_GAP -> copy(itemGap = clamped)
            DdayStyleField.PADDING -> copy(padding = clamped)
            DdayStyleField.ITEM_COUNT -> copy(itemCount = clamped)
        }
    }
}

/** 위젯에 표시할 D-Day 항목 하나, [label]은 남은 날짜 문구, [dateText]는 날짜·시각 문구 */
data class DdayItem(
    val eventId: Long,
    val title: String?,
    val label: String,
    val dateText: String,
    val begin: Long,
    val end: Long,
)
