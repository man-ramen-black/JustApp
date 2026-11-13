package com.black.app.ui.main.usagetimer

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.black.app.R
import com.black.app.ui.main.OverlayTestFixture
import com.black.app.ui.main.usagetimer.UsageTimerGlobal.TimerAction
import com.black.app.ui.main.usagetimer.view.UsageTimerView
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageTimerGlobal] 타이머 동작·세션 시작·닫기 유지 판정과 타이머 오버레이 닫기 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerGlobalTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** 각 테스트 후 남은 타이머 오버레이와 세션·닫기·지연 숨김 상태 초기화(싱글턴 상태 격리) */
    @After
    fun resetTimerState() {
        UsageTimerGlobal.reset()
        OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).forEach { it.detachView() }
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
     * 닫은 앱에 머무는 중이면(떠난 시각 없음) 닫기 유지
     * 닫기 후 같은 앱 내 activity 전환 시 타이머가 재노출되지 않음
     *
     * Given: 요청 앱과 닫은 앱이 같고 떠난 시각 없음
     * When: isDismissedOnShow 호출
     * Then: true
     */
    @Test
    fun test_13_keepDismissedWhileStaying() = runTest {
        /** Given / When / Then **/
        assertTrue(UsageTimerGlobal.isDismissedOnShow("com.a", "com.a", null, 100_000L))
    }

    /**
     * 닫은 앱을 떠났다가 유예 안에 돌아오면 닫기 유지(공유 시트·알림창 같은 일시 창 전환)
     *
     * Given: 5초 전에 닫은 앱을 떠남
     * When: 같은 앱으로 isDismissedOnShow 호출
     * Then: true
     */
    @Test
    fun test_14_keepDismissedWhenReturningWithinGrace() = runTest {
        /** Given / When / Then **/
        assertTrue(UsageTimerGlobal.isDismissedOnShow("com.a", "com.a", 95_000L, 100_000L))
    }

    /**
     * 닫은 앱을 떠났다가 유예를 넘겨 돌아오면 닫기 해제
     *
     * Given: 유예(10초)보다 1초 더 전에 닫은 앱을 떠남
     * When: 같은 앱으로 isDismissedOnShow 호출
     * Then: false
     */
    @Test
    fun test_15_releaseDismissedAfterGrace() = runTest {
        /** Given **/
        val leftAt = 100_000L - UsageTimerGlobal.SESSION_GRACE_MILLIS - 1_000L

        /** When / Then **/
        assertFalse(UsageTimerGlobal.isDismissedOnShow("com.a", "com.a", leftAt, 100_000L))
    }

    /**
     * 닫은 앱이 아닌 앱의 표시 요청이면 닫기 해제, 닫은 앱이 없으면 표시
     *
     * Given: 닫은 앱 com.a, 요청 앱 com.b / 닫은 앱 없음
     * When: isDismissedOnShow 호출
     * Then: 모두 false
     */
    @Test
    fun test_16_notDismissedForOtherAppOrNone() = runTest {
        /** Given / When / Then **/
        assertFalse(UsageTimerGlobal.isDismissedOnShow("com.b", "com.a", null, 100_000L))
        assertFalse(UsageTimerGlobal.isDismissedOnShow("com.a", null, null, 100_000L))
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
        assertTrue(OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).isEmpty())
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
        val trackedView = OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).single()
        val previewView = attachPreviewView()

        /** When **/
        clickClose(previewView)
        val remainingViews = OverlayTestFixture.attachedViewsOf<UsageTimerView>(context)
        UsageTimerGlobal.detachView()

        /** Then **/
        assertEquals(listOf(trackedView), remainingViews)
        assertTrue(OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).isEmpty())
    }

    /**
     * 앱 화면 미리보기는 여러 번 요청해도 하나만 붙고, 일시정지 경로의 detachAllViews로 함께 제거되는지 검증
     *
     * Given: 타이머 오버레이 없음
     * When: showPreview를 두 번 호출한 뒤 detachAllViews 호출
     * Then: 미리보기 호출 뒤 오버레이 1개, detachAllViews 뒤 0개
     */
    @Test
    fun test_19_previewAttachesOnceAndDetachesWithTimer() = runTest {
        /** Given **/
        // 남은 오버레이 없음(@After 정리)

        /** When **/
        UsageTimerGlobal.showPreview(context)
        UsageTimerGlobal.showPreview(context)
        idleMainLooper()
        val previewViews = OverlayTestFixture.attachedViewsOf<UsageTimerView>(context)
        UsageTimerGlobal.detachAllViews()

        /** Then **/
        assertEquals(1, previewViews.size)
        assertTrue(OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).isEmpty())
    }

    /**
     * reset이 떠 있는 타이머와 닫기 상태를 모두 지워 같은 앱도 다시 표시되는지 검증(접근성 서비스 종료 경로)
     *
     * Given: 선택 앱 타이머를 띄운 뒤 닫기 버튼으로 닫음
     * When: reset 호출 후 같은 앱으로 다시 표시 요청
     * Then: reset 직후 오버레이 0개, 다시 표시 요청 뒤 1개
     */
    @Test
    fun test_20_resetClearsViewsAndDismissedState() = runTest {
        /** Given **/
        UsageTimerGlobal.showForApp(context, "com.example.app")
        // 대기 중인 바인딩을 실행해 닫기 버튼 클릭 리스너 연결
        idleMainLooper()
        clickClose(OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).single())

        /** When **/
        UsageTimerGlobal.reset()
        val afterReset = OverlayTestFixture.attachedViewsOf<UsageTimerView>(context)
        UsageTimerGlobal.showForApp(context, "com.example.app")
        idleMainLooper()

        /** Then **/
        assertTrue(afterReset.isEmpty())
        assertEquals(1, OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).size)
    }

    /**
     * 활성 창 확정 시 닫은 앱을 떠난 시각 기록 규칙 검증
     *
     * Given: 닫은 앱 com.a
     * When: 활성 앱이 com.a, 처음 다른 앱, 이미 떠난 뒤 다른 앱, 불명일 때 resolveDismissedLeftAt 호출
     * Then: 머무는 중 null, 처음 떠나면 지금, 이미 떠났으면 처음 시각 유지, 불명이면 현재 값 유지
     */
    @Test
    fun test_21_resolveDismissedLeftAt() = runTest {
        /** Given **/
        val now = 100_000L

        /** When / Then **/
        assertNull(UsageTimerGlobal.resolveDismissedLeftAt("com.a", "com.a", 90_000L, now))
        assertEquals(now, UsageTimerGlobal.resolveDismissedLeftAt("android", "com.a", null, now))
        assertEquals(90_000L, UsageTimerGlobal.resolveDismissedLeftAt("com.android.systemui", "com.a", 90_000L, now))
        assertEquals(90_000L, UsageTimerGlobal.resolveDismissedLeftAt(null, "com.a", 90_000L, now))
        assertNull(UsageTimerGlobal.resolveDismissedLeftAt("android", null, null, now))
    }

    /**
     * 닫은 뒤 알림창 같은 다른 창이 활성으로 확정됐다가 곧 돌아와도 타이머가 다시 뜨지 않는지 검증
     *
     * Given: 선택 앱 타이머를 띄운 뒤 닫기 버튼으로 닫음
     * When: 시스템 UI가 활성인 숨김 예약이 디바운스를 통과한 뒤 같은 앱으로 다시 표시 요청
     * Then: 타이머 오버레이 0개
     */
    @Test
    fun test_22_transientWindowKeepsDismissed() = runTest {
        /** Given **/
        UsageTimerGlobal.showForApp(context, "com.example.app")
        // 대기 중인 바인딩을 실행해 닫기 버튼 클릭 리스너 연결
        idleMainLooper()
        clickClose(OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).single())

        /** When **/
        UsageTimerGlobal.scheduleHide("com.android.systemui")
        waitCoroutine()
        UsageTimerGlobal.showForApp(context, "com.example.app")
        idleMainLooper()

        /** Then **/
        assertTrue(OverlayTestFixture.attachedViewsOf<UsageTimerView>(context).isEmpty())
    }

    /** 앱 화면 SHOW 경로처럼 UsageTimerGlobal을 거치지 않고 타이머 오버레이 표시 */
    private fun attachPreviewView(): UsageTimerView =
        UsageTimerView(ContextThemeWrapper(context, R.style.Theme_Black)).also {
            it.attachView()
            idleMainLooper()
        }

    /** 타이머 오버레이의 닫기 버튼 클릭 */
    private fun clickClose(view: UsageTimerView) {
        view.findViewById<View>(R.id.close).performClick()
        idleMainLooper()
    }
}
