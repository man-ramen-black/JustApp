package com.black.app.util

import android.app.Notification
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.black.app.R

object NotificationUtil {
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
            .setSmallIcon(pushIconRes ?: R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .also { onCustomizeNotification?.invoke(it) }
        return notificationBuilder.build()
    }

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