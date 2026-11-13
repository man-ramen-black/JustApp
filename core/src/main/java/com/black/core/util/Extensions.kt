package com.black.core.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity

object Extensions {

    /**  Context에서 activity 획득 */
    tailrec fun Context.activity(): ComponentActivity?
            = when(this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }

    /** Context에서 FragmentActivity 획득(supportFragmentManager가 필요한 경우) */
    tailrec fun Context.fragmentActivity(): FragmentActivity?
            = when(this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.fragmentActivity()
        else -> null
    }
}
