package com.black.core.ui.component

import com.black.core.model.OverlayPosition
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor

/**
 * [OverlayPositionSetting]의 입력 상태
 *
 * 여백은 입력 칸에 보이는 문자열 그대로 보관하고 빈 칸은 [toPosition]에서 0으로 취급
 */
data class OverlayPositionInput(
    val horizontal: HorizontalAnchor = HorizontalAnchor.LEFT,
    val vertical: VerticalAnchor = VerticalAnchor.TOP,
    val marginTop: String = "0",
    val marginBottom: String = "0",
    val marginLeft: String = "0",
    val marginRight: String = "0",
) {
    /**
     * 입력을 [OverlayPosition]으로 변환
     *
     * 비어 있거나 숫자가 아닌 칸은 0으로 취급
     */
    fun toPosition(): OverlayPosition = OverlayPosition(
        horizontal = horizontal,
        vertical = vertical,
        marginTopDp = marginTop.toIntOrNull() ?: 0,
        marginBottomDp = marginBottom.toIntOrNull() ?: 0,
        marginLeftDp = marginLeft.toIntOrNull() ?: 0,
        marginRightDp = marginRight.toIntOrNull() ?: 0,
    )

    companion object {
        /** 여백 입력 최대 자릿수 */
        private const val MARGIN_MAX_DIGITS = 3

        /** [position]에서 입력 상태 생성 */
        fun from(position: OverlayPosition): OverlayPositionInput = OverlayPositionInput(
            horizontal = position.horizontal,
            vertical = position.vertical,
            marginTop = position.marginTopDp.toString(),
            marginBottom = position.marginBottomDp.toString(),
            marginLeft = position.marginLeftDp.toString(),
            marginRight = position.marginRightDp.toString(),
        )

        /**
         * 여백 입력 정리
         *
         * ASCII 숫자만 남기고, 3자리를 넘으면 [previous]를 그대로 돌려줘 입력을 무시
         */
        fun filterMargin(input: String, previous: String): String {
            val digits = input.filter { it in '0'..'9' }
            return if (digits.length > MARGIN_MAX_DIGITS) previous else digits
        }
    }
}
