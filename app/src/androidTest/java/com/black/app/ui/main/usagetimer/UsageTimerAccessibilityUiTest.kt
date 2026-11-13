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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * 접근성 서비스를 UiAutomator로 직접 켜고 선택 앱 포그라운드 시 UsageTimer가
 * 노출된 뒤 사라지지 않고 유지되는지 검증하는 계측 테스트
 *
 * 계측 중에도 이 앱의 접근성 서비스가 바인딩되는 것은 `HiltTestRunner`가 UiAutomation의 접근성 서비스 억제를 끄기 때문
 * 선택 앱은 기기의 홈 앱으로 함. 설정 앱은 HIDE_NON_SYSTEM_OVERLAY_WINDOWS로 다른 앱의 오버레이 창을 숨겨
 * UiAutomator가 타이머를 찾지 못하고, 이 앱 자신을 띄우면 접근성 서비스가 활성 창(rootInActiveWindow)을 얻지 못해 판정 불가
 *
 * 사용자가 켜 둔 다른 접근성 서비스는 그대로 두고 이 서비스만 더해 켬.
 * 접근성 설정·오버레이 표시 권한·선택 앱은 테스트 후 테스트 전 값으로 되돌림
 * 바인딩 확인은 개발 앱 서비스 라벨((Dev)JustApp - UsageTimer)로 해 같은 기기의 실사용 앱 서비스와 구분
 *
 * 잠금 중에는 오버레이 창이 표시되지 않고 홈 앱도 포그라운드가 되지 않아,
 * `UsageTimerViewUiTest`와 같이 화면을 켜고 잠금 해제를 시도한 뒤 그래도 잠겨 있으면 실패 대신 건너뜀
 *
 * 표시 판정 로직은 [UsageTimerGlobalTest]가 단위 테스트로 검증
 *
 * 실제 시간 대기가 필요하므로 가상 시간으로 delay를 건너뛰는 runTest 대신 runBlocking 사용
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerAccessibilityUiTest : BaseUiTest() {

    private val repository = UsageTimerRepository(context)

    /** 이 앱의 접근성 서비스 컴포넌트(패키지는 설치한 빌드의 applicationId) */
    private val accessibilityComponentName = ComponentName(context, UsageTimerAccessibility::class.java)

    // 리소스 이름의 패키지 부분을 applicationId·namespace 중 하나로 가정하지 않고 실제 리소스 이름(패키지:id/timer)으로 조회
    private val timerSelector = By.res(context.resources.getResourceName(R.id.timer))

    /** 선택 앱으로 저장할 홈 앱 패키지 */
    private val homePackage: String by lazy { device.launcherPackageName }

    // 홈으로 새 포그라운드 전환을 만들려고 거쳐 가는 설정 화면 앱 패키지(기기별 차이 대비 동적 조회)
    private val settingsPackage: String by lazy {
        context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
            ?.activityInfo?.packageName ?: "com.android.settings"
    }

    /**
     * 오버레이 표시 권한·사용자 선택 앱·접근성 설정을 테스트 후 복원하도록 BaseUiTest에 등록하며 변경
     *
     * BaseUiTest는 등록 역순으로 복원하므로 접근성 설정 → 선택 앱과 오버레이 → 오버레이 표시 권한 순으로 되돌림
     */
    override fun setup() {
        // 오버레이 표시 권한 부여(shell uid 권한으로 직접 허용, teardown 뒤 BaseUiTest가 테스트 전 모드로 복원)
        setAppOpMode("SYSTEM_ALERT_WINDOW", "allow")
        // 사용자 선택 앱 보관 후 홈 앱을 선택 앱으로 저장
        preserveValue(
            read = { repository.getSelectedApps() },
            restore = { selectedApps ->
                // 홈 화면의 이후 접근성 이벤트로 타이머가 다시 붙지 않도록 선택 앱을 먼저 복원
                repository.saveSelectedApps(selectedApps)
                // 오버레이 제거(같은 프로세스 싱글톤이므로 메인 스레드에서 직접 정리)
                instrumentation.runOnMainSync { UsageTimerGlobal.detachView() }
            },
        )
        runBlocking { repository.saveSelectedApps(listOf(homePackage)) }
        // 켜 둔 접근성 서비스에 이 서비스를 더해 활성화(teardown 뒤 BaseUiTest가 테스트 전 값으로 복원)
        putSetting("secure", Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledServicesWithUsageTimer())
        putSetting("secure", Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        // 설정값 반영이 아닌 실제 바인딩 완료까지 대기(레이스 방지)
        runBlocking { waitUntilServiceBound() }
    }

    /**
     * 접근성 서비스를 켠 뒤 선택 앱을 포그라운드로 띄우면 타이머가 노출되고 유지되는지 검증
     *
     * Given: 잠금 해제 상태, 접근성 서비스 활성화 + 홈 앱을 선택 앱으로 저장(setUp)
     * When: 설정 화면을 거쳐 홈으로 이동
     * Then: 타이머 오버레이가 노출되고, 일정 시간 동안 사라지지 않고 유지(즉시 사라짐 회귀 방지)
     */
    @Test
    fun test_01_selectedAppForegroundShowsAndKeepsTimer() = runBlocking {
        /** Given **/
        assumeUnlocked()
        // setUp에서 접근성 서비스 활성화 및 선택 앱 저장 완료

        /** When **/
        // 바인딩 완료 후 홈으로 새 포그라운드 전환 이벤트를 발생시키려고 설정 화면을 거쳐 홈으로 이동
        device.executeShellCommand("am start -a android.settings.SETTINGS")
        device.wait(Until.hasObject(By.pkg(settingsPackage)), DEFAULT_TIMEOUT_MILLIS)
        device.pressHome()
        device.wait(Until.hasObject(By.pkg(homePackage)), DEFAULT_TIMEOUT_MILLIS)

        /** Then **/
        // 선택 앱 포그라운드 진입 후 타이머 오버레이 노출 확인
        assertTrue(
            "선택 앱 포그라운드 후 타이머 오버레이가 노출되지 않았습니다",
            device.wait(Until.hasObject(timerSelector), DEFAULT_TIMEOUT_MILLIS),
        )

        // 일시적 창 이벤트가 발생할 시간 동안 반복 확인하여 타이머가 즉시 사라지지 않는지 검증
        val disappeared = waitUntil(timeoutMillis = KEEP_DURATION, intervalMillis = KEEP_POLL_INTERVAL) {
            !device.hasObject(timerSelector)
        }
        assertFalse("타이머가 노출 직후 사라졌습니다", disappeared)
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
     * 접근성 서비스가 시스템에 실제로 바인딩될 때까지 대기, [DEFAULT_TIMEOUT_MILLIS] 안에 바인딩되지 않으면 실패
     *
     * 설정값(enabled_accessibility_services)은 즉시 반영되지만 실제 바인딩은 지연되므로,
     * dumpsys accessibility의 "Bound services" 항목에 서비스 라벨이 나타날 때까지 폴링
     */
    private suspend fun waitUntilServiceBound() {
        assertTrue(
            "접근성 서비스가 ${DEFAULT_TIMEOUT_MILLIS}ms 안에 바인딩되지 않았습니다",
            waitUntil { isServiceBound() },
        )
    }

    /**
     * dumpsys accessibility의 "Bound services" 항목에 이 서비스 라벨이 있는지 여부
     *
     * 라벨은 이 앱의 리소스에서 읽어 개발 앱 라벨((Dev)…)로 실사용 앱의 같은 서비스와 구분
     */
    private fun isServiceBound(): Boolean {
        val dump = device.executeShellCommand("dumpsys accessibility")
        val boundSection = dump.substringAfter("Bound services:", "").substringBefore("Enabled services:")
        return boundSection.contains(context.getString(R.string.accessibility_service_label_usage_timer))
    }

    companion object {
        /** 타이머가 노출된 뒤 사라지지 않는지 확인하는 시간(일시적 창 이벤트가 발생할 시간) */
        private const val KEEP_DURATION = 3000L
        private const val KEEP_POLL_INTERVAL = 500L
    }
}
