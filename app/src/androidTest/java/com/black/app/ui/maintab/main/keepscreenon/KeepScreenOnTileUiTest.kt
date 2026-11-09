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
 * 사용자가 테스트 전에 추가해 둔 타일과 화면 켜짐 유지 활성 여부는 테스트 후 그 상태로 되돌림
 * 같은 기기의 매일 쓰는 debug 앱은 다른 패키지라 계측 중에도 켜짐 유지 창이 남고 창 제목이 같아 화면 유지 판정을 흐리므로,
 * 테스트 동안 그 앱의 켜짐 유지를 끄고 켜져 있었으면 테스트 후 다시 켬
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

    private val tileComponentName = ComponentName(context, KeepScreenOnTileService::class.java)
    private val tileComponent = tileComponentName.flattenToString()
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    /** 테스트 전 사용자가 빠른 설정창에 이 타일을 추가해 뒀는지 여부, setup에서 확인하기 전에는 null */
    private var wasTileAdded: Boolean? = null

    /** 테스트 전 화면 켜짐 유지 활성 여부, setup에서 확인하기 전에는 null */
    private var wasKeepScreenOnActive: Boolean? = null

    /**
     * 같은 기기의 매일 쓰는 debug 앱 패키지
     *
     * 계측 테스트는 applicationIdSuffix(.uitest)가 붙은 uitest 빌드 타입에서 돌아 이 앱 패키지에서 suffix를 떼면 매일 쓰는 앱 패키지
     */
    private val dailyPackageName = context.packageName.removeSuffix(UITEST_PACKAGE_SUFFIX)

    /** setup에서 매일 쓰는 앱의 화면 켜짐 유지를 껐는지 여부(teardown에서 다시 켬) */
    private var isDailyKeepScreenOnDeactivated = false

    override fun setup() {
        // 오버레이 표시 권한 부여(shell uid 권한으로 직접 허용, teardown 뒤 BaseUiTest가 테스트 전 모드로 복원)
        setAppOpMode("SYSTEM_ALERT_WINDOW", "allow")
        // 화면 시간 제한·충전 중 켜짐 유지 설정에 테스트 값 적용(teardown 뒤 BaseUiTest가 테스트 전 값으로 복원)
        putSetting("global", "stay_on_while_plugged_in", "0")
        putSetting("system", SMART_STAY_SETTING, "0")
        putSetting("system", "screen_off_timeout", SCREEN_OFF_TIMEOUT.toString())
        // 사용자가 추가해 둔 타일은 teardown에서 지우지 않도록 추가 여부 보관 후 빠른 설정창에 타일 추가
        wasTileAdded = isTileAdded()
        device.executeShellCommand("cmd statusbar add-tile $tileComponent")
        wakeUpAndUnlock()
        // 동영상 재생 앱 등 전면 앱의 FLAG_KEEP_SCREEN_ON 창이 화면을 붙잡지 않도록 홈 화면으로 이동
        device.pressHome()
        // 테스트 전 활성 여부 보관 후 같은 프로세스 싱글톤 비활성 상태로 시작
        instrumentation.runOnMainSync {
            wasKeepScreenOnActive = KeepScreenOnGlobal.isActive.value
            KeepScreenOnGlobal.deactivate(context)
        }
        // 이 앱의 창이 없는 상태에서 화면을 붙잡는 매일 쓰는 앱의 켜짐 유지 창 해제
        deactivateDailyKeepScreenOn()
    }

    /** 타일·활성 여부를 테스트 전 상태로 복원, 기기 설정·오버레이 표시 권한은 이 함수가 실패해도 BaseUiTest가 이후에 복원 */
    override fun teardown() {
        // 테스트 전에 없던 타일만 제거
        if (wasTileAdded == false) {
            device.executeShellCommand("cmd statusbar remove-tile $tileComponent")
        }
        wakeUpAndUnlock()
        // 오버레이 창 추가에 쓰는 권한이 BaseUiTest 복원 전이라 여기서 활성 여부 복원
        wasKeepScreenOnActive?.let { wasActive ->
            instrumentation.runOnMainSync {
                if (wasActive) {
                    KeepScreenOnGlobal.activate(context)
                } else {
                    KeepScreenOnGlobal.deactivate(context)
                }
            }
        }
        // setup에서 끈 매일 쓰는 앱의 켜짐 유지 복원
        restoreDailyKeepScreenOn()
    }

    /**
     * 매일 쓰는 앱의 화면 켜짐 유지 창이 화면을 붙잡고 있으면 끄고 끈 여부 보관
     *
     * 이 앱의 창을 없앤 뒤 호출하므로 화면 유지 창이 KeepScreenOn 창이면 매일 쓰는 앱의 창(두 앱의 창 제목이 같음).
     * KeepScreenOnReceiver는 처리 후 활성 여부만 돌려줘 테스트 전 상태는 이 창으로 판정.
     * 매일 쓰는 앱 미설치, KeepScreenOnReceiver가 없는 빌드면 broadcast 결과에 data가 없어 아무것도 바꾸지 않음
     */
    private fun deactivateDailyKeepScreenOn() {
        // uitest가 아닌 빌드에서 실행돼 패키지가 같으면 이 앱 자신이라 대상 없음
        if (dailyPackageName == context.packageName) return
        // 홈 이동 직후 이전 전면 앱 창이 남아 있을 수 있어 화면 유지 창이 없어지거나 KeepScreenOn 창이 될 때까지 대기
        val isHeldByDaily = runBlocking {
            waitUntil(STATE_TIMEOUT) { hasNoScreenHolder() || holdScreenLine().contains(KeepScreenOnGlobal.WINDOW_TITLE) }
            holdScreenLine().contains(KeepScreenOnGlobal.WINDOW_TITLE)
        }
        // 화면 유지 창이 없거나 다른 창이면 매일 쓰는 앱은 건드리지 않음(다른 창은 이후 assertNoOtherScreenHolder가 실패로 알림)
        if (!isHeldByDaily) return
        isDailyKeepScreenOnDeactivated = sendDailyKeepScreenOnAction(ACTION_DEACTIVATE_DAILY) == "active=false"
    }

    /**
     * setup에서 끈 매일 쓰는 앱의 화면 켜짐 유지 복원
     *
     * 잠긴 상태(화면 끄기 테스트 뒤)면 테스트가 없었어도 화면 꺼짐으로 해제됐을 상태라 생략
     */
    private fun restoreDailyKeepScreenOn() {
        if (!isDailyKeepScreenOnDeactivated) return
        isDailyKeepScreenOnDeactivated = false
        if (keyguardManager.isKeyguardLocked) return
        sendDailyKeepScreenOnAction(ACTION_ACTIVATE_DAILY)
    }

    /**
     * 매일 쓰는 앱의 debug KeepScreenOnReceiver에 [action]을 보내고 결과 data(`active=true`·`active=false`) 반환
     *
     * shell(DUMP 권한 보유)이 보내는 am broadcast는 순서 있는 브로드캐스트라 처리 후 활성 여부가 결과로 돌아오고,
     * Receiver가 없거나 앱이 없으면 data 없이 끝나 null
     */
    private fun sendDailyKeepScreenOnAction(action: String): String? {
        val output = device.executeShellCommand("am broadcast -a $action -p $dailyPackageName")
        return BROADCAST_DATA_REGEX.find(output)?.groupValues?.get(1)
    }

    /**
     * 빠른 설정창에 이 타일이 추가돼 있는지 여부
     *
     * 추가된 타일 목록 설정에서 TileService 타일은 `custom(컴포넌트)` 항목으로 저장
     */
    private fun isTileAdded(): Boolean {
        val tileSpecs = getSetting("secure", QS_TILES_SETTING)?.split(",").orEmpty().map { it.trim() }
        return tileSpecs.any {
            it == "custom(${tileComponentName.flattenToShortString()})" || it == "custom($tileComponent)"
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
        /** 빠른 설정창에 추가된 타일 목록 설정 키(secure) */
        private const val QS_TILES_SETTING = "sysui_qs_tiles"
        /** dumpsys window 화면 유지 창 필드명 */
        private const val HOLD_SCREEN_FIELD = "mHoldScreenWindow"
        /** 계측 테스트 빌드 타입(app/build.gradle.kts의 uitest)의 applicationIdSuffix */
        private const val UITEST_PACKAGE_SUFFIX = ".uitest"
        /** 매일 쓰는 앱의 KeepScreenOnReceiver 켜기 액션(debug 소스셋 클래스라 uitest 변형에서 상수 참조 불가) */
        private const val ACTION_ACTIVATE_DAILY = "com.black.app.action.ACTIVATE_KEEP_SCREEN_ON"
        /** 매일 쓰는 앱의 KeepScreenOnReceiver 끄기 액션 */
        private const val ACTION_DEACTIVATE_DAILY = "com.black.app.action.DEACTIVATE_KEEP_SCREEN_ON"
        /** am broadcast 출력(`Broadcast completed: result=0, data="active=false"`)의 결과 data */
        private val BROADCAST_DATA_REGEX = Regex("""data="([^"]*)"""")
    }
}
