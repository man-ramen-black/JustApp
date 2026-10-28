package com.black.app.ui.maintab.main.keepscreenon

import android.app.KeyguardManager
import android.content.ComponentName
import com.black.app.testutil.BaseUiTest
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * 빠른 설정 타일 클릭부터 화면 꺼짐 자동 해제까지 실제 발동 경로 검증 계측 테스트
 *
 * cmd statusbar로 타일 추가·클릭, 화면 시간 제한을 최소값(10초)으로 줄여 실제 화면 유지 여부 확인
 * 충전 중 화면 켜짐 유지(stay_on_while_plugged_in) 개발자 옵션은 테스트 중 비활성화 후 원복
 * 삼성 "화면을 보는 동안 켜진 상태 유지"(intelligent_sleep_mode)도 전면 카메라로 얼굴을 감지하면 화면을 켜 두므로 같은 방식으로 비활성화 후 원복
 *
 * 잠금 해제 상태에서 쓰는 기능이라 잠겨 있으면 각 테스트를 건너뜀
 * (잠금 중에는 시스템이 오버레이 창을 숨겨 FLAG_KEEP_SCREEN_ON 미적용, 보안 잠금은 테스트에서 해제 불가)
 * 화면을 끄는 테스트는 보안 잠금 기기를 잠가 이후 테스트를 건너뛰게 하므로 마지막 순번에 배치
 *
 * 실제 시간 대기가 필요하므로 가상 시간으로 delay를 건너뛰는 runTest 대신 runBlocking 사용
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KeepScreenOnTileUiTest : BaseUiTest() {

    private val tileComponent =
        ComponentName(context, KeepScreenOnTileService::class.java).flattenToString()
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    private var originalScreenOffTimeout = ""
    private var originalStayOnWhilePluggedIn = ""
    private var originalSmartStay = ""

    override fun setup() {
        // 오버레이 표시 권한 부여(shell uid 권한으로 직접 허용)
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        // 화면 시간 제한·충전 중 켜짐 유지 설정 백업 후 테스트 값 적용
        originalScreenOffTimeout = device.executeShellCommand("settings get system screen_off_timeout").trim()
        originalStayOnWhilePluggedIn = device.executeShellCommand("settings get global stay_on_while_plugged_in").trim()
        originalSmartStay = device.executeShellCommand("settings get system $SMART_STAY_SETTING").trim()
        device.executeShellCommand("settings put global stay_on_while_plugged_in 0")
        device.executeShellCommand("settings put system $SMART_STAY_SETTING 0")
        device.executeShellCommand("settings put system screen_off_timeout $SCREEN_OFF_TIMEOUT")
        // 빠른 설정창에 타일 추가
        device.executeShellCommand("cmd statusbar add-tile $tileComponent")
        wakeUpAndUnlock()
        // 동영상 재생 앱 등 전면 앱의 FLAG_KEEP_SCREEN_ON 창이 화면을 붙잡지 않도록 홈 화면으로 이동
        device.pressHome()
        // 같은 프로세스 싱글톤 비활성 상태로 시작
        instrumentation.runOnMainSync { KeepScreenOnGlobal.deactivate(context) }
    }

    override fun teardown() {
        // 이후 단계 실패 시에도 기기 설정 잔존 방지 목적의 설정 우선 복원
        restoreSetting("system", "screen_off_timeout", originalScreenOffTimeout)
        restoreSetting("global", "stay_on_while_plugged_in", originalStayOnWhilePluggedIn)
        restoreSetting("system", SMART_STAY_SETTING, originalSmartStay)
        instrumentation.runOnMainSync { KeepScreenOnGlobal.deactivate(context) }
        device.executeShellCommand("cmd statusbar remove-tile $tileComponent")
        wakeUpAndUnlock()
    }

    /** 원래 값이 없던 설정(null)은 삭제, 그 외 백업값으로 복원 */
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
     * Given: 잠금 해제 상태, 다른 화면 유지 창 없음, 타일 추가, 화면 시간 제한 10초, 비활성 상태
     * When: 타일 클릭 후 화면 시간 제한보다 오래 대기
     * Then: 활성 상태, 화면 켜짐 유지, dumpsys window 화면 유지 창이 KeepScreenOn 창
     */
    @Test
    fun test_01_clickTileKeepsScreenOnAfterTimeout() = runBlocking {
        /** Given **/
        assumeUnlocked()
        assertNoOtherScreenHolder()

        /** When **/
        clickTile()
        assertTrue("타일 클릭 후 활성화되지 않았습니다", waitUntil(STATE_TIMEOUT) { KeepScreenOnGlobal.isActive.value })
        // 켜짐 유지가 없었다면 화면이 꺼졌을 시간만큼 대기
        delay(SCREEN_OFF_TIMEOUT + TIMEOUT_MARGIN)

        /** Then **/
        assertTrue("화면 시간 제한 후 화면이 꺼졌습니다", device.isScreenOn)
        // 화면을 붙잡고 있는 창이 다른 앱이 아닌 이 기능의 오버레이 창인지 확인
        val holdScreenLine = holdScreenLine()
        assertTrue(
            "화면 유지 창이 KeepScreenOn 창이 아닙니다: $holdScreenLine",
            holdScreenLine.contains(KeepScreenOnGlobal.WINDOW_TITLE),
        )
    }

    /**
     * 타일 2회 클릭으로 해제 시 화면을 붙잡던 창이 없어지는지 검증
     * 화면 꺼짐까지 기다리면 보안 잠금이 걸려 다음 테스트가 건너뛰어지므로 화면 유지 창 해제로 판정
     *
     * Given: 잠금 해제 상태, 다른 화면 유지 창 없음, 타일 클릭으로 활성화, 화면 유지 창이 KeepScreenOn 창
     * When: 타일 재클릭
     * Then: 비활성 상태, dumpsys window 화면 유지 창 없음
     */
    @Test
    fun test_02_clickTileAgainReleasesScreenHold() = runBlocking {
        /** Given **/
        assumeUnlocked()
        assertNoOtherScreenHolder()
        clickTile()
        assertTrue("타일 클릭 후 활성화되지 않았습니다", waitUntil(STATE_TIMEOUT) { KeepScreenOnGlobal.isActive.value })
        // 해제 판정이 의미 있도록 해제 전에는 이 기능의 창이 화면을 붙잡고 있는지 확인
        val isHeldByKeepScreenOn = waitUntil(STATE_TIMEOUT) {
            holdScreenLine().contains(KeepScreenOnGlobal.WINDOW_TITLE)
        }
        assertTrue("화면 유지 창이 KeepScreenOn 창이 아닙니다: ${holdScreenLine()}", isHeldByKeepScreenOn)

        /** When **/
        clickTile()

        /** Then **/
        assertTrue("타일 재클릭 후 해제되지 않았습니다", waitUntil(STATE_TIMEOUT) { !KeepScreenOnGlobal.isActive.value })
        val isReleased = waitUntil(STATE_TIMEOUT) { hasNoScreenHolder() }
        assertTrue("해제 후에도 화면 유지 창이 남아 있습니다: ${holdScreenLine()}", isReleased)
    }

    /**
     * 활성 중 화면을 끄면 자동 해제되는지 검증
     *
     * Given: 잠금 해제 상태, 타일 클릭으로 활성화
     * When: 화면 끄기(전원 버튼과 동일한 sleep)
     * Then: 비활성 상태
     */
    @Test
    fun test_03_screenOffReleasesKeepScreenOn() = runBlocking {
        /** Given **/
        assumeUnlocked()
        clickTile()
        assertTrue("타일 클릭 후 활성화되지 않았습니다", waitUntil(STATE_TIMEOUT) { KeepScreenOnGlobal.isActive.value })

        /** When **/
        device.sleep()

        /** Then **/
        assertTrue("화면 꺼짐 후 해제되지 않았습니다", waitUntil(STATE_TIMEOUT) { !KeepScreenOnGlobal.isActive.value })
    }

    /**
     * 실제 사용 흐름(빠른 설정창 열기 → 타일 클릭 → 닫기)으로 타일 클릭
     *
     * 패널을 닫은 채 click-tile만 보내면 SystemUI 바인딩 요청 플래그가 남아 동결된 앱 프로세스로 클릭 미전달
     */
    private suspend fun clickTile() {
        device.executeShellCommand("cmd statusbar expand-settings")
        delay(PANEL_ANIMATION_DELAY)
        device.executeShellCommand("cmd statusbar click-tile $tileComponent")
        delay(PANEL_ANIMATION_DELAY)
        device.executeShellCommand("cmd statusbar collapse")
    }

    /** 화면 켜기 후 비보안 키가드 해제 */
    private fun wakeUpAndUnlock() {
        device.wakeUp()
        // 보안 잠금(PIN 등)은 해제되지 않고 PIN 입력 화면만 뜨므로 요청 생략
        if (!keyguardManager.isDeviceSecure) {
            device.executeShellCommand("wm dismiss-keyguard")
        }
    }

    /** 잠겨 있으면 검증 대상 상태가 아니므로 테스트 건너뜀, 비보안 키가드의 비동기 해제는 기다린 뒤 판정 */
    private suspend fun assumeUnlocked() {
        val unlocked = waitUntil(STATE_TIMEOUT) { !keyguardManager.isKeyguardLocked }
        assumeTrue("기기가 잠겨 있어 건너뜀(잠금 해제 상태에서 실행 필요)", unlocked)
    }

    /** 다른 창(재생 중인 동영상·PiP 등)이 화면을 붙잡고 있으면 켜짐 유지·해제 판정이 무의미하므로 사전 확인 */
    private suspend fun assertNoOtherScreenHolder() {
        assertTrue(
            "다른 창이 화면을 켜 두고 있습니다. 동영상 재생 등을 멈춘 뒤 실행해야 합니다: ${holdScreenLine()}",
            waitUntil(STATE_TIMEOUT) { hasNoScreenHolder() },
        )
    }

    /** 화면을 붙잡고 있는 창이 없는지 여부 */
    private fun hasNoScreenHolder(): Boolean = holdScreenLine().endsWith("$HOLD_SCREEN_FIELD=null")

    /** dumpsys window의 화면 유지 창 줄 조회 */
    private fun holdScreenLine(): String =
        device.executeShellCommand("dumpsys window")
            .lines()
            .firstOrNull { it.contains(HOLD_SCREEN_FIELD) }
            .orEmpty()
            .trim()

    /** [condition] 충족까지 [timeout] 동안 폴링 후 충족 여부 반환 */
    private suspend fun waitUntil(timeout: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            delay(POLL_INTERVAL)
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
        /** 삼성 "화면을 보는 동안 켜진 상태 유지" 설정 키(삼성 외 기기에는 없어 원복 시 삭제) */
        private const val SMART_STAY_SETTING = "intelligent_sleep_mode"
        /** dumpsys window 화면 유지 창 필드명 */
        private const val HOLD_SCREEN_FIELD = "mHoldScreenWindow"
    }
}
