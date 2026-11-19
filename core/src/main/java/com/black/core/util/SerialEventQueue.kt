package com.black.core.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 받은 이벤트를 받은 순서대로 하나씩 처리하는 대기열
 *
 * 콜백 스레드처럼 suspend할 수 없는 곳에서 [offer]로 넣고, 호출하는 쪽이 띄운 코루틴 하나에서 [consume]으로 처리함.
 * 앞 이벤트의 처리가 끝나기 전에 다음 이벤트의 처리가 시작되지 않아, 이벤트마다 코루틴을 띄울 때처럼 뒤 이벤트가 앞 이벤트의 저장보다 먼저 판정되지 않음.
 * 소비 코루틴을 띄우는 시점(앞 소비가 끝나기를 기다린 뒤 등)은 호출하는 쪽이 정함.
 */
class SerialEventQueue<T> {

    private val events = Channel<T>(Channel.UNLIMITED)

    /** 넣었으면 true, [close] 뒤면 버리고 false */
    fun offer(event: T): Boolean {
        return events.trySend(event).isSuccess
    }

    /** 새 이벤트를 받지 않음, 이미 받은 이벤트는 [consume]이 마저 처리 */
    fun close() {
        events.close()
    }

    /**
     * 닫히고 남은 이벤트를 다 처리할 때까지 받은 순서대로 [handle]
     *
     * 이벤트마다 이 코루틴이 취소됐는지 먼저 확인해 취소 뒤 남은 이벤트는 처리하지 않음.
     * [handle]의 실패는 [runSuspendCatching] 기준으로 [onFailure]에 넘기고 다음 이벤트로 넘어감.
     * 그래서 처리 안의 시간 초과처럼 이 코루틴의 취소가 아닌 취소 예외도 그 한 건의 실패로 끝나고 소비는 계속됨.
     * 한 대기열에는 소비자를 하나만 둠.
     */
    suspend fun consume(onFailure: (Throwable) -> Unit, handle: suspend (T) -> Unit) {
        for (event in events) {
            // 받아 둔 이벤트는 대기 없이 꺼내져 취소 확인이 일어나지 않으므로 직접 확인
            currentCoroutineContext().ensureActive()
            // 한 건의 실패로 소비가 끝나 이후 이벤트가 모두 멈추지 않도록 실패만 넘기고 계속
            runSuspendCatching { handle(event) }.onFailure(onFailure)
        }
    }
}
