package com.black.app.usagelog.collect

import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * 집 전환을 받은 순서대로 하나씩 [handle]에 넘기는 대기열
 *
 * 재부팅 직후에는 추정 출발과 실제 도착이 거의 동시에 와, 전환마다 코루틴을 따로 띄우면 도착 판정이 추정 출발 저장보다 먼저 돌아 알림을 막지 못할 수 있음.
 * 만들 때 [scope]에서 소비 코루틴 하나를 띄우며, 한 전환의 처리 예외는 클래스 이름만 남기고 다음 전환으로 넘어감.
 * 처리 안의 시간 초과처럼 소비 코루틴 자신의 취소가 아닌 취소 예외도 처리 예외로 봄.
 * [close] 뒤에는 새 전환을 버리고, 소비 코루틴은 이미 받은 전환까지 처리한 뒤 끝남.
 *
 * @see UsageLogListenerService Todo 집 도착 처리에 쓰는 곳
 */
class HomeTransitionQueue(
    scope: CoroutineScope,
    private val handle: suspend (HomeTransition) -> Unit,
) {

    private val transitions = Channel<HomeTransition>(Channel.UNLIMITED)

    init {
        scope.launch {
            // 단일 소비자라 앞 전환의 출발 시각 저장이 끝난 뒤 다음 도착을 판정함
            for (transition in transitions) process(transition)
        }
    }

    /** 대기열에 넣음, [close] 뒤에는 버림 */
    fun offer(transition: HomeTransition) {
        transitions.trySend(transition)
    }

    fun close() {
        transitions.close()
    }

    private suspend fun process(transition: HomeTransition) {
        try {
            handle(transition)
        } catch (cancelled: CancellationException) {
            // 소비 코루틴 자신이 취소됐으면 그대로 끝냄
            currentCoroutineContext().ensureActive()
            // 자신의 취소가 아니면 이 한 건의 실패로 보고 다음 전환으로 넘어감
            Log.w("[HomeTransitionQueue] 집 전환 처리 실패: ${cancelled::class.java.simpleName}")
        } catch (failure: Exception) {
            // 한 건의 실패로 소비가 끝나 이후 전환이 모두 멈추지 않도록 다음 전환으로 넘어감
            Log.w("[HomeTransitionQueue] 집 전환 처리 실패: ${failure::class.java.simpleName}")
        }
    }
}
