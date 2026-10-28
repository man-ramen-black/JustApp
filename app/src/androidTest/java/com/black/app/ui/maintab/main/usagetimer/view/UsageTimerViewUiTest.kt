package com.black.app.ui.maintab.main.usagetimer.view

import android.app.KeyguardManager
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.Surface
import android.view.WindowInsets
import androidx.test.core.app.ActivityScenario
import com.black.app.R
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.HiltTestActivity
import com.black.core.util.Cutout
import com.black.core.util.OverlayViewUtil
import com.black.core.util.UiUtil
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [UsageTimerView] 오버레이 창의 첫 표시 위치와 드래그 이동 범위가 실제 화면에 맞는지 검증하는 계측 테스트
 *
 * 오버레이 창 x, y는 WindowManager가 창 속성(컷아웃 모드 등)에 따라 시스템 바를 피해 잡은 부모 프레임 기준이라 실제 기기 배치로만 확인 가능
 * 이동 가능 영역은 화면에서 보이는 시스템 바만 뺀 영역(컷아웃 영역 포함)
 * 회전은 자동 회전을 끈 상태의 user_rotation으로 고정하고 테스트 후 원복
 * 몰입 모드는 유튜브 전체화면처럼 상단바·내비게이션 바를 숨기고 컷아웃 영역까지 그리는 테스트 액티비티로 재현
 * 잠금 중에는 오버레이 창이 표시되지 않아 잠겨 있으면 건너뜀
 *
 * 실제 창 배치 대기가 필요하므로 가상 시간으로 delay를 건너뛰는 runTest 대신 runBlocking 사용
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerViewUiTest : BaseUiTest() {

    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    private var timerView: UsageTimerView? = null
    private var originalAccelerometerRotation = ""
    private var originalUserRotation = ""

    override fun setup() {
        // 오버레이 표시 권한 부여(shell uid 권한으로 직접 허용)
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        // 회전 설정 백업 후 자동 회전 해제(user_rotation 고정 회전 적용 조건)
        originalAccelerometerRotation = device.executeShellCommand("settings get system accelerometer_rotation").trim()
        originalUserRotation = device.executeShellCommand("settings get system user_rotation").trim()
        device.executeShellCommand("settings put system accelerometer_rotation 0")
    }

    override fun teardown() {
        // 오버레이 창 제거 후 회전 설정 원복
        instrumentation.runOnMainSync { timerView?.detachView() }
        timerView = null
        restoreSetting("user_rotation", originalUserRotation)
        restoreSetting("accelerometer_rotation", originalAccelerometerRotation)
    }

    /** 원래 값이 없던 설정(null)은 삭제, 그 외 백업값으로 복원 */
    private fun restoreSetting(key: String, value: String) {
        if (value.isEmpty() || value == "null") {
            device.executeShellCommand("settings delete system $key")
        } else {
            device.executeShellCommand("settings put system $key $value")
        }
    }

    /**
     * 처음 표시될 때 화면 세로 중앙 왼쪽 끝에 놓이는지 검증
     *
     * Given: 잠금 해제 상태, 세로 화면, 테스트 액티비티 표시
     * When: 타이머 창 추가
     * Then: 창 왼쪽이 이동 가능 영역 왼쪽 끝, 창 세로 중앙이 이동 가능 영역 세로 중앙(정수 나눗셈 오차 1px 허용)
     */
    @Test
    fun test_01_attachShowsAtLeftVerticalCenter() = runBlocking {
        /** Given **/
        assumeUnlocked()
        val movableArea = prepareScreen(Surface.ROTATION_0, isImmersive = false)

        /** When **/
        val timerRect = attachTimer()

        /** Then **/
        assertEquals("창 왼쪽 위치가 다릅니다: $timerRect, 이동 가능 영역 $movableArea", movableArea.left, timerRect.left)
        assertEquals(
            "창 세로 중앙 위치가 다릅니다: $timerRect, 이동 가능 영역 $movableArea",
            movableArea.exactCenterY(),
            timerRect.exactCenterY(),
            1f,
        )
    }

    /**
     * 상단바가 왼쪽인 가로 화면에서 끝까지 끌면 화면을 넘지 않고 왼쪽 컷아웃 영역까지 들어가는지 검증
     *
     * Given: 잠금 해제 상태, ROTATION_90 가로 화면(컷아웃 왼쪽), 타이머 창 표시
     * When: 화면 밖 오른쪽 아래, 왼쪽 위로 차례로 이동
     * Then: 각각 이동 가능 영역(컷아웃 포함) 오른쪽 아래, 왼쪽 위 가장자리에 정확히 맞춰짐
     */
    @Test
    fun test_02_moveToEdgesReachesScreenEdgesInLandscape() = runBlocking {
        assertMoveToEdgesReachesMovableArea(Surface.ROTATION_90, isImmersive = false)
    }

    /**
     * 상단바가 왼쪽인 가로 몰입 모드(유튜브 전체화면 등)에서 끝까지 끌면 화면을 넘지 않고 왼쪽 컷아웃 영역까지 들어가는지 검증
     *
     * Given: 잠금 해제 상태, ROTATION_90 가로 화면, 시스템 바를 숨긴 몰입 모드 액티비티, 타이머 창 표시
     * When: 화면 밖 오른쪽 아래, 왼쪽 위로 차례로 이동
     * Then: 각각 이동 가능 영역(컷아웃 포함) 오른쪽 아래, 왼쪽 위 가장자리에 정확히 맞춰짐
     */
    @Test
    fun test_03_moveToEdgesReachesScreenEdgesInImmersiveLandscape() = runBlocking {
        assertMoveToEdgesReachesMovableArea(Surface.ROTATION_90, isImmersive = true)
    }

    /**
     * 상단바가 오른쪽인 가로 몰입 모드에서 끝까지 끌면 화면을 넘지 않고 오른쪽 컷아웃 영역까지 들어가는지 검증
     *
     * Given: 잠금 해제 상태, ROTATION_270 가로 화면(컷아웃 오른쪽), 몰입 모드 액티비티, 타이머 창 표시
     * When: 화면 밖 오른쪽 아래, 왼쪽 위로 차례로 이동
     * Then: 각각 이동 가능 영역(컷아웃 포함) 오른쪽 아래, 왼쪽 위 가장자리에 정확히 맞춰짐
     */
    @Test
    fun test_04_moveToEdgesReachesScreenEdgesInImmersiveReverseLandscape() = runBlocking {
        assertMoveToEdgesReachesMovableArea(Surface.ROTATION_270, isImmersive = true)
    }

    /** test_02~04 공통: 지정 회전·몰입 모드에서 화면 밖 양 끝으로 이동 시 이동 가능 영역 가장자리에 맞춰지는지 확인 */
    private suspend fun assertMoveToEdgesReachesMovableArea(rotation: Int, isImmersive: Boolean) {
        /** Given **/
        assumeUnlocked()
        val movableArea = prepareScreen(rotation, isImmersive)
        attachTimer()

        /** When **/
        val bottomRightRect = moveTimer(OUTSIDE_POSITION, OUTSIDE_POSITION)
        val topLeftRect = moveTimer(-OUTSIDE_POSITION, -OUTSIDE_POSITION)

        /** Then **/
        assertEquals("오른쪽 끝이 이동 가능 영역과 다릅니다: $bottomRightRect, 이동 가능 영역 $movableArea", movableArea.right, bottomRightRect.right)
        assertEquals("아래쪽 끝이 이동 가능 영역과 다릅니다: $bottomRightRect, 이동 가능 영역 $movableArea", movableArea.bottom, bottomRightRect.bottom)
        assertEquals("왼쪽 끝이 이동 가능 영역과 다릅니다: $topLeftRect, 이동 가능 영역 $movableArea", movableArea.left, topLeftRect.left)
        assertEquals("위쪽 끝이 이동 가능 영역과 다릅니다: $topLeftRect, 이동 가능 영역 $movableArea", movableArea.top, topLeftRect.top)
    }

    /**
     * 회전 고정·테스트 액티비티 표시(몰입 모드 선택) 후 오버레이 창의 이동 가능 영역(화면 좌표) 반환
     * 이동 가능 영역은 화면에서 보이는 시스템 바만 뺀 영역(컷아웃 영역 포함)
     */
    private suspend fun prepareScreen(rotation: Int, isImmersive: Boolean): Rect {
        // 회전 고정 후 반영 대기
        device.executeShellCommand("settings put system user_rotation $rotation")
        assertTrue("화면이 회전되지 않았습니다: ${device.displayRotation}", waitUntil { device.displayRotation == rotation })

        // 테스트 액티비티 표시, 몰입 모드면 유튜브 전체화면처럼 시스템 바 숨김·컷아웃 영역 확장
        val scenario = launchHost()
        if (isImmersive) {
            scenario.onActivity {
                UiUtil.setCutout(it.window, Cutout.SHORT_EDGES)
                UiUtil.setImmersiveMode(it.window, true)
            }
        }

        // 몰입 모드 상단바 숨김 반영 대기
        assertTrue("몰입 모드 상단바 숨김이 반영되지 않았습니다", waitUntil { isStatusBarVisible(scenario.activity()) != isImmersive })
        delay(SETTLE_DELAY)

        // 화면 전체 영역에서 현재 보이는 시스템 바 인셋만 뺀 영역
        var movableArea = Rect()
        scenario.onActivity {
            val windowMetrics = it.windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
            val bounds = windowMetrics.bounds
            movableArea = Rect(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom,
            )
        }
        return movableArea
    }

    /** 타이머 창 추가 후 배치된 화면 좌표 영역 반환 */
    private suspend fun attachTimer(): Rect {
        instrumentation.runOnMainSync {
            timerView = UsageTimerView(ContextThemeWrapper(context, R.style.Theme_Black)).also { it.attachView() }
        }
        assertTrue("타이머 창이 배치되지 않았습니다", waitUntil { timerRect().width() > 0 })
        delay(SETTLE_DELAY)
        return timerRect()
    }

    /** 드래그 이동과 같은 경로로 타이머 창 이동 후 배치된 화면 좌표 영역 반환 */
    private suspend fun moveTimer(x: Float, y: Float): Rect {
        instrumentation.runOnMainSync { OverlayViewUtil.moveView(requireNotNull(timerView), x, y) }
        delay(SETTLE_DELAY)
        return timerRect()
    }

    /** 타이머 창의 화면 좌표 영역 */
    private fun timerRect(): Rect {
        val rect = Rect()
        instrumentation.runOnMainSync {
            val view = timerView ?: return@runOnMainSync
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            rect.set(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        return rect
    }

    /** 액티비티 창 기준 상단바 표시 여부 */
    private fun isStatusBarVisible(activity: HiltTestActivity): Boolean {
        var isVisible = true
        instrumentation.runOnMainSync {
            isVisible = activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) ?: true
        }
        return isVisible
    }

    /** scenario의 현재 액티비티 인스턴스 */
    private fun ActivityScenario<HiltTestActivity>.activity(): HiltTestActivity {
        var activity: HiltTestActivity? = null
        onActivity { activity = it }
        return requireNotNull(activity)
    }

    /** 잠겨 있으면 오버레이 창이 표시되지 않아 검증 대상 상태가 아니므로 테스트 건너뜀 */
    private fun assumeUnlocked() {
        assumeTrue("기기가 잠겨 있어 건너뜀(잠금 해제 상태에서 실행 필요)", !keyguardManager.isKeyguardLocked)
    }

    /** [condition] 충족까지 [STATE_TIMEOUT] 동안 폴링 후 충족 여부 반환 */
    private suspend fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + STATE_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            delay(POLL_INTERVAL)
        }
        return condition()
    }

    companion object {
        /** 화면 밖 좌표(화면 크기보다 충분히 큰 값) */
        private const val OUTSIDE_POSITION = 100_000f
        /** 회전·몰입 모드·창 배치 반영 대기 시간 */
        private const val STATE_TIMEOUT = 5_000L
        /** 창 이동 후 재배치 완료 대기 시간 */
        private const val SETTLE_DELAY = 700L
        private const val POLL_INTERVAL = 200L
    }
}
