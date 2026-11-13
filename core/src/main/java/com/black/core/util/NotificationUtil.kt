package com.black.core.util

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
     * 포그라운드 서비스 상시 실행 알림 생성(중요도 낮음 채널 생성 포함)
     *
     * 작은 아이콘은 [createNotification]이 매니페스트 NOTIFICATION_ICON(없으면 앱 아이콘)으로 지정.
     * One UI 8.5 "그 외 알림"(백그라운드 활동) 분류 조건은 액션 버튼·커스텀 뷰 없는 FGS 알림, 분류된 알림은 Samsung 강제 자동 묶음에서도 제외.
     * setColorized FGS 알림은 SystemUI가 상단 FGS 구역으로 먼저 분류하므로 액션·색 미지정
     *
     * @param contentIntent 알림 터치 시 시작할 Activity 인텐트
     * @param requestCode contentIntent PendingIntent 요청 코드, ex) 알림 ID
     */
    fun createOngoingNotification(
        context: Context,
        channelId: String,
        channelName: String,
        title: String,
        message: String,
        contentIntent: Intent,
        requestCode: Int,
    ): Notification {
        createNotificationChannel(context, channelId, channelName, NotificationManagerCompat.IMPORTANCE_LOW)
        return createNotification(context, channelId, title, message) {
            it.setOngoing(true)
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            it.setContentIntent(PendingIntent.getActivity(context, requestCode, contentIntent, flags))
        }
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