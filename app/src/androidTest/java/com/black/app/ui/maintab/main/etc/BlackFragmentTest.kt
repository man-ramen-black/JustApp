package com.black.app.ui.maintab.main.etc

import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.HiltTestActivity
import com.black.app.testutil.assertBelowStatusBar
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [BlackFragment] 진입·이탈 시 immersive 모드 전환이 Window 상태를 올바르게 복원하는지 검증하는 Espresso UI 테스트
 *
 * 네비게이션을 사용하지 않으므로 NavController 없이 컨테이너에만 추가(패턴 B).
 * 레이아웃(fragment_black.xml)에 id 있는 View가 없어 View 노출 검증 대신,
 * 이탈 후 콘텐츠가 status bar와 겹치는 회귀(setDecorFitsSystemWindows 복원 누락) 검증
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BlackFragmentTest : BaseUiTest() {

    companion object {
        private const val TAG_BLACK_FRAGMENT = "black"
        private const val TIMEOUT_WINDOW_LAYOUT_MS = 5_000L
        private const val POLLING_INTERVAL_MS = 100L
    }

    private var containerId = 0

    /**
     * BlackFragment 이탈 후 콘텐츠가 status bar와 겹치지 않는지 검증
     *
     * Given:
     * - BlackFragment 호스팅(onResume에서 immersive 모드 진입, status bar 숨김)
     * When:
     * - BlackFragment 제거(onPause에서 immersive 모드 해제, status bar 복원)
     * Then:
     * - 콘텐츠 컨테이너 상단이 status bar 아래로 복원(겹침 없음)
     */
    @Test
    fun test_01_contentNotOverlappingStatusBarAfterExit() {
        /** Given **/
        val scenario = hostBlackFragment()
        waitUntilStatusBarVisibility(
            scenario,
            visible = false,
            failureMessage = "immersive 진입 후 status bar가 숨겨지지 않았습니다",
        )

        /** When **/
        removeBlackFragment(scenario)
        waitUntilStatusBarVisibility(
            scenario,
            visible = true,
            failureMessage = "BlackFragment 이탈 후 status bar가 다시 표시되지 않았습니다",
        )

        /** Then **/
        // Window 재배치(insets 적용)가 비동기이므로 복원 완료까지 대기 후 겹침 검증
        waitUntilContainerTop(
            scenario,
            "BlackFragment 이탈 후 컨테이너 상단이 status bar 아래로 복원되지 않았습니다",
        ) { top -> top > 0 }
        assertBelowStatusBar(containerId)
    }

    /**
     * BlackFragment를 HiltTestActivity에 호스팅
     *
     * 진입(onResume)·이탈(onPause) 모두 같은 Activity에서 검증해야 하므로
     * onHostActivity 대신 scenario를 직접 반환(teardown에서 자동 close)
     */
    private fun hostBlackFragment(): ActivityScenario<HiltTestActivity> {
        val scenario = launchHost()
        scenario.onActivity { activity ->
            val container = FrameLayout(activity).apply { id = View.generateViewId() }
            containerId = container.id
            activity.setContentView(container)

            activity.supportFragmentManager.beginTransaction()
                .add(container.id, BlackFragment(), TAG_BLACK_FRAGMENT)
                .commitNow()
        }
        return scenario
    }

    /** BlackFragment를 제거하여 onPause(immersive 모드 해제) 트리거 */
    private fun removeBlackFragment(scenario: ActivityScenario<HiltTestActivity>) {
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag(TAG_BLACK_FRAGMENT)
                ?: throw AssertionError("호스팅된 BlackFragment를 찾을 수 없습니다")
            activity.supportFragmentManager.beginTransaction()
                .remove(fragment)
                .commitNow()
        }
    }

    /**
     * status bar 표시 여부가 [visible]과 일치할 때까지 폴링 대기
     *
     * windowInsetsController의 hide/show가 비동기로 적용되므로,
     * 제한 시간 내 일치하지 않으면 [failureMessage]로 실패 처리
     */
    private fun waitUntilStatusBarVisibility(
        scenario: ActivityScenario<HiltTestActivity>,
        visible: Boolean,
        failureMessage: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_WINDOW_LAYOUT_MS
        var lastVisible: Boolean? = null
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                lastVisible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.statusBars())
            }
            if (lastVisible == visible) {
                return
            }
            SystemClock.sleep(POLLING_INTERVAL_MS)
        }
        throw AssertionError("$failureMessage (마지막 status bar 표시 여부=$lastVisible)")
    }

    /**
     * 컨테이너 상단 화면 y 좌표가 [condition]을 충족할 때까지 폴링 대기
     *
     * immersive 모드 전환에 따른 Window 재배치가 비동기로 일어나므로,
     * 제한 시간 내 충족되지 않으면 [failureMessage]로 실패 처리
     */
    private fun waitUntilContainerTop(
        scenario: ActivityScenario<HiltTestActivity>,
        failureMessage: String,
        condition: (Int) -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_WINDOW_LAYOUT_MS
        var lastTop = Int.MIN_VALUE
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val location = IntArray(2)
                activity.findViewById<View>(containerId).getLocationOnScreen(location)
                lastTop = location[1]
            }
            if (condition(lastTop)) {
                return
            }
            SystemClock.sleep(POLLING_INTERVAL_MS)
        }
        throw AssertionError("$failureMessage (마지막 컨테이너 상단 y=$lastTop)")
    }
}
