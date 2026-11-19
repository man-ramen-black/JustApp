package com.black.test

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager

/** 알림 권한과 게시된 알림을 Robolectric 섀도로 다루는 테스트 헬퍼 */
object NotificationTestUtil {

    /** Android 13 이상 알림 권한 허용 */
    fun grantNotificationPermission(context: Context) {
        shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Android 13 이상 알림 권한 거부 */
    fun denyNotificationPermission(context: Context) {
        shadowOf(context.applicationContext as Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** NotificationManager 조회 */
    fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /** NotificationManager 섀도 조회 */
    fun shadowNotificationManager(context: Context): ShadowNotificationManager =
        shadowOf(notificationManager(context))

    /** 알림 제목(EXTRA_TITLE) */
    fun notificationTitle(notification: Notification): String =
        notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    /** 알림 본문(EXTRA_TEXT) */
    fun notificationText(notification: Notification): String =
        notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
}
