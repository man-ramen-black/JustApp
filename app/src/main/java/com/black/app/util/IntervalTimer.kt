package com.black.app.util

import android.os.Handler
import android.os.Looper

/**
 * 일정 주기로 동작 반복
 * Created by jinhyuk.lee on 2022/06/08
 **/
class IntervalTimer(private val interval: Long, private val callback: () -> Unit) {
    var isRunning = false
        private set

    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (isRunning) {
            return
        }
        isRunning = true
        run()
    }

    private fun run() {
        handler.postDelayed({
            callback()
            run()
        }, interval)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}