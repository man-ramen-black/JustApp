package com.black.app.ui.main.usagetimer

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.black.app.R
import com.black.app.model.UsageTimerRepository
import com.black.app.testutil.BaseUiTest
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
 * 호출되지 않아 자동 검증 불가
 *
 * 사용자가 켜 둔 다른 접근성 서비스는 그대로 두고 이 서비스만 더해 켬.
 * 접근성 설정·오버레이 표시 권한·선택 앱은 테스트 후 테스트 전 값으로 되돌림
 * 같은 기기의 매일 쓰는 debug 앱과 서비스 라벨(JustApp - UsageTimer)이 같아, 그 앱의 서비스가 켜져 있으면 라벨로 찾는 바인딩 확인이 이 앱과 구분되지 않음
 *
 * [수동 검증 방법] 정상 설치 앱에서 다음 절차로 오버레이 표시·유지 확인(`<패키지>`는 설치한 빌드의 applicationId)
 * 1. `ANDROID_SERIAL=<시리얼> ./gradlew :app:installDebug`
 * 2. 선택 앱 저장: 앱의 UsageTimer 화면에서 앱 선택으로 대상 앱 선택
 * 3. `adb shell appops set <패키지> SYSTEM_ALERT_WINDOW allow`
 * 4. `adb shell settings get secure enabled_accessibility_services`로 켜 둔 서비스를 확인한 뒤 그 값 끝에
 *    `:<패키지>/com.black.app.ui.main.usagetimer.UsageTimerAccessibility`를 붙여 put(켜 둔 서비스가 없으면 콜론 없이)
 *    `adb shell settings put secure accessibility_enabled 1`
 * 5. 선택 앱을 포그라운드로 실행 후 dumpsys window로 오버레이 유지 확인
 *
 * 표시 판정 로직은 [UsageTimerGlobalTest]가 단위 테스트로 검증
 *
 * 실제 시간 대기가 필요하므로 가상 시간으로 delay를 건너뛰는 runTest 대신 runBlocking 사용
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore("계측 중인 앱은 자기 자신의 AccessibilityService가 바인딩되지 않아 자동 검증 불가, KDoc의 수동 절차로 검증")
class UsageTimerAccessibilityUiTest : BaseUiTest() {

    private val repository = UsageTimerRepository(context)

    /** 테스트 전 사용자가 선택해 둔 앱, setup에서 읽기 전에는 null */
    private var originalSelectedApps: List<String>? = null

    /** 이 앱의 접근성 서비스 컴포넌트(패키지는 설치한 빌드의 applicationId) */
    private val accessibilityComponentName = ComponentName(context, UsageTimerAccessibility::class.java)

    // 리소스 이름의 패키지 부분을 applicationId·namespace 중 하나로 가정하지 않고 실제 리소스 이름(패키지:id/timer)으로 조회
    private val timerSelector = By.res(context.resources.getResourceName(R.id.timer))

    // 선택 앱으로 사용할 설정 화면 앱 패키지(기기별 차이 대비 동적 조회)
    private val settingsPackage: String by lazy {
        context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
            ?.activityInfo?.packageName ?: "com.android.settings"
    }

    override fun setup() {
        // 오버레이 표시 권한 부여(shell uid 권한으로 직접 허용, teardown 뒤 BaseUiTest가 테스트 전 모드로 복원)
        setAppOpMode("SYSTEM_ALERT_WINDOW", "allow")
        // 사용자 선택 앱 보관 후 설정 화면 앱을 선택 앱으로 저장
        runBlocking {
            originalSelectedApps = repository.getSelectedApps()
            repository.saveSelectedApps(listOf(settingsPackage))
        }
        // 켜 둔 접근성 서비스에 이 서비스를 더해 활성화(teardown 뒤 BaseUiTest가 테스트 전 값으로 복원)
        putSetting("secure", Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledServicesWithUsageTimer())
        putSetting("secure", Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        // 설정값 반영이 아닌 실제 바인딩 완료까지 대기(레이스 방지)
        runBlocking { waitUntilServiceBound() }
    }

    /** 오버레이 제거·사용자 선택 앱 복원, 접근성 설정·오버레이 표시 권한은 이 함수 뒤 BaseUiTest가 복원 */
    override fun teardown() {
        // 앞에 남은 설정 화면 앱의 접근성 이벤트로 타이머가 다시 붙지 않도록 오버레이 제거 전에 홈으로 이동
        device.pressHome()
        // 오버레이 제거(같은 프로세스 싱글톤이므로 메인 스레드에서 직접 정리)
        instrumentation.runOnMainSync { UsageTimerGlobal.detachView() }
        originalSelectedApps?.let { runBlocking { repository.saveSelectedApps(it) } }
    }

    /**
     * 접근성 서비스를 켠 뒤 선택 앱을 포그라운드로 띄우면 타이머가 노출되고 유지되는지 검증
     *
     * Given: 접근성 서비스 활성화 + 설정 화면 앱을 선택 앱으로 저장(setUp)
     * When: 설정 화면 앱을 포그라운드로 실행
     * Then: 타이머 오버레이가 노출되고, 일정 시간 동안 사라지지 않고 유지(즉시 사라짐 회귀 방지)
     */
    @Test
    fun test_01_selectedAppForegroundShowsAndKeepsTimer() = runBlocking {
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
            delay(KEEP_POLL_INTERVAL)
        }
    }

    /**
     * 사용자가 켜 둔 접근성 서비스 목록에 이 서비스를 더한 값
     *
     * 목록은 콜론으로 구분한 컴포넌트 이름이고, 이 서비스가 이미 들어 있으면 그대로 반환
     */
    private fun enabledServicesWithUsageTimer(): String {
        val enabledServices = getSetting("secure", Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(":")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val alreadyEnabled = enabledServices.any {
            ComponentName.unflattenFromString(it) == accessibilityComponentName
        }
        val services = if (alreadyEnabled) {
            enabledServices
        } else {
            enabledServices + accessibilityComponentName.flattenToString()
        }
        return services.joinToString(":")
    }

    /**
     * 접근성 서비스가 시스템에 실제로 바인딩될 때까지 대기, [ENABLE_TIMEOUT] 안에 바인딩되지 않으면 실패
     *
     * 설정값(enabled_accessibility_services)은 즉시 반영되지만 실제 바인딩은 지연되므로,
     * dumpsys accessibility의 "Bound services" 항목에 서비스 라벨이 나타날 때까지 폴링
     */
    private suspend fun waitUntilServiceBound() {
        val bound = withTimeoutOrNull(ENABLE_TIMEOUT) {
            while (!isServiceBound()) {
                delay(POLL_INTERVAL)
            }
        } != null
        assertTrue("접근성 서비스가 ${ENABLE_TIMEOUT}ms 안에 바인딩되지 않았습니다", bound)
    }

    /** dumpsys accessibility의 "Bound services" 항목에 이 서비스 라벨이 있는지 여부 */
    private fun isServiceBound(): Boolean {
        val dump = device.executeShellCommand("dumpsys accessibility")
        val boundSection = dump.substringAfter("Bound services:", "").substringBefore("Enabled services:")
        return boundSection.contains(SERVICE_LABEL)
    }

    companion object {
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
