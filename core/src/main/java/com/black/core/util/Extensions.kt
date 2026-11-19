package com.black.core.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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

    /**
     * 이 앱의 시작 액티비티를 여는 인텐트이며, 시작 액티비티가 없으면 null
     *
     * 기능 모듈은 앱의 화면 클래스를 모르므로 알림을 눌러 앱을 열 때 사용.
     */
    fun Context.appLaunchIntent(): Intent? = packageManager.getLaunchIntentForPackage(packageName)
}
