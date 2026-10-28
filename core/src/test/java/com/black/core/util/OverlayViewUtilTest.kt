package com.black.core.util

import android.graphics.Point
import android.view.Gravity
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [OverlayViewUtil]의 Gravity 기준 창 좌표를 Top·Left 기준 좌표로 바꾸는 계산 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OverlayViewUtilTest : BaseTest() {

    /** 상단바만 빠진 1080x2340 세로 화면의 배치 영역 */
    private val containerSize = Point(1080, 2237)

    /**
     * 왼쪽·세로 중앙 Gravity 창이 배치 영역 세로 중앙 좌표로 바뀌는지 검증
     *
     * Given: LEFT·CENTER_VERTICAL, x·y 0, 300x100 뷰
     * When: calculateTopLeftPosition 호출
     * Then: x 0, y는 배치 영역 높이 절반에서 뷰 높이 절반을 뺀 1068
     */
    @Test
    fun test_01_leftCenterVerticalResolvesToVerticalCenter() = runTest {
        /** Given **/
        val gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

        /** When **/
        val position = OverlayViewUtil.calculateTopLeftPosition(gravity, 0, 0, containerSize, 300, 100)

        /** Then **/
        assertEquals(Point(0, 1068), position)
    }

    /**
     * 왼쪽·아래 Gravity 창의 y가 아래에서부터의 거리로 해석되는지 검증
     *
     * Given: LEFT·BOTTOM, x 0, y 70, 300x100 뷰
     * When: calculateTopLeftPosition 호출
     * Then: x 0, y는 배치 영역 높이에서 뷰 높이와 70을 뺀 2067
     */
    @Test
    fun test_02_leftBottomResolvesFromBottomEdge() = runTest {
        /** Given **/
        val gravity = Gravity.LEFT or Gravity.BOTTOM

        /** When **/
        val position = OverlayViewUtil.calculateTopLeftPosition(gravity, 0, 70, containerSize, 300, 100)

        /** Then **/
        assertEquals(Point(0, 2067), position)
    }

    /**
     * 위쪽·가로 중앙 Gravity 창이 배치 영역 가로 중앙 좌표로 바뀌는지 검증
     *
     * Given: TOP·CENTER_HORIZONTAL, x 0, y 20, 300x100 뷰
     * When: calculateTopLeftPosition 호출
     * Then: x는 배치 영역 너비 절반에서 뷰 너비 절반을 뺀 390, y 20
     */
    @Test
    fun test_03_topCenterHorizontalResolvesToHorizontalCenter() = runTest {
        /** Given **/
        val gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        /** When **/
        val position = OverlayViewUtil.calculateTopLeftPosition(gravity, 0, 20, containerSize, 300, 100)

        /** Then **/
        assertEquals(Point(390, 20), position)
    }
}
