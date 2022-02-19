package com.black.code.contents.notification

import android.app.PendingIntent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NotificationViewModel : ViewModel() {
    enum class PendingIntentFlag(val value: Int) {
        CANCEL(PendingIntent.FLAG_CANCEL_CURRENT),
        UPDATE(PendingIntent.FLAG_UPDATE_CURRENT),
        NO_CREATE(PendingIntent.FLAG_NO_CREATE),
        ONE_SHOT(PendingIntent.FLAG_ONE_SHOT)
    }

    val notificationId by lazy { MutableLiveData(1) }
    val title by lazy { MutableLiveData("title") }
    val message by lazy { MutableLiveData("Message") }
    val contentIntentFlag by lazy { MutableLiveData(PendingIntentFlag.CANCEL) }
}