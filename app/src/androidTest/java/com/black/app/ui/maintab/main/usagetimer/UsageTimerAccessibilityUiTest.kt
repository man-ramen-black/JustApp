package com.black.app.ui.maintab.main.usagetimer

import android.content.Intent
import android.provider.Settings
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.black.app.model.preferences.ForegroundServicePreference
import com.black.app.testutil.BaseUiTest
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * 접근성 서비스를 UiAutomator로 직접 켜고 선택 앱 포그라운드 시 UsageTimer가
 * 노출된 뒤 사라지지 않고 유지되는지 검증하는 계측 테스트
 *
 * [한계로 @Ignore 처리]
 * 시스템은 계측(instrumentation) 중인 앱의 AccessibilityService를 바인딩하지 않으므로,
 * connectedAndroidTest로 자기 자신의 접근성 서비스를 활성화해도 onAccessibilityEvent가
 * 호출되지 않아 자동 검증이 불가능하다.
 *
 * [수동 검증 방법] 정상 설치 앱에서 다음 절차로 실기기 검증 완료(오버레이 표시·유지 확인)
 * 1. ./gradlew :app:installDebug
 * 2. 선택 앱 저장: shared_prefs/com.black.app.Foreground.xml의 UsageTimerSelectedApps에 패키지명 기록
 * 3. adb shell appops set com.black.app SYSTEM_ALERT_WINDOW allow
 * 4. adb shell settings put secure enabled_accessibility_services com.black.app/...UsageTimerAccessibility
 *    adb shell settings put secure accessibility_enabled 1
 * 5. 선택 앱을 포그라운드로 실행 후 dumpsys window로 오버레이 유지 확인
 *
 * 표시 판정 로직은 [UsageTimerGlobalTest]가 단위 테스트로 검증한다.
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore("계측 중인 앱은 자기 자신의 AccessibilityService가 바인딩되지 않아 자동 검증 불가, KDoc의 수동 절차로 검증")
class UsageTimerAccessibilityUiTest : BaseUiTest() {

    private val preference = ForegroundServicePreference(context)

    private val accessibilityComponent =
        "$PACKAGE_NAME/$PACKAGE_NAME.ui.maintab.main.usagetimer.UsageTimerAccessibility"
    private val timerSelector = By.res(PACKAGE_NAME, "timer")

    // 선택 앱으로 사용할 설정 화면 앱 패키지(기기별 차이 대비 동적 조회)
    private val settingsPackage: String by lazy {
        context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
            ?.activityInfo?.packageName ?: "com.android.settings"
    }

    override fun setup() {
        // 오버레이 표시 권한 부여(shell uid 권한으로 직접 허용)
        device.executeShellCommand("appops set $PACKAGE_NAME SYSTEM_ALERT_WINDOW allow")
        // 설정 화면 앱을 선택 앱으로 저장
        preference.putUsageTimerSelectedApps(listOf(settingsPackage))
        // 접근성 서비스 직접 활성화
        device.executeShellCommand("settings put secure enabled_accessibility_services $accessibilityComponent")
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        // 설정값 반영이 아닌 실제 바인딩 완료까지 대기(레이스 방지)
        waitUntilServiceBound()
    }

    override fun teardown() {
        // 오버레이 제거(같은 프로세스 싱글톤이므로 메인 스레드에서 직접 정리)
        instrumentation.runOnMainSync { UsageTimerGlobal.detachView() }
        // 접근성 서비스 비활성화 및 선택 앱 저장 초기화
        device.executeShellCommand("settings put secure enabled_accessibility_services \"\"")
        device.executeShellCommand("settings put secure accessibility_enabled 0")
        preference.putUsageTimerSelectedApps(emptyList())
        device.pressHome()
    }

    /**
     * 접근성 서비스를 켠 뒤 선택 앱을 포그라운드로 띄우면 타이머가 노출되고 유지되는지 검증
     *
     * Given: 접근성 서비스 활성화 + 설정 화면 앱을 선택 앱으로 저장(setUp)
     * When: 설정 화면 앱을 포그라운드로 실행
     * Then: 타이머 오버레이가 노출되고, 일정 시간 동안 사라지지 않고 유지(즉시 사라짐 회귀 방지)
     */
    @Test
    fun test_01_selectedAppForegroundShowsAndKeepsTimer() {
        /** Given **/
        // setUp에서 접근성 서비스 활성화 및 선택 앱 저장 완료

        /** When **/
        // 바인딩 완료 후 새 포그라운드 전환 이벤트를 발생시키려고 홈 경유 후 설정 화면 실행
        device.pressHome()
        device.executeShellCommand("am start -a android.settings.SETTINGS")
        device.wait(Until.hasObject(By.pkg(settingsPackage)), LAUNCH_TIMEOUT)

        /** Then **/
        // 선택 앱 포그라운드 진입 후 타이머 오버레이 노출 확인
        assertTrue(
            "선택 앱 포그라운드 후 타이머 오버레이가 노출되지 않았습니다",
            device.wait(Until.hasObject(timerSelector), SHOW_TIMEOUT),
        )

        // 일시적 창 이벤트가 발생할 시간 동안 반복 확인하여 타이머가 즉시 사라지지 않는지 검증
        val keepDeadline = System.currentTimeMillis() + KEEP_DURATION
        while (System.currentTimeMillis() < keepDeadline) {
            assertTrue(
                "타이머가 노출 직후 사라졌습니다",
                device.hasObject(timerSelector),
            )
            Thread.sleep(KEEP_POLL_INTERVAL)
        }
    }

    /**
     * 접근성 서비스가 시스템에 실제로 바인딩될 때까지 대기
     *
     * 설정값(enabled_accessibility_services)은 즉시 반영되지만 실제 바인딩은 지연되므로,
     * dumpsys accessibility의 "Bound services" 항목에 서비스 라벨이 나타날 때까지 폴링
     */
    private fun waitUntilServiceBound() {
        val deadline = System.currentTimeMillis() + ENABLE_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            val dump = device.executeShellCommand("dumpsys accessibility")
            val boundSection = dump.substringAfter("Bound services:", "").substringBefore("Enabled services:")
            if (boundSection.contains(SERVICE_LABEL)) {
                return
            }
            Thread.sleep(POLL_INTERVAL)
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.black.app"
        // AndroidManifest의 UsageTimerAccessibility android:label과 일치
        private const val SERVICE_LABEL = "JustApp - UsageTimer"
        private const val ENABLE_TIMEOUT = 12000L
        private const val LAUNCH_TIMEOUT = 5000L
        private const val SHOW_TIMEOUT = 5000L
        private const val KEEP_DURATION = 3000L
        private const val KEEP_POLL_INTERVAL = 500L
        private const val POLL_INTERVAL = 300L
    }
}
