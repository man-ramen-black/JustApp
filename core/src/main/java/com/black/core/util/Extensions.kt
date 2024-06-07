package com.black.core.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

object Extensions {
    fun <T> T.ifThen(isTrue: Boolean, then: T.() -> Unit): T = apply {
        if (isTrue) then()
    }

    /**  Context에서 activity 획득 */
    tailrec fun Context.activity(): ComponentActivity?
            = when(this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
}