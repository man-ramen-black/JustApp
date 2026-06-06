package com.black.app.ui.maintab.main.studypopup

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.black.app.R
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.assertBelowStatusBar
import com.black.app.testutil.assertDisplayed
import com.black.app.testutil.assertText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [StudyPopupFragment]가 계측 환경에서 정상 동작하고 상단바와 겹치지 않는지 검증하는 Espresso UI 테스트
 *
 * 네비게이션을 사용하지 않으므로 NavController 없이 컨테이너에만 추가(패턴 B).
 * 상단바 겹침 검증을 위해 콘텐츠가 status bar 뒤까지 그려지는 edge-to-edge 환경으로 호스팅
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupFragmentTest : BaseUiTest() {

    /**
     * StudyPopupFragment가 계측 환경에서 핵심 View를 정상 노출하는지 검증
     *
     * Given:
     * - StudyPopupFragment 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - AttachView 버튼 노출 + 라벨 일치
     * - 목록 RecyclerView 노출
     */
    @Test
    fun test_01_studyPopupFragmentDisplaysCoreViews() {
        /** Given **/
        hostStudyPopupFragment()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertDisplayed(R.id.attach_view_button)
        assertText(R.id.attach_view_button, "AttachView")
        assertDisplayed(R.id.recycler_view)
    }

    /**
     * 최상단 콘텐츠(AttachView 버튼)가 status bar 영역과 겹치지 않는지 검증
     *
     * Given:
     * - 콘텐츠가 status bar 뒤까지 그려지는 edge-to-edge 환경으로 StudyPopupFragment 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - AttachView 버튼 상단이 status bar 높이 아래에 위치(fitsStatusBar 적용 효과)
     */
    @Test
    fun test_02_topContentNotOverlappingStatusBar() {
        /** Given **/
        hostStudyPopupFragment()

        /** When **/
        // fitsStatusBar로 적용된 top padding 효과만 검증(별도 조작 없음)

        /** Then **/
        assertBelowStatusBar(R.id.attach_view_button)
    }

    /**
     * StudyPopupFragment를 status bar 뒤까지 그려지는 edge-to-edge 환경으로 호스팅
     *
     * 네비게이션을 사용하지 않으므로 NavController 부착 없이 컨테이너에만 추가하고,
     * fitsStatusBar 리스너가 status bar inset을 받도록 inset 재적용 요청
     */
    private fun hostStudyPopupFragment() {
        onHostActivity { activity ->
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val container = FrameLayout(activity).apply { id = View.generateViewId() }
            activity.setContentView(container)

            activity.supportFragmentManager.beginTransaction()
                .add(container.id, StudyPopupFragment(), "studyPopup")
                .commitNow()

            ViewCompat.requestApplyInsets(container)
        }
    }
}
