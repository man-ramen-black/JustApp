package com.black.app.ui.maintab.main.studypopup.popup

import android.app.Activity
import android.view.ContextThemeWrapper
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.widget.ViewPager2
import com.black.app.R
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric

/** [StudyPopupView] 창 부착 시 바인딩 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupViewTest : BaseTest() {

    /**
     * 창에 부착될 때 크래시 없이 pager에 어댑터가 연결되는지 검증(화면 켜짐 시 오버레이 부착 경로)
     *
     * Given: 화면에 표시 중인 Activity, 앱 테마로 생성한 StudyPopupView
     * When: StudyPopupView를 Activity 화면에 부착
     * Then: lifecycle RESUMED + pager 어댑터가 StudyPopupPagerAdapter
     */
    @Test
    fun test_01_attachToWindowBindsPagerAdapter() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = StudyPopupView(ContextThemeWrapper(activity, R.style.Theme_Black))

        /** When **/
        activity.setContentView(view)

        /** Then **/
        assertEquals(Lifecycle.State.RESUMED, view.lifecycle.currentState)
        assertTrue(view.findViewById<ViewPager2>(R.id.pager).adapter is StudyPopupPagerAdapter)
    }
}
