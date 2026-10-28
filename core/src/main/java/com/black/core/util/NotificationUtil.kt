package com.black.core.util

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtil {
    /** 앱 매니페스트에 선언하는 기본 알림 아이콘 meta-data 이름 */
    const val META_DATA_NOTIFICATION_ICON = "NOTIFICATION_ICON"

    /**
     * https://developer.android.com/training/notify-user/build-notification?hl=ko#Priority
     * 알림 채널이 만들어진 상태라면 아무 작업도 실행되지 않으므로 이 코드를 반복적으로 호출하는 것이 안전합니다.
     * Channel은 Android P(API 28) 이상
     *
     * @param importance ex) NotificationManagerCompat.IMPORTANCE_DEFAULT
     */
    fun createNotificationChannel(context: Context, channelId: String, channelName: String, importance: Int = NotificationManagerCompat.IMPORTANCE_MAX, onCustomizeChannel: ((NotificationChannelCompat.Builder) -> Unit)? = null) {
        val channelBuilder = NotificationChannelCompat
            .Builder(channelId, importance)
            .setName(channelName)
        onCustomizeChannel?.invoke(channelBuilder)
        NotificationManagerCompat.from(context)
            .createNotificationChannel(channelBuilder.build())
    }

    fun deleteNotificationChannel(context: Context, channelId: String) {
        NotificationManagerCompat.from(context).deleteNotificationChannel(channelId)
    }

    fun createNotification(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        @DrawableRes pushIconRes: Int? = null,
        onCustomizeNotification: ((NotificationCompat.Builder) -> Unit)? = null
    ) : Notification {
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(pushIconRes ?: getDefaultNotificationIcon(context))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .also { onCustomizeNotification?.invoke(it) }
        return notificationBuilder.build()
    }

    /**
     * 매니페스트 NOTIFICATION_ICON meta-data 아이콘, 없으면 앱 아이콘
     *
     * 불투명한 런처 아이콘은 상태 표시줄에서 알파만 남아 흰 사각형으로 표시되므로 단색 알림 아이콘 우선
     */
    @DrawableRes
    fun getDefaultNotificationIcon(context: Context): Int {
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        return applicationInfo.metaData
            ?.getInt(META_DATA_NOTIFICATION_ICON, 0)
            ?.takeIf { it != 0 }
            ?: applicationInfo.icon
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showNotification(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        notificationId: Int = 1,
        @DrawableRes pushIconRes: Int? = null,
        onCustomizeNotification: ((NotificationCompat.Builder) -> Unit)? = null
    ) {
        NotificationManagerCompat.from(context)
            .notify(
                notificationId,
                createNotification(
                    context,
                    channelId,
                    title,
                    message,
                    pushIconRes,
                    onCustomizeNotification
                )
            )
    }
}