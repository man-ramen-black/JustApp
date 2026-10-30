package com.black.app.util

import android.content.BroadcastReceiver
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Created by jinhyuk.lee on 2024/07/29
 **/
object ComponentExtensions {

    fun ComponentActivity.launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job
        = lifecycleScope.launch(context, start, block)

    /**
     * onReceive가 반환된 뒤에도 [block]이 끝날 때까지 브로드캐스트 처리를 유지하며 실행
     *
     * goAsync 제한 시간(약 10초) 안에 끝나는 짧은 작업에만 사용
     */
    fun BroadcastReceiver.launchAsync(block: suspend CoroutineScope.() -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                block()
            } finally {
                pendingResult.finish()
            }
        }
    }
}