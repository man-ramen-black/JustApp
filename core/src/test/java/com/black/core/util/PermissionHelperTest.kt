package com.black.core.util

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
 * [PermissionHelper.isGranted]·[PermissionHelper.isNotificationPermissionGranted]·[PermissionHelper.areNotificationsEnabled]의 판단 검증
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

    private companion object {
        /** PermissionHelper가 쓰는 비공개 접근성 상세 화면 액션 */
        const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

        /** 설정 앱의 항목 하이라이트 extras 키(PermissionHelper의 private 상수와 같은 값) */
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    }
}
