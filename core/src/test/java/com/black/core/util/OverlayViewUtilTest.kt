package com.black.core.util

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.black.core.model.OverlayPosition
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [OverlayViewUtil]의 Gravity 기준 창 좌표 계산과 창 추가 실패 처리 검증 */
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

    /**
     * 시스템이 창 추가를 거부하면 예외 대신 false를 돌려주는지 검증
     *
     * Given: addView가 BadTokenException, SecurityException을 던지는 WindowManager를 쓰는 뷰 두 개
     * When: 각각 tryAttachView 호출
     * Then: 모두 false
     */
    @Test
    fun test_04_tryAttachViewReturnsFalseWhenRejected() = runTest {
        /** Given **/
        val badTokenView = View(contextWithWindowManager(WindowManager.BadTokenException("rejected")))
        val securityView = View(contextWithWindowManager(SecurityException("permission revoked")))

        /** When **/
        val badTokenResult = OverlayViewUtil.tryAttachView(badTokenView)
        val securityResult = OverlayViewUtil.tryAttachView(securityView)

        /** Then **/
        assertFalse(badTokenResult)
        assertFalse(securityResult)
    }

    /**
     * 오른쪽·아래 위치가 가장자리 기준 거리로 계산되는지 검증
     *
     * Given: 오른쪽·아래, 오른쪽 16dp·아래 25dp, 밀도 2.75
     * When: calculateWindowPlacement 호출
     * Then: RIGHT or BOTTOM, x 44, y 69
     */
    @Test
    fun test_05_placementRightBottomUsesEdgeMargins() = runTest {
        /** Given **/
        val position = OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.BOTTOM, marginBottomDp = 25, marginRightDp = 16)

        /** When **/
        val placement = OverlayViewUtil.calculateWindowPlacement(position, 2.75f)

        /** Then **/
        assertEquals(OverlayViewUtil.WindowPlacement(Gravity.RIGHT or Gravity.BOTTOM, 44, 69), placement)
    }

    /**
     * 가운데 위치가 양쪽 여백 차이의 절반 오프셋으로 계산되는지 검증
     *
     * Given: 가운데·가운데, 왼쪽 10·오른쪽 30·위 40·아래 0, 밀도 2
     * When: calculateWindowPlacement 호출
     * Then: CENTER_HORIZONTAL or CENTER_VERTICAL, x -20, y 40
     */
    @Test
    fun test_06_placementCenterUsesMarginDifference() = runTest {
        /** Given **/
        val position = OverlayPosition(
            HorizontalAnchor.CENTER, VerticalAnchor.CENTER,
            marginTopDp = 40, marginBottomDp = 0, marginLeftDp = 10, marginRightDp = 30,
        )

        /** When **/
        val placement = OverlayViewUtil.calculateWindowPlacement(position, 2f)

        /** Then **/
        assertEquals(
            OverlayViewUtil.WindowPlacement(Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL, -20, 40),
            placement,
        )
    }

    /**
     * 여백이 모두 0이면 좌표가 0인지 검증
     *
     * Given: 왼쪽·위, 여백 0, 밀도 3
     * When: calculateWindowPlacement 호출
     * Then: LEFT or TOP, x 0, y 0
     */
    @Test
    fun test_07_placementLeftTopWithoutMargins() = runTest {
        /** Given / When **/
        val placement = OverlayViewUtil.calculateWindowPlacement(OverlayPosition(), 3f)

        /** Then **/
        assertEquals(OverlayViewUtil.WindowPlacement(Gravity.LEFT or Gravity.TOP, 0, 0), placement)
    }

    /**
     * 붙은 쪽 여백만 쓰이는지 검증
     *
     * Given: 왼쪽·위, 왼쪽 7·위 3·오른쪽 99·아래 99, 밀도 1
     * When: calculateWindowPlacement 호출
     * Then: x 7, y 3
     */
    @Test
    fun test_08_placementUsesOnlyAnchoredMargins() = runTest {
        /** Given **/
        val position = OverlayPosition(marginTopDp = 3, marginBottomDp = 99, marginLeftDp = 7, marginRightDp = 99)

        /** When **/
        val placement = OverlayViewUtil.calculateWindowPlacement(position, 1f)

        /** Then **/
        assertEquals(7, placement.x)
        assertEquals(3, placement.y)
    }

    /**
     * Flow가 예외로 끝나면 기본값을 내보내는지 검증
     *
     * Given: 예외를 던지는 Flow
     * When: positionOrDefault 수집
     * Then: 기본값 하나
     */
    @Test
    fun test_09_positionOrDefaultEmitsDefaultOnError() = runTest {
        /** Given **/
        val defaultPosition = OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.BOTTOM)
        val broken = flow<OverlayPosition> { throw IllegalStateException() }

        /** When **/
        val result = OverlayViewUtil.positionOrDefault(broken, defaultPosition).toList()

        /** Then **/
        assertEquals(listOf(defaultPosition), result)
    }

    /**
     * 정상 Flow는 값을 그대로 내보내는지 검증
     *
     * Given: 값 두 개를 내보내는 Flow
     * When: positionOrDefault 수집
     * Then: 같은 값 목록
     */
    @Test
    fun test_10_positionOrDefaultPassesValues() = runTest {
        /** Given **/
        val first = OverlayPosition(marginTopDp = 1)
        val second = OverlayPosition(marginTopDp = 2)

        /** When **/
        val result = OverlayViewUtil.positionOrDefault(flowOf(first, second), OverlayPosition()).toList()

        /** Then **/
        assertEquals(listOf(first, second), result)
    }

    /**
     * 가로 3 × 세로 3의 모든 조합이 기대 gravity와 좌표로 계산되는지 검증
     *
     * Given: 위 10·아래 30·왼쪽 20·오른쪽 60 여백, 밀도 2, 가로·세로 기준 9가지 조합
     * When: 조합마다 calculateWindowPlacement 호출
     * Then: 조합마다 기대 gravity·x·y
     */
    @Test
    fun test_11_placementAllNineAnchorCombinations() = runTest {
        /** Given **/
        val horizontalExpectations = listOf(
            Triple(HorizontalAnchor.LEFT, Gravity.LEFT, 40),
            Triple(HorizontalAnchor.CENTER, Gravity.CENTER_HORIZONTAL, -40),
            Triple(HorizontalAnchor.RIGHT, Gravity.RIGHT, 120),
        )
        val verticalExpectations = listOf(
            Triple(VerticalAnchor.TOP, Gravity.TOP, 20),
            Triple(VerticalAnchor.CENTER, Gravity.CENTER_VERTICAL, -20),
            Triple(VerticalAnchor.BOTTOM, Gravity.BOTTOM, 60),
        )

        horizontalExpectations.forEach { (horizontal, horizontalGravity, expectedX) ->
            verticalExpectations.forEach { (vertical, verticalGravity, expectedY) ->
                val position = OverlayPosition(
                    horizontal, vertical,
                    marginTopDp = 10, marginBottomDp = 30, marginLeftDp = 20, marginRightDp = 60,
                )

                /** When **/
                val placement = OverlayViewUtil.calculateWindowPlacement(position, 2f)

                /** Then **/
                assertEquals(
                    "$horizontal·$vertical",
                    OverlayViewUtil.WindowPlacement(horizontalGravity or verticalGravity, expectedX, expectedY),
                    placement,
                )
            }
        }
    }

    /** addView가 [error]를 던지는 WindowManager를 돌려주는 Context */
    private fun contextWithWindowManager(error: Throwable): Context {
        val windowManager: WindowManager = mockk {
            every { addView(any(), any()) } throws error
        }
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.WINDOW_SERVICE) windowManager else super.getSystemService(name)
        }
    }
}
