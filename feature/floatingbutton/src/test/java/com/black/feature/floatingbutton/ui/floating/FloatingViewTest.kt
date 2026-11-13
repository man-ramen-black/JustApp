package com.black.feature.floatingbutton.ui.floating

import android.content.ComponentName
import android.content.Intent
import com.black.feature.floatingbutton.service.FloatingAccessibilityService
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/** [FloatingView]의 뒤로 가기·홈 버튼이 시작하는 설정·홈 화면 인텐트 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FloatingViewTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()

    /**
     * 접근성 서비스가 꺼져 있으면 뒤로 가기 버튼이 이 기능의 접근성 상세 화면을 여는지 검증
     *
     * Given: 접근성 서비스 꺼짐(활성 서비스 설정 없음), 애플리케이션 컨텍스트의 FloatingView
     * When: goBack 호출
     * Then: 안내 토스트, ACCESSIBILITY_DETAILS_SETTINGS 인텐트에 FloatingAccessibilityService 컴포넌트 이름과 FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    fun test_01_goBackOpensAccessibilityDetailWhenDisabled() = runTest {
        /** Given **/
        val view = FloatingView(application)

        /** When **/
        view.goBack()

        /** Then **/
        assertEquals("접근성 설정이 필요합니다.", ShadowToast.getTextOfLatestToast())
        val intent = shadowOf(application).nextStartedActivity
        assertEquals(ACTION_ACCESSIBILITY_DETAILS_SETTINGS, intent.action)
        assertEquals(
            ComponentName(application, FloatingAccessibilityService::class.java).flattenToString(),
            intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME),
        )
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * 홈 버튼이 Activity가 아닌 컨텍스트에서 새 태스크로 홈 화면을 여는지 검증
     *
     * Given: 애플리케이션 컨텍스트의 FloatingView
     * When: goHome 호출
     * Then: ACTION_MAIN, CATEGORY_HOME, FLAG_ACTIVITY_NEW_TASK 포함 인텐트 시작
     */
    @Test
    fun test_02_goHomeStartsHomeInNewTask() = runTest {
        /** Given **/
        val view = FloatingView(application)

        /** When **/
        view.goHome()

        /** Then **/
        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    private companion object {
        /** PermissionHelper가 쓰는 비공개 접근성 상세 화면 액션 */
        const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    }
}
