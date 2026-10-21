package com.black.app.ui.maintab.main.keepscreenon

import android.content.ComponentName
import com.black.app.testutil.BaseUiTest
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * 빠른 설정 타일 클릭부터 화면 꺼짐 자동 해제까지 실제 발동 경로 검증 계측 테스트
 *
 * cmd statusbar로 타일 추가·클릭, 화면 시간 제한을 최소값(10초)으로 줄여 실제 화면 유지 여부 확인
 * 충전 중 화면 켜짐 유지(stay_on_while_plugged_in) 개발자 옵션은 테스트 중 비활성화 후 원복
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KeepScreenOnTileUiTest : BaseUiTest() {

    private val tileComponent =
        ComponentName(context, KeepScreenOnTileService::class.java).flattenToString()

    private var originalScreenOffTimeout = ""
    private var originalStayOnWhilePluggedIn = ""

    override fun setup() {
        // 오버레이 표시 권한 부여(shell uid 권한으로 직접 허용)
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        // 화면 시간 제한·충전 중 켜짐 유지 설정 백업 후 테스트 값 적용
        originalScreenOffTimeout = device.executeShellCommand("settings get system screen_off_timeout").trim()
        originalStayOnWhilePluggedIn = device.executeShellCommand("settings get global stay_on_while_plugged_in").trim()
        device.executeShellCommand("settings put global stay_on_while_plugged_in 0")
        device.executeShellCommand("settings put system screen_off_timeout $SCREEN_OFF_TIMEOUT")
        // 빠른 설정창에 타일 추가
        device.executeShellCommand("cmd statusbar add-tile $tileComponent")
        wakeUpAndUnlock()
        // 같은 프로세스 싱글톤 비활성 상태로 시작
        instrumentation.runOnMainSync { KeepScreenOnGlobal.deactivate(context) }
    }

    override fun teardown() {
        // 이후 단계 실패 시에도 기기 설정 잔존 방지 목적의 설정 우선 복원
        restoreSetting("system", "screen_off_timeout", originalScreenOffTimeout)
        restoreSetting("global", "stay_on_while_plugged_in", originalStayOnWhilePluggedIn)
        instrumentation.runOnMainSync { KeepScreenOnGlobal.deactivate(context) }
        device.executeShellCommand("cmd statusbar remove-tile $tileComponent")
        wakeUpAndUnlock()
    }

    /**
     * 원래 값이 없던 설정(null)은 삭제, 그 외 백업값으로 복원
     */
    private fun restoreSetting(namespace: String, key: String, value: String) {
        if (value.isEmpty() || value == "null") {
            device.executeShellCommand("settings delete $namespace $key")
        } else {
            device.executeShellCommand("settings put $namespace $key $value")
        }
    }

    /**
     * 타일 클릭 후 화면 시간 제한이 지나도 화면이 켜진 채 유지되는지 검증
     *
     * Given: 타일 추가, 화면 시간 제한 10초, 비활성 상태
     * When: 타일 클릭 후 화면 시간 제한보다 오래 대기
     * Then: 활성 상태, 화면 켜짐 유지, dumpsys window 화면 유지 창이 KeepScreenOn 창
     */
    @Test
    fun test_01_clickTileKeepsScreenOnAfterTimeout() {
        /** Given **/
        // setup에서 타일 추가·화면 시간 제한 적용 완료

        /** When **/
        clickTile()
        assertTrue("타일 클릭 후 활성화되지 않았습니다", waitUntil(STATE_TIMEOUT) { KeepScreenOnGlobal.isActive.value })
        Thread.sleep(SCREEN_OFF_TIMEOUT + TIMEOUT_MARGIN)

        /** Then **/
        assertTrue("화면 시간 제한 후 화면이 꺼졌습니다", device.isScreenOn)
        val holdScreenLine = device.executeShellCommand("dumpsys window")
            .lines()
            .firstOrNull { it.contains(HOLD_SCREEN_FIELD) }
            .orEmpty()
        assertTrue(
            "화면 유지 창이 KeepScreenOn 창이 아닙니다: $holdScreenLine",
            holdScreenLine.contains(KeepScreenOnGlobal.WINDOW_TITLE),
        )
    }

    /**
     * 활성 중 화면을 끄면 자동 해제되는지 검증
     *
     * Given: 타일 클릭으로 활성화
     * When: 화면 끄기(전원 버튼과 동일한 sleep)
     * Then: 비활성 상태
     */
    @Test
    fun test_02_screenOffReleasesKeepScreenOn() {
        /** Given **/
        clickTile()
        assertTrue("타일 클릭 후 활성화되지 않았습니다", waitUntil(STATE_TIMEOUT) { KeepScreenOnGlobal.isActive.value })

        /** When **/
        device.sleep()

        /** Then **/
        assertTrue("화면 꺼짐 후 해제되지 않았습니다", waitUntil(STATE_TIMEOUT) { !KeepScreenOnGlobal.isActive.value })
    }

    /**
     * 타일 2회 클릭으로 해제 후 화면 시간 제한이 지나면 화면이 꺼지는지 검증
     *
     * Given: 타일 클릭으로 활성화
     * When: 타일 재클릭 후 화면 시간 제한보다 오래 대기
     * Then: 비활성 상태, 화면 꺼짐
     */
    @Test
    fun test_03_clickTileAgainReleasesScreenTimeout() {
        /** Given **/
        clickTile()
        assertTrue("타일 클릭 후 활성화되지 않았습니다", waitUntil(STATE_TIMEOUT) { KeepScreenOnGlobal.isActive.value })

        /** When **/
        clickTile()

        /** Then **/
        assertTrue("타일 재클릭 후 해제되지 않았습니다", waitUntil(STATE_TIMEOUT) { !KeepScreenOnGlobal.isActive.value })
        assertTrue(
            "해제 후 화면 시간 제한이 지나도 화면이 꺼지지 않았습니다",
            waitUntil(SCREEN_OFF_TIMEOUT + TIMEOUT_MARGIN) { !device.isScreenOn },
        )
        assertFalse(KeepScreenOnGlobal.isActive.value)
    }

    /**
     * 실제 사용 흐름(빠른 설정창 열기 → 타일 클릭 → 닫기)으로 타일 클릭
     *
     * 패널을 닫은 채 click-tile만 보내면 SystemUI 바인딩 요청 플래그가 남아 동결된 앱 프로세스로 클릭 미전달
     */
    private fun clickTile() {
        device.executeShellCommand("cmd statusbar expand-settings")
        Thread.sleep(PANEL_ANIMATION_DELAY)
        device.executeShellCommand("cmd statusbar click-tile $tileComponent")
        Thread.sleep(PANEL_ANIMATION_DELAY)
        device.executeShellCommand("cmd statusbar collapse")
    }

    private fun wakeUpAndUnlock() {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
    }

    /**
     * [condition] 충족까지 [timeout] 동안 폴링 후 충족 여부 반환
     */
    private fun waitUntil(timeout: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            Thread.sleep(POLL_INTERVAL)
        }
        return condition()
    }

    companion object {
        /** 화면 시간 제한 테스트 값(시스템 최소값) */
        private const val SCREEN_OFF_TIMEOUT = 10_000L
        /** 화면 시간 제한 경과 판정 여유 시간 */
        private const val TIMEOUT_MARGIN = 5_000L
        /** 활성 상태 전환 대기 시간 */
        private const val STATE_TIMEOUT = 5_000L
        private const val POLL_INTERVAL = 200L
        /** 빠른 설정창 펼침·접힘 및 타일 서비스 바인딩 대기 시간 */
        private const val PANEL_ANIMATION_DELAY = 1_500L
        /** dumpsys window 화면 유지 창 필드명 */
        private const val HOLD_SCREEN_FIELD = "mHoldScreenWindow"
    }
}
