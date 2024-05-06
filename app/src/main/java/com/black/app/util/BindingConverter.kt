package com.black.app.util

import androidx.databinding.InverseMethod
import com.black.app.ui.example.notification.NotificationViewModel

object BindingConverter {
    @JvmStatic
    @InverseMethod("stringToPendingIntentFlag")
    fun pendingIntentFlagToString(
        value: NotificationViewModel.PendingIntentFlag?
    ) : String {
        return (value ?: NotificationViewModel.PendingIntentFlag.CANCEL).toString()
    }

    @JvmStatic
    fun stringToPendingIntentFlag(
        value: String
    ) : NotificationViewModel.PendingIntentFlag {
        return NotificationViewModel.PendingIntentFlag.valueOf(value)
    }
}