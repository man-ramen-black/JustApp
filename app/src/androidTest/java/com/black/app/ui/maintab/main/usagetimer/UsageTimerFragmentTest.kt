package com.black.app.ui.maintab.main.usagetimer

import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelStore
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.black.app.R
import com.black.app.testutil.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [UsageTimerFragment]가 실제 기기(계측 환경)에서 정상 노출되는지 검증하는 Espresso UI 테스트
 *
 * @AndroidEntryPoint Fragment이므로 [HiltTestActivity]로 호스팅하고,
 * onViewCreated에서 findNavController()를 사용하므로 컨테이너에 [TestNavHostController]를 미리 부착한다.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UsageTimerFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun usageTimerFragmentDisplaysCoreViews() {
        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Fragment가 findNavController()를 사용하므로, View 생성 전에 컨테이너에 NavController를 부착한다.
                val navController = TestNavHostController(activity).apply {
                    // savedStateHandle(popBackStack 결과 observe)에 접근하려면 ViewModelStore가 필요
                    setViewModelStore(ViewModelStore())
                    setGraph(R.navigation.root_navigation)
                    setCurrentDestination(R.id.usage_timer_fragment)
                }
                val container = FrameLayout(activity).apply { id = View.generateViewId() }
                activity.setContentView(container)
                Navigation.setViewNavController(container, navController)

                activity.supportFragmentManager.beginTransaction()
                    .add(container.id, UsageTimerFragment(), "usageTimer")
                    .commitNow()
            }

            // 화면이 실제로 렌더링되었는지: 타이틀이 화면에 표시되고 텍스트가 일치
            onView(withId(R.id.title)).check(matches(isDisplayed()))
            onView(withId(R.id.title)).check(matches(withText("UsageTimer")))

            // 핵심 조작 View들이 노출(VISIBLE) 상태인지 검증 (스크롤 위치와 무관하게 검증하기 위해 effectiveVisibility 사용)
            onView(withId(R.id.show)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.pause_input)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.save_button)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.pause_button)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.cancel_button)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.accessibility_button)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.select_app_button)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

            // 일시정지 잔여 시간이 0이므로 일시정지 정보 영역은 숨겨져 있어야 함
            onView(withId(R.id.pause_info)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }
}
