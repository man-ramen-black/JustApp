package com.black.code.util

import androidx.databinding.InverseMethod
import com.black.code.ui.example.notification.NotificationViewModel

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