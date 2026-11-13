package com.black.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.black.core.util.Log

/**
 * 앱 내부 전용(RECEIVER_NOT_EXPORTED) 런타임 리시버 하나의 등록·해제와 등록 여부 보관
 *
 * 언제 등록·해제할지(서비스 수명, 켜짐 유지 구간 등)는 사용하는 곳이 정함.
 * 등록·해제 모두 applicationContext를 써 호출부가 넘기는 Context(서비스 등)가 달라도 같은 Context에서 해제.
 * NOT_EXPORTED여도 시스템이 보내는 브로드캐스트는 수신하고, 보호 브로드캐스트(화면 켜짐·꺼짐, 패키지 설치 등)는 다른 앱이 송신 불가
 */
class ReceiverRegistration(
    private val filter: IntentFilter,
    private val receiver: BroadcastReceiver,
) {
    /** 받은 브로드캐스트를 [handle]로 넘기는 리시버로 등록 */
    constructor(filter: IntentFilter, handle: (Context, Intent) -> Unit) : this(
        filter,
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handle(context, intent)
            }
        },
    )

    var isRegistered = false
        private set

    fun register(context: Context) {
        // 해제 없이 재호출 시 중복 등록으로 같은 브로드캐스트를 두 번 처리
        if (isRegistered) {
            Log.w("already registered : ${actions()}")
            return
        }

        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true
    }

    fun unregister(context: Context) {
        // 등록 없이 호출되면 해제할 리시버 없음
        if (!isRegistered) {
            return
        }

        context.applicationContext.unregisterReceiver(receiver)
        isRegistered = false
    }

    /** 로그 태그가 이 클래스라 어느 리시버인지 구분하는 데 쓰는 필터 action 목록 */
    private fun actions(): List<String> = (0 until filter.countActions()).map { filter.getAction(it) }
}
