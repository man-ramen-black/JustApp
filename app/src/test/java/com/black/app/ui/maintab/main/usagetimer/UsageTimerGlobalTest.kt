package com.black.app.ui.maintab.main.usagetimer

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.black.app.R
import com.black.app.ui.maintab.main.usagetimer.UsageTimerGlobal.TimerAction
import com.black.app.ui.maintab.main.usagetimer.view.UsageTimerView
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

/** [UsageTimerGlobal] 타이머 동작·세션 시작·닫기 유지 판정과 타이머 오버레이 닫기 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerGlobalTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** 각 테스트 후 남은 타이머 오버레이 제거(싱글턴 상태 격리) */
    @After
    fun detachTimerViews() {
        UsageTimerGlobal.detachView()
        attachedTimerViews().forEach { it.detachView() }
    }

    /**
     * 활성 앱이 선택 앱이고 일시정지가 아니면 표시
     *
     * Given: 활성 앱이 선택 앱, 일시정지 아님
     * When: resolveTimerAction 호출
     * Then: SHOW
     */
    @Test
    fun test_01_showWhenActiveSelectedAndNotPaused() = runTest {
        /** Given / When / Then **/
        assertEquals(
            TimerAction.SHOW,
            UsageTimerGlobal.resolveTimerAction("com.a", listOf("com.a", "com.b"), false),
        )
    }

    /**
     * 일시정지 상태면 활성 앱이 선택 앱이라도 숨김
     *
     * Given: 활성 앱이 선택 앱, 일시정지 상태
     * When: resolveTimerAction 호출
     * Then: HIDE
     */
    @Test
    fun test_02_hideWhenSelectedButPaused() = runTest {
        /** Given / When / Then **/
        assertEquals(
            TimerAction.HIDE,
            UsageTimerGlobal.resolveTimerAction("com.a", listOf("com.a"), true),
        )
    }

    /**
     * 활성 앱이 선택 목록에 없으면 숨김
     * 다른 앱 위에서 선택 앱의 늦은 창 이벤트가 도착해도 활성 앱 기준이라 오재노출되지 않음
     *
     * Given: 활성 앱이 비선택 앱
     * When: resolveTimerAction 호출
     * Then: HIDE
     */
    @Test
    fun test_03_hideWhenActiveNotSelected() = runTest {
        /** Given / When / Then **/
        assertEquals(
            TimerAction.HIDE,
            UsageTimerGlobal.resolveTimerAction("com.c", listOf("com.a", "com.b"), false),
        )
    }

    /**
     * 활성 앱을 알 수 없으면(전환 순간 노드 미준비) 현재 상태 유지
     *
     * Given: activePackage null
     * When: resolveTimerAction 호출
     * Then: IGNORE (타이머가 즉시 사라지지 않음)
     */
    @Test
    fun test_04_ignoreWhenActiveNull() = runTest {
        /** Given / When / Then **/
        assertEquals(
            TimerAction.IGNORE,
            UsageTimerGlobal.resolveTimerAction(null, listOf("com.a"), false),
        )
    }

    /**
     * 선택 목록이 비어있으면 숨김
     *
     * Given: 선택 목록 비어있음
     * When: resolveTimerAction 호출
     * Then: HIDE
     */
    @Test
    fun test_05_hideWhenSelectedAppsEmpty() = runTest {
        /** Given / When / Then **/
        assertEquals(
            TimerAction.HIDE,
            UsageTimerGlobal.resolveTimerAction("com.a", emptyList(), false),
        )
    }

    /**
     * 진행 중 세션이 없으면 현재 시각으로 새 세션 시작
     *
     * Given: 세션 패키지·시작 시각 없음
     * When: resolveSessionStart 호출
     * Then: 현재 시각 반환
     */
    @Test
    fun test_08_newSessionWhenNoSession() = runTest {
        /** Given / When / Then **/
        assertEquals(
            5_000L,
            UsageTimerGlobal.resolveSessionStart("com.a", null, null, null, 5_000L),
        )
    }

    /**
     * 같은 앱이 표시 중(숨김 이력 없음)이면 기존 세션 유지
     *
     * Given: 같은 앱 세션, 숨김 시각 없음
     * When: resolveSessionStart 호출
     * Then: 기존 시작 시각 반환
     */
    @Test
    fun test_09_keepSessionWhenSameAppStillShown() = runTest {
        /** Given / When / Then **/
        assertEquals(
            1_000L,
            UsageTimerGlobal.resolveSessionStart("com.a", "com.a", 1_000L, null, 5_000L),
        )
    }

    /**
     * 같은 앱이 숨김 후 유예 내 복귀하면 기존 세션 유지
     * 권한 다이얼로그·공유 시트 등 일시적 창 전환으로 인한 타이머 재시작 방지
     *
     * Given: 같은 앱 세션, 숨김 후 유예 이내 경과
     * When: resolveSessionStart 호출
     * Then: 기존 시작 시각 반환
     */
    @Test
    fun test_10_keepSessionWhenSameAppReturnsWithinGrace() = runTest {
        /** Given **/
        val hiddenAt = 5_000L
        val now = hiddenAt + UsageTimerGlobal.SESSION_GRACE_MILLIS

        /** When / Then **/
        assertEquals(
            1_000L,
            UsageTimerGlobal.resolveSessionStart("com.a", "com.a", 1_000L, hiddenAt, now),
        )
    }

    /**
     * 같은 앱이라도 유예를 초과해 복귀하면 새 세션 시작
     *
     * Given: 같은 앱 세션, 숨김 후 유예 초과 경과
     * When: resolveSessionStart 호출
     * Then: 현재 시각 반환
     */
    @Test
    fun test_11_newSessionWhenSameAppReturnsAfterGrace() = runTest {
        /** Given **/
        val hiddenAt = 5_000L
        val now = hiddenAt + UsageTimerGlobal.SESSION_GRACE_MILLIS + 1

        /** When / Then **/
        assertEquals(
            now,
            UsageTimerGlobal.resolveSessionStart("com.a", "com.a", 1_000L, hiddenAt, now),
        )
    }

    /**
     * 다른 앱으로 표시 요청이 오면 새 세션 시작
     *
     * Given: 다른 앱 세션 진행 중
     * When: resolveSessionStart 호출
     * Then: 현재 시각 반환
     */
    @Test
    fun test_12_newSessionWhenDifferentApp() = runTest {
        /** Given / When / Then **/
        assertEquals(
            5_000L,
            UsageTimerGlobal.resolveSessionStart("com.b", "com.a", 1_000L, null, 5_000L),
        )
    }

    /**
     * 닫은 앱이 그대로 활성이면 닫기 유지
     * 닫기 후 같은 앱 내 activity 전환 시 타이머가 재노출되지 않음
     *
     * Given: 활성 앱과 닫은 앱이 동일
     * When: resolveDismissedPackage 호출
     * Then: 닫은 앱 유지
     */
    @Test
    fun test_13_keepDismissedWhenSameAppActive() = runTest {
        /** Given / When / Then **/
        assertEquals(
            "com.a",
            UsageTimerGlobal.resolveDismissedPackage("com.a", "com.a"),
        )
    }

    /**
     * 다른 앱이 활성으로 확정되면 닫기 해제
     * 다른 앱에 갔다가 닫은 앱으로 복귀하면 타이머가 다시 노출됨
     *
     * Given: 활성 앱과 닫은 앱이 다름
     * When: resolveDismissedPackage 호출
     * Then: null (닫기 해제)
     */
    @Test
    fun test_14_clearDismissedWhenOtherAppActive() = runTest {
        /** Given / When / Then **/
        assertEquals(
            null,
            UsageTimerGlobal.resolveDismissedPackage("com.b", "com.a"),
        )
    }

    /**
     * 활성 앱을 알 수 없으면(전환 순간 노드 미준비) 닫기 유지
     *
     * Given: activePackage null, 닫은 앱 있음
     * When: resolveDismissedPackage 호출
     * Then: 닫은 앱 유지
     */
    @Test
    fun test_15_keepDismissedWhenActiveUnknown() = runTest {
        /** Given / When / Then **/
        assertEquals(
            "com.a",
            UsageTimerGlobal.resolveDismissedPackage(null, "com.a"),
        )
    }

    /**
     * 닫은 앱이 없으면 결과도 없음
     *
     * Given: 닫은 앱 없음
     * When: resolveDismissedPackage 호출
     * Then: null
     */
    @Test
    fun test_16_nullWhenNoDismissed() = runTest {
        /** Given / When / Then **/
        assertEquals(
            null,
            UsageTimerGlobal.resolveDismissedPackage("com.a", null),
        )
    }

    /**
     * 앱 화면 SHOW로 띄운 타이머의 닫기 버튼이 그 타이머를 제거하는지 검증
     *
     * Given: UsageTimerGlobal이 추적하지 않는 타이머 오버레이 표시
     * When: 닫기 버튼 클릭
     * Then: 타이머 오버레이 제거
     */
    @Test
    fun test_17_closeButtonDetachesUntrackedTimer() = runTest {
        /** Given **/
        val previewView = attachPreviewView()

        /** When **/
        clickClose(previewView)

        /** Then **/
        assertTrue(attachedTimerViews().isEmpty())
    }

    /**
     * SHOW 타이머를 닫아도 선택 앱 타이머 추적이 유지되는지 검증
     *
     * Given: 선택 앱 타이머(showForApp)와 SHOW 타이머 표시
     * When: SHOW 타이머 닫기 버튼 클릭 후 UsageTimerGlobal.detachView 호출
     * Then: 닫기 직후 선택 앱 타이머만 남고, detachView로 선택 앱 타이머도 제거
     */
    @Test
    fun test_18_closingUntrackedTimerKeepsTrackedTimer() = runTest {
        /** Given **/
        UsageTimerGlobal.showForApp(context, "com.example.app")
        val trackedView = attachedTimerViews().single()
        val previewView = attachPreviewView()

        /** When **/
        clickClose(previewView)
        val remainingViews = attachedTimerViews()
        UsageTimerGlobal.detachView()

        /** Then **/
        assertEquals(listOf(trackedView), remainingViews)
        assertTrue(attachedTimerViews().isEmpty())
    }

    /** 앱 화면 SHOW 경로처럼 UsageTimerGlobal을 거치지 않고 타이머 오버레이 표시 */
    private fun attachPreviewView(): UsageTimerView =
        UsageTimerView(ContextThemeWrapper(context, R.style.Theme_Black)).also {
            it.attachView()
            shadowOf(Looper.getMainLooper()).idle()
        }

    /** 타이머 오버레이의 닫기 버튼 클릭 */
    private fun clickClose(view: UsageTimerView) {
        view.findViewById<View>(R.id.close).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** WindowManager에 추가된 타이머 오버레이 목록 조회 */
    private fun attachedTimerViews(): List<UsageTimerView> =
        Shadow.extract<ShadowWindowManagerImpl>(windowManager).views.filterIsInstance<UsageTimerView>()
}
