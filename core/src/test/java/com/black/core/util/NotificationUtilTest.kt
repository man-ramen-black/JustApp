package com.black.core.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [NotificationUtil]의 기본 알림 아이콘 선택 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationUtilTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * NOTIFICATION_ICON meta-data가 있으면 그 아이콘을 기본 small icon으로 사용하는지 검증
     *
     * Given: 앱 meta-data에 NOTIFICATION_ICON 지정
     * When: 아이콘 지정 없이 createNotification 호출
     * Then: small icon이 meta-data 아이콘
     */
    @Test
    fun test_01_metaDataIconUsedAsDefaultSmallIcon() = runTest {
        /** Given **/
        val notificationIcon = android.R.drawable.stat_notify_more
        mutableApplicationInfo().metaData = Bundle().apply {
            putInt(NotificationUtil.META_DATA_NOTIFICATION_ICON, notificationIcon)
        }

        /** When **/
        val notification = NotificationUtil.createNotification(context, "channel", "title", "message")

        /** Then **/
        assertEquals(notificationIcon, notification.smallIcon.resId)
    }

    /**
     * NOTIFICATION_ICON meta-data가 없으면 앱 아이콘을 기본 small icon으로 사용하는지 검증
     *
     * Given: 앱 meta-data 없음, 앱 아이콘 지정
     * When: 아이콘 지정 없이 createNotification 호출
     * Then: small icon이 앱 아이콘
     */
    @Test
    fun test_02_applicationIconUsedWithoutMetaData() = runTest {
        /** Given **/
        val applicationIcon = android.R.drawable.sym_def_app_icon
        mutableApplicationInfo().apply {
            metaData = null
            icon = applicationIcon
        }

        /** When **/
        val notification = NotificationUtil.createNotification(context, "channel", "title", "message")

        /** Then **/
        assertEquals(applicationIcon, notification.smallIcon.resId)
    }

    /**
     * PackageManager가 돌려주는 자기 앱 ApplicationInfo 원본
     *
     * 이 객체를 수정하면 getApplicationInfo 결과에 반영
     */
    private fun mutableApplicationInfo(): ApplicationInfo =
        checkNotNull(shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).applicationInfo)
}
