package com.black.core.util

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
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
