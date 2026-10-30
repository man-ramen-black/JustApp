package com.black.core.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [PermissionHelper.showAppNotificationSettings]의 버전별 설정 화면 인텐트 검증 */
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
}
