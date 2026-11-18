package com.black.core.ui.component

import com.black.core.model.OverlayPosition
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [OverlayPositionInput]의 여백 입력 정리와 [OverlayPosition] 변환 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OverlayPositionInputTest : BaseTest() {

    /**
     * 여백 입력에서 숫자만 남기고 3자리 이하는 그대로 허용하는지 검증
     *
     * Given: 숫자가 아닌 글자, 부호, 빈 문자열이 섞인 3자리 이하 입력
     * When: filterMargin 호출
     * Then: 숫자만 남음
     */
    @Test
    fun test_01_filterMarginKeepsDigitsUpToThree() = runTest {
        /** Given / When / Then **/
        assertEquals("12", OverlayPositionInput.filterMargin("12a", "5"))
        assertEquals("5", OverlayPositionInput.filterMargin("-5", "0"))
        assertEquals("", OverlayPositionInput.filterMargin("", "7"))
        assertEquals("000", OverlayPositionInput.filterMargin("000", "0"))
    }

    /**
     * 숫자가 3자리를 넘으면 입력이 무시되고 이전 값이 유지되는지 검증
     *
     * Given: 이전 값 "123"에 숫자를 더한 "1234", 이전 값 "100" 앞에 2를 넣은 "2100"
     * When: filterMargin 호출
     * Then: 이전 값 그대로
     */
    @Test
    fun test_02_filterMarginKeepsPreviousWhenOverThreeDigits() = runTest {
        /** Given / When / Then **/
        assertEquals("123", OverlayPositionInput.filterMargin("1234", "123"))
        assertEquals("100", OverlayPositionInput.filterMargin("2100", "100"))
    }

    /**
     * 전각·비ASCII 숫자가 걸러지는지 검증
     *
     * Given: 전각 숫자가 섞인 입력 "１２3"
     * When: filterMargin 호출
     * Then: ASCII 숫자만 남은 "3"
     */
    @Test
    fun test_03_filterMarginDropsNonAsciiDigits() = runTest {
        /** Given / When / Then **/
        assertEquals("3", OverlayPositionInput.filterMargin("１２3", ""))
    }

    /**
     * 빈 칸이 0으로 변환되는지 검증
     *
     * Given: 위 여백 빈 칸, 아래 여백 "25"
     * When: toPosition 호출
     * Then: marginTopDp 0, marginBottomDp 25
     */
    @Test
    fun test_04_toPositionTreatsBlankAsZero() = runTest {
        /** Given **/
        val input = OverlayPositionInput(marginTop = "", marginBottom = "25")

        /** When **/
        val position = input.toPosition()

        /** Then **/
        assertEquals(0, position.marginTopDp)
        assertEquals(25, position.marginBottomDp)
    }

    /**
     * 위치에서 입력 상태를 만들었다가 되돌리면 원래 값과 같은지 검증
     *
     * Given: 오른쪽·아래, 여백 1·2·3·4인 위치
     * When: from 후 toPosition 호출
     * Then: 원래 값과 같음
     */
    @Test
    fun test_05_fromAndToPositionRoundTrip() = runTest {
        /** Given **/
        val position = OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.BOTTOM, 1, 2, 3, 4)

        /** When **/
        val restored = OverlayPositionInput.from(position).toPosition()

        /** Then **/
        assertEquals(position, restored)
    }
}
