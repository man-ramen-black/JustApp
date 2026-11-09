package com.black.core.util

import android.app.Activity
import android.widget.Toast
import java.lang.ref.WeakReference

object BackPressHelper {
    // Tip) Toast.LENGTH_SHORT : 2s, Toast.LENGTH_LONG : 3.5s
    private const val CLOSE_DELAY = 2000

    private var backPressedTime : Long = 0L
    private var toast: WeakReference<Toast>? = null

    fun onBackPressed(activity: Activity, message: String, onKill: () -> Unit) {
        if (System.currentTimeMillis() - backPressedTime < CLOSE_DELAY) {
            toast?.get()?.cancel()
            // 종료 뒤 다시 진입한 화면에서 첫 뒤로 가기가 바로 종료로 이어지지 않도록 초기화
            backPressedTime = 0L
            onKill()

        } else {
            backPressedTime = System.currentTimeMillis()
            // Toast 길이는 LENGTH_SHORT·LENGTH_LONG만 유효(LENGTH_SHORT가 CLOSE_DELAY와 같은 2초)
            val toast = Toast.makeText(activity, message, Toast.LENGTH_SHORT).also { it.show() }
            this.toast = WeakReference(toast)
        }
    }
}