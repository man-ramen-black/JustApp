package com.black.core.util

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [PermissionHelper]의 설정 화면 인텐트와 컨텍스트 기반 권한·알림 허용 확인 검증
 *
 * [PermissionHelper.showAppNotificationSettings]의 버전별 설정 화면 인텐트와
 * [PermissionHelper.openAccessibilityServiceDetailSetting]의 실패 시 다음 화면 시도 순서,
 * [PermissionHelper.isGranted]·[PermissionHelper.isNotificationPermissionGranted]·[PermissionHelper.areNotificationsEnabled]의 판단,
 * 사용 정보 접근·알림 접근·위치 권한·위치 설정 판정과 각 설정 화면 인텐트 검증
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PermissionHelperTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * API 26 이상에서 앱 알림 설정 화면을 패키지 지정으로 여는지 검증
     *
     * Given: SDK 34, Activity가 아닌 애플리케이션 컨텍스트
     * When: showAppNotificationSettings 호출
     * Then: ACTION_APP_NOTIFICATION_SETTINGS, EXTRA_APP_PACKAGE가 앱 패키지, FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    @Config(sdk = [34])
    fun test_01_opensAppNotificationSettingsOnOreoOrAbove() = runTest {
        /** Given **/
        // SDK는 @Config, 애플리케이션 컨텍스트는 필드로 지정(별도 준비 없음)

        /** When **/
        PermissionHelper.showAppNotificationSettings(context)

        /** Then **/
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * API 26 미만에서 앱 정보 화면으로 대신 여는지 검증
     *
     * Given: SDK 25, Activity가 아닌 애플리케이션 컨텍스트
     * When: showAppNotificationSettings 호출
     * Then: ACTION_APPLICATION_DETAILS_SETTINGS, data가 package:<앱 패키지>, FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    @Config(sdk = [25])
    fun test_02_opensApplicationDetailsSettingsBelowOreo() = runTest {
        /** Given **/
        // SDK는 @Config, 애플리케이션 컨텍스트는 필드로 지정(별도 준비 없음)

        /** When **/
        PermissionHelper.showAppNotificationSettings(context)

        /** Then **/
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * 접근성 상세 화면, 하이라이트 목록, 일반 목록이 모두 실패해도 예외 없이 끝나는지 검증
     *
     * Given: 상세 화면은 SecurityException, 접근성 설정 목록은 ActivityNotFoundException을 던지는 컨텍스트
     * When: openAccessibilityServiceDetailSetting 호출
     * Then: 예외 없이 상세 화면, 하이라이트 extras가 있는 목록, extras 없는 목록 순으로 3회 시도
     */
    @Test
    fun test_03_accessibilityDetailFallbacksAllFailWithoutException() = runTest {
        /** Given **/
        val failingContext: Context = mockk()
        val startedIntents = mutableListOf<Intent>()
        every { failingContext.packageName } returns context.packageName
        every { failingContext.startActivity(capture(startedIntents)) } answers {
            if (firstArg<Intent>().action == ACTION_ACCESSIBILITY_DETAILS_SETTINGS) {
                throw SecurityException("Permission Denial")
            }
            throw ActivityNotFoundException("No Activity found")
        }

        /** When **/
        PermissionHelper.openAccessibilityServiceDetailSetting(failingContext, AccessibilityService::class.java)

        /** Then **/
        assertEquals(
            listOf(
                ACTION_ACCESSIBILITY_DETAILS_SETTINGS,
                Settings.ACTION_ACCESSIBILITY_SETTINGS,
                Settings.ACTION_ACCESSIBILITY_SETTINGS,
            ),
            startedIntents.map { it.action },
        )
        assertTrue(startedIntents[1].hasExtra(EXTRA_FRAGMENT_ARG_KEY))
        assertFalse(startedIntents[2].hasExtra(EXTRA_FRAGMENT_ARG_KEY))
    }

    /**
     * 접근성 상세 화면이 열리면 하이라이트 목록과 일반 목록을 시도하지 않는지 검증
     *
     * Given: startActivity가 성공하는 컨텍스트
     * When: openAccessibilityServiceDetailSetting 호출
     * Then: 상세 화면 1회만 시작, 대상 서비스 컴포넌트 이름과 FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    fun test_04_accessibilityDetailSuccessSkipsFallbacks() = runTest {
        /** Given **/
        val startingContext: Context = mockk()
        val startedIntents = mutableListOf<Intent>()
        every { startingContext.packageName } returns context.packageName
        every { startingContext.startActivity(capture(startedIntents)) } just Runs

        /** When **/
        PermissionHelper.openAccessibilityServiceDetailSetting(startingContext, AccessibilityService::class.java)

        /** Then **/
        verify(exactly = 1) { startingContext.startActivity(any()) }
        val intent = startedIntents.single()
        assertEquals(ACTION_ACCESSIBILITY_DETAILS_SETTINGS, intent.action)
        assertEquals(
            ComponentName(context.packageName, AccessibilityService::class.java.name).flattenToString(),
            intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME),
        )
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * 접근성 상세 화면이 실패하고 하이라이트 목록이 열리면 일반 목록을 시도하지 않는지 검증
     *
     * Given: 상세 화면만 SecurityException을 던지고 접근성 설정 목록은 성공하는 컨텍스트
     * When: openAccessibilityServiceDetailSetting 호출
     * Then: 상세 화면, 하이라이트 extras가 있는 목록 순으로 2회만 시도
     */
    @Test
    fun test_05_accessibilityHighlightSuccessSkipsPlainList() = runTest {
        /** Given **/
        val partlyFailingContext: Context = mockk()
        val startedIntents = mutableListOf<Intent>()
        every { partlyFailingContext.packageName } returns context.packageName
        every { partlyFailingContext.startActivity(capture(startedIntents)) } answers {
            if (firstArg<Intent>().action == ACTION_ACCESSIBILITY_DETAILS_SETTINGS) {
                throw SecurityException("Permission Denial")
            }
        }

        /** When **/
        PermissionHelper.openAccessibilityServiceDetailSetting(partlyFailingContext, AccessibilityService::class.java)

        /** Then **/
        assertEquals(
            listOf(ACTION_ACCESSIBILITY_DETAILS_SETTINGS, Settings.ACTION_ACCESSIBILITY_SETTINGS),
            startedIntents.map { it.action },
        )
        assertTrue(startedIntents[1].hasExtra(EXTRA_FRAGMENT_ARG_KEY))
    }

    /**
     * Activity 없이 컨텍스트만으로 권한 허용 여부를 확인하는지 검증
     *
     * Given: SDK 34, 애플리케이션 컨텍스트에 EXPAND_STATUS_BAR 허용, CAMERA 거부
     * When: isGranted 호출
     * Then: EXPAND_STATUS_BAR true, CAMERA false
     */
    @Test
    @Config(sdk = [34])
    fun test_06_isGrantedFollowsPermissionState() = runTest {
        /** Given **/
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.EXPAND_STATUS_BAR)
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)

        /** When **/
        val expandStatusBarGranted = PermissionHelper.isGranted(context, Manifest.permission.EXPAND_STATUS_BAR)
        val cameraGranted = PermissionHelper.isGranted(context, Manifest.permission.CAMERA)

        /** Then **/
        assertTrue(expandStatusBarGranted)
        assertFalse(cameraGranted)
    }

    /**
     * Android 13 이상은 알림 권한 허용 여부를 그대로 따르는지 검증
     *
     * Given: SDK 34, 알림 권한 거부 후 허용
     * When: 각 상태에서 isNotificationPermissionGranted 호출
     * Then: 거부 시 false, 허용 시 true
     */
    @Test
    @Config(sdk = [34])
    fun test_07_notificationPermissionFollowsGrantFromTiramisu() = runTest {
        /** Given **/
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        /** When **/
        val deniedResult = PermissionHelper.isNotificationPermissionGranted(context)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val grantedResult = PermissionHelper.isNotificationPermissionGranted(context)

        /** Then **/
        assertFalse(deniedResult)
        assertTrue(grantedResult)
    }

    /**
     * Android 13 미만은 알림 권한이 없어 항상 허용으로 판단하는지 검증
     *
     * Given: SDK 32, 알림 권한 거부
     * When: isNotificationPermissionGranted 호출
     * Then: true
     */
    @Test
    @Config(sdk = [32])
    fun test_08_notificationPermissionGrantedBelowTiramisu() = runTest {
        /** Given **/
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        /** When **/
        val granted = PermissionHelper.isNotificationPermissionGranted(context)

        /** Then **/
        assertTrue(granted)
    }

    /**
     * 앱 알림 허용 여부가 NotificationManager의 앱 알림 설정을 따르는지 검증
     *
     * Given: SDK 34, 앱 알림 끔 후 켬
     * When: 각 상태에서 areNotificationsEnabled 호출
     * Then: 끔 시 false, 켬 시 true
     */
    @Test
    @Config(sdk = [34])
    fun test_09_areNotificationsEnabledFollowsAppSetting() = runTest {
        /** Given **/
        val shadowNotificationManager = shadowOf(context.getSystemService(NotificationManager::class.java))
        shadowNotificationManager.setNotificationsEnabled(false)

        /** When **/
        val disabledResult = PermissionHelper.areNotificationsEnabled(context)
        shadowNotificationManager.setNotificationsEnabled(true)
        val enabledResult = PermissionHelper.areNotificationsEnabled(context)

        /** Then **/
        assertFalse(disabledResult)
        assertTrue(enabledResult)
    }

    /** 사용 정보 접근 AppOps가 [mode]이고 `PACKAGE_USAGE_STATS` 권한 부여 여부가 [usageStatsPermissionGranted]인 컨텍스트 */
    private fun contextWithUsageMode(mode: Int, usageStatsPermissionGranted: Boolean = false): Context {
        val appOpsManager = mockk<AppOpsManager>()
        // 실행 중인 SDK에 없는 메서드는 스텁할 수 없어 버전별로 하나만 지정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            every { appOpsManager.unsafeCheckOpNoThrow(any<String>(), any(), any()) } returns mode
        } else {
            @Suppress("DEPRECATION")
            every { appOpsManager.checkOpNoThrow(any<String>(), any(), any()) } returns mode
        }
        val permissionResult = if (usageStatsPermissionGranted) {
            PackageManager.PERMISSION_GRANTED
        } else {
            PackageManager.PERMISSION_DENIED
        }
        return mockk<Context> {
            every { getSystemService(Context.APP_OPS_SERVICE) } returns appOpsManager
            every { packageName } returns "com.black.app"
            every { checkPermission(Manifest.permission.PACKAGE_USAGE_STATS, any(), any()) } returns permissionResult
        }
    }

    /**
     * API 29 이상에서 허용 모드면 사용 정보 접근이 허용으로 판정되는지 검증
     *
     * Given: SDK 34, AppOps가 MODE_ALLOWED
     * When: isUsageAccessGranted 호출
     * Then: true
     */
    @Test
    @Config(sdk = [34])
    fun test_10_usageAccessGrantedOnQOrAbove() = runTest {
        /** Given / When / Then **/
        assertTrue(PermissionHelper.isUsageAccessGranted(contextWithUsageMode(AppOpsManager.MODE_ALLOWED)))
    }

    /**
     * API 29 이상에서 기본 모드이고 권한이 부여되지 않았으면 거부로 판정되는지 검증
     *
     * Given: SDK 34, AppOps가 MODE_DEFAULT, PACKAGE_USAGE_STATS 권한 없음
     * When: isUsageAccessGranted 호출
     * Then: false
     */
    @Test
    @Config(sdk = [34])
    fun test_11_usageAccessDeniedOnQOrAbove() = runTest {
        /** Given / When / Then **/
        assertFalse(
            PermissionHelper.isUsageAccessGranted(
                contextWithUsageMode(AppOpsManager.MODE_DEFAULT, usageStatsPermissionGranted = false),
            ),
        )
    }

    /**
     * API 29 미만에서 허용 모드면 허용으로 판정되는지 검증
     *
     * Given: SDK 25, AppOps가 MODE_ALLOWED
     * When: isUsageAccessGranted 호출
     * Then: true
     */
    @Test
    @Config(sdk = [25])
    fun test_12_usageAccessGrantedBelowQ() = runTest {
        /** Given / When / Then **/
        assertTrue(PermissionHelper.isUsageAccessGranted(contextWithUsageMode(AppOpsManager.MODE_ALLOWED)))
    }

    /**
     * API 29 미만에서 거부 모드면 거부로 판정되는지 검증
     *
     * Given: SDK 25, AppOps가 MODE_ERRORED
     * When: isUsageAccessGranted 호출
     * Then: false
     */
    @Test
    @Config(sdk = [25])
    fun test_13_usageAccessDeniedBelowQ() = runTest {
        /** Given / When / Then **/
        assertFalse(PermissionHelper.isUsageAccessGranted(contextWithUsageMode(AppOpsManager.MODE_ERRORED)))
    }

    /** 알림 접근 판정용 서비스 클래스 */
    private class FirstListenerService : NotificationListenerService()

    /** 다른 컴포넌트와 구분하기 위한 서비스 클래스 */
    private class SecondListenerService : NotificationListenerService()

    private fun putEnabledListeners(value: String?) {
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", value)
    }

    /**
     * 허용 목록에 해당 서비스 컴포넌트가 있으면 알림 접근이 허용으로 판정되는지 검증
     *
     * Given: 허용 목록에 다른 서비스와 대상 서비스가 함께 있음
     * When: isNotificationListenerEnabled 호출
     * Then: true
     */
    @Test
    fun test_14_notificationListenerEnabledWhenComponentListed() = runTest {
        /** Given **/
        val other = ComponentName("com.other", "com.other.Service").flattenToString()
        val target = ComponentName(context, FirstListenerService::class.java).flattenToString()
        putEnabledListeners("$other:$target")

        /** When / Then **/
        assertTrue(PermissionHelper.isNotificationListenerEnabled(context, FirstListenerService::class.java))
    }

    /**
     * 같은 앱의 다른 서비스만 허용 목록에 있으면 거부로 판정되는지 검증
     *
     * Given: 허용 목록에 같은 패키지의 다른 서비스만 있음
     * When: isNotificationListenerEnabled 호출
     * Then: false
     */
    @Test
    fun test_15_notificationListenerDisabledWhenOnlyOtherComponentListed() = runTest {
        /** Given **/
        putEnabledListeners(ComponentName(context, SecondListenerService::class.java).flattenToString())

        /** When / Then **/
        assertFalse(PermissionHelper.isNotificationListenerEnabled(context, FirstListenerService::class.java))
    }

    /**
     * 허용 목록 설정값이 없으면 거부로 판정되는지 검증
     *
     * Given: 허용 목록 설정값 없음
     * When: isNotificationListenerEnabled 호출
     * Then: false
     */
    @Test
    fun test_16_notificationListenerDisabledWhenSettingMissing() = runTest {
        /** Given **/
        putEnabledListeners(null)

        /** When / Then **/
        assertFalse(PermissionHelper.isNotificationListenerEnabled(context, FirstListenerService::class.java))
    }

    /**
     * API 30 이상에서 알림 접근 상세 화면을 컴포넌트 지정으로 여는지 검증
     *
     * Given: SDK 34, Activity가 아닌 애플리케이션 컨텍스트
     * When: showNotificationListenerSettings 호출
     * Then: ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, 컴포넌트 extra가 대상 서비스, FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    @Config(sdk = [34])
    fun test_17_opensNotificationListenerDetailOnROrAbove() = runTest {
        /** Given / When **/
        PermissionHelper.showNotificationListenerSettings(context, FirstListenerService::class.java)

        /** Then **/
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, intent.action)
        assertEquals(
            ComponentName(context, FirstListenerService::class.java).flattenToString(),
            intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME),
        )
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * API 30 미만에서 알림 접근 목록 화면을 여는지 검증
     *
     * Given: SDK 25, Activity가 아닌 애플리케이션 컨텍스트
     * When: showNotificationListenerSettings 호출
     * Then: ACTION_NOTIFICATION_LISTENER_SETTINGS, FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    @Config(sdk = [25])
    fun test_18_opensNotificationListenerListBelowR() = runTest {
        /** Given / When **/
        PermissionHelper.showNotificationListenerSettings(context, FirstListenerService::class.java)

        /** Then **/
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * 사용 정보 접근 설정 화면을 이 앱 지정으로 여는지 검증
     *
     * Given: Activity가 아닌 애플리케이션 컨텍스트
     * When: showUsageAccessSettings 호출
     * Then: ACTION_USAGE_ACCESS_SETTINGS, data가 package:<앱 패키지>, FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    fun test_19_opensUsageAccessSettingsForApp() = runTest {
        /** Given / When **/
        PermissionHelper.showUsageAccessSettings(context)

        /** Then **/
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * 앱 지정 화면을 열 수 없으면 사용 정보 접근 목록 화면으로 대체하는지 검증
     *
     * Given: 첫 startActivity가 ActivityNotFoundException을 던지는 컨텍스트
     * When: showUsageAccessSettings 호출
     * Then: 두 번째로 시작한 인텐트가 데이터 없는 ACTION_USAGE_ACCESS_SETTINGS
     */
    @Test
    fun test_20_fallsBackToUsageAccessListWhenAppScreenMissing() = runTest {
        /** Given **/
        val started = mutableListOf<Intent>()
        val failingContext = mockk<Context> {
            every { packageName } returns "com.black.app"
            every { startActivity(any()) } answers {
                started.add(firstArg())
                if (started.size == 1) throw ActivityNotFoundException()
            }
        }

        /** When **/
        PermissionHelper.showUsageAccessSettings(failingContext)

        /** Then **/
        assertEquals(2, started.size)
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, started[1].action)
        assertEquals(null, started[1].data)
    }

    /**
     * 앱 정보 화면을 여는지 검증
     *
     * Given: Activity가 아닌 애플리케이션 컨텍스트
     * When: showAppDetailsSettings 호출
     * Then: ACTION_APPLICATION_DETAILS_SETTINGS, data가 package:<앱 패키지>, FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    fun test_21_opensAppDetailsSettings() = runTest {
        /** Given / When **/
        PermissionHelper.showAppDetailsSettings(context)

        /** Then **/
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * 기본 모드여도 PACKAGE_USAGE_STATS 권한이 부여됐으면 허용으로 판정되는지 검증
     *
     * Given: SDK 34, AppOps가 MODE_DEFAULT, PACKAGE_USAGE_STATS 권한 있음
     * When: isUsageAccessGranted 호출
     * Then: true
     */
    @Test
    @Config(sdk = [34])
    fun test_22_usageAccessGrantedByPermissionInDefaultMode() = runTest {
        /** Given / When / Then **/
        assertTrue(
            PermissionHelper.isUsageAccessGranted(
                contextWithUsageMode(AppOpsManager.MODE_DEFAULT, usageStatsPermissionGranted = true),
            ),
        )
    }

    /**
     * 설정 앱에서 끈 모드는 PACKAGE_USAGE_STATS 권한이 있어도 거부로 판정되는지 검증
     *
     * Given: SDK 34, AppOps가 MODE_IGNORED, PACKAGE_USAGE_STATS 권한 있음
     * When: isUsageAccessGranted 호출
     * Then: false
     */
    @Test
    @Config(sdk = [34])
    fun test_23_usageAccessDeniedWhenIgnoredDespitePermission() = runTest {
        /** Given / When / Then **/
        assertFalse(
            PermissionHelper.isUsageAccessGranted(
                contextWithUsageMode(AppOpsManager.MODE_IGNORED, usageStatsPermissionGranted = true),
            ),
        )
    }

    /**
     * API 31 이상에서 대략적인 위치만 허용되면 정확한 위치 없음, 대략적인 위치만 있음으로 판정되는지 검증
     *
     * Given: SDK 34, ACCESS_COARSE_LOCATION만 허용
     * When: isFineLocationGranted, isApproximateLocationOnly 호출
     * Then: 정확한 위치 false, 대략적인 위치만 true
     */
    @Test
    @Config(sdk = [34])
    fun test_24_approximateLocationOnlyOnSOrAbove() = runTest {
        /** Given **/
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        /** When / Then **/
        assertFalse(PermissionHelper.isFineLocationGranted(context))
        assertTrue(PermissionHelper.isApproximateLocationOnly(context))
    }

    /**
     * 정확한 위치까지 허용되면 대략적인 위치만 있는 상태가 아닌지 검증
     *
     * Given: SDK 34, ACCESS_COARSE_LOCATION과 ACCESS_FINE_LOCATION 허용
     * When: isFineLocationGranted, isApproximateLocationOnly 호출
     * Then: 정확한 위치 true, 대략적인 위치만 false
     */
    @Test
    @Config(sdk = [34])
    fun test_25_fineLocationGrantedIsNotApproximateOnly() = runTest {
        /** Given **/
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        /** When / Then **/
        assertTrue(PermissionHelper.isFineLocationGranted(context))
        assertFalse(PermissionHelper.isApproximateLocationOnly(context))
    }

    /**
     * API 31 미만은 대략적인 위치만 허용돼도 대략적인 위치만 있는 상태로 보지 않는지 검증
     *
     * Given: SDK 30, ACCESS_COARSE_LOCATION만 허용
     * When: isApproximateLocationOnly 호출
     * Then: false
     */
    @Test
    @Config(sdk = [30])
    fun test_26_approximateLocationOnlyFalseBelowS() = runTest {
        /** Given **/
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        /** When / Then **/
        assertFalse(PermissionHelper.isApproximateLocationOnly(context))
    }

    /**
     * API 29 이상에서 백그라운드 위치 권한 허용 여부를 따르는지 검증
     *
     * Given: SDK 34, ACCESS_BACKGROUND_LOCATION 미허용
     * When: isBackgroundLocationGranted 호출, ACCESS_BACKGROUND_LOCATION 허용 뒤 다시 호출
     * Then: 처음 false, 허용 뒤 true
     */
    @Test
    @Config(sdk = [34])
    fun test_27_backgroundLocationFollowsPermissionOnQOrAbove() = runTest {
        /** Given **/
        val application = RuntimeEnvironment.getApplication()

        /** When **/
        val beforeGrant = PermissionHelper.isBackgroundLocationGranted(context)
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val afterGrant = PermissionHelper.isBackgroundLocationGranted(context)

        /** Then **/
        assertFalse(beforeGrant)
        assertTrue(afterGrant)
    }

    /**
     * API 29 미만은 백그라운드 위치 권한이 없어도 허용으로 판정되는지 검증
     *
     * Given: SDK 25, ACCESS_BACKGROUND_LOCATION 미허용
     * When: isBackgroundLocationGranted 호출
     * Then: true
     */
    @Test
    @Config(sdk = [25])
    fun test_28_backgroundLocationAlwaysGrantedBelowQ() = runTest {
        /** Given / When / Then **/
        assertTrue(PermissionHelper.isBackgroundLocationGranted(context))
    }

    /**
     * 기기 위치 설정 켜짐 여부를 따르는지 검증
     *
     * Given: SDK 34, 기기 위치 설정 꺼짐
     * When: isLocationEnabled 호출, 위치 설정을 켠 뒤 다시 호출
     * Then: 처음 false, 켠 뒤 true
     */
    @Test
    @Config(sdk = [34])
    fun test_29_locationEnabledFollowsDeviceSetting() = runTest {
        /** Given **/
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(locationManager).setLocationEnabled(false)

        /** When **/
        val whenOff = PermissionHelper.isLocationEnabled(context)
        shadowOf(locationManager).setLocationEnabled(true)
        val whenOn = PermissionHelper.isLocationEnabled(context)

        /** Then **/
        assertFalse(whenOff)
        assertTrue(whenOn)
    }

    /**
     * 기기 위치 설정 화면을 여는지 검증
     *
     * Given: Activity가 아닌 애플리케이션 컨텍스트
     * When: showLocationSourceSettings 호출
     * Then: ACTION_LOCATION_SOURCE_SETTINGS, FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    fun test_30_opensLocationSourceSettings() = runTest {
        /** Given / When **/
        PermissionHelper.showLocationSourceSettings(context)

        /** Then **/
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    private companion object {
        /** PermissionHelper가 쓰는 비공개 접근성 상세 화면 액션 */
        const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

        /** 설정 앱의 항목 하이라이트 extras 키(PermissionHelper의 private 상수와 같은 값) */
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    }
}
