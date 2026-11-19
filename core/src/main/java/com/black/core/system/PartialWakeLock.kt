package com.black.core.system

import android.content.Context
import android.os.PowerManager
import kotlin.time.Duration

/**
 * 제한 시간이 있는 부분 wake lock(화면이 꺼져도 CPU를 깨워 둠) 획득·해제
 *
 * 쓰는 모듈의 매니페스트에 WAKE_LOCK 권한 선언이 필요
 *
 * @param tag 시스템 wake lock 목록에 보이는 이름(`앱:기능` 형식)
 */
class PartialWakeLock(context: Context, private val tag: String) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * [timeout] 동안 wake lock 획득
     *
     * 이미 쥐고 있으면 풀고 새 제한 시간으로 다시 획득
     */
    fun acquire(timeout: Duration) {
        release()
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            .apply { acquire(timeout.inWholeMilliseconds) }
    }

    /** 쥐고 있는 wake lock 해제, 없으면 무시 */
    fun release() {
        // 제한 시간이 지나 이미 풀린 wake lock을 release하면 예외
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
}
