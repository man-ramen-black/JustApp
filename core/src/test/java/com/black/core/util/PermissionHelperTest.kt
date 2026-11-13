package com.black.core.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
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

/** [PermissionHelper]의 설정 화면 인텐트와 사용 정보 접근·알림 접근 허용 판정 검증 */
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

    private fun contextWithUsageMode(mode: Int): Context {
        val appOpsManager = mockk<AppOpsManager>()
        // 실행 중인 SDK에 없는 메서드는 스텁할 수 없어 버전별로 하나만 지정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            every { appOpsManager.unsafeCheckOpNoThrow(any<String>(), any(), any()) } returns mode
        } else {
            @Suppress("DEPRECATION")
            every { appOpsManager.checkOpNoThrow(any<String>(), any(), any()) } returns mode
        }
        return mockk<Context> {
            every { getSystemService(Context.APP_OPS_SERVICE) } returns appOpsManager
            every { packageName } returns "com.black.app"
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
    fun test_03_usageAccessGrantedOnQOrAbove() {
        /** Given / When / Then **/
        assertTrue(PermissionHelper.isUsageAccessGranted(contextWithUsageMode(AppOpsManager.MODE_ALLOWED)))
    }

    /**
     * API 29 이상에서 허용이 아닌 모드면 거부로 판정되는지 검증
     *
     * Given: SDK 34, AppOps가 MODE_DEFAULT
     * When: isUsageAccessGranted 호출
     * Then: false
     */
    @Test
    @Config(sdk = [34])
    fun test_04_usageAccessDeniedOnQOrAbove() {
        /** Given / When / Then **/
        assertFalse(PermissionHelper.isUsageAccessGranted(contextWithUsageMode(AppOpsManager.MODE_DEFAULT)))
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
    fun test_05_usageAccessGrantedBelowQ() {
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
    fun test_06_usageAccessDeniedBelowQ() {
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
    fun test_07_notificationListenerEnabledWhenComponentListed() {
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
    fun test_08_notificationListenerDisabledWhenOnlyOtherComponentListed() {
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
    fun test_09_notificationListenerDisabledWhenSettingMissing() {
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
    fun test_10_opensNotificationListenerDetailOnROrAbove() {
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
    fun test_11_opensNotificationListenerListBelowR() {
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
    fun test_12_opensUsageAccessSettingsForApp() {
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
    fun test_13_fallsBackToUsageAccessListWhenAppScreenMissing() {
        /** Given **/
        val started = mutableListOf<Intent>()
        val failingContext = mockk<Context> {
            every { packageName } returns "com.black.app"
            every { startActivity(any()) } answers {
                started.add(firstArg())
                if (started.size == 1) throw android.content.ActivityNotFoundException()
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
    fun test_14_opensAppDetailsSettings() {
        /** Given / When **/
        PermissionHelper.showAppDetailsSettings(context)

        /** Then **/
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
