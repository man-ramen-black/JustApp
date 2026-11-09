package com.black.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.black.core.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

open class EventViewModel : ViewModel()  {

    /** 구독 중인 구독자마다 받을 때까지 이벤트를 보관하는 수신함(이 목록을 잠금 대상으로도 사용) */
    private val inboxes = mutableListOf<Channel<ViewModelEvent>>()

    /** 구독자가 없는 동안 보낸 이벤트(다음 첫 구독자에게 순서대로 전달) */
    private val pendingEvents = ArrayDeque<ViewModelEvent>()

    /**
     * 타입 이벤트 스트림. 각 이벤트는 그때 구독 중인 모든 구독자에게 한 번씩 전달
     *
     * 구독자가 없는 동안 보낸 이벤트는 누적했다가 첫 구독자에게 순서대로 전달하고, 추가 구독자는 구독 이후 보낸 이벤트만 수신.
     * 마지막 구독자의 수집이 끝날 때 그 구독자가 아직 받지 못한 이벤트는 누적분으로 되돌림.
     * 그래서 핸들러가 이전 이벤트를 처리하며 멈춘 사이 보낸 이벤트도 백그라운드 전환으로 수집이 취소될 때 사라지지 않음
     */
    val events: Flow<ViewModelEvent> = flow {
        val inbox = Channel<ViewModelEvent>(Channel.UNLIMITED)
        synchronized(inboxes) {
            // 첫 구독자면 누적분을 먼저 수신함에 이동
            if (inboxes.isEmpty()) {
                while (pendingEvents.isNotEmpty()) inbox.trySend(pendingEvents.removeFirst())
            }
            inboxes.add(inbox)
        }

        try {
            for (event in inbox) emit(event)
        } finally {
            synchronized(inboxes) {
                inboxes.remove(inbox)
                inbox.close()
                // 마지막 구독자면 받지 못한 이벤트를 누적분으로 복귀
                if (inboxes.isEmpty()) {
                    while (true) pendingEvents.addLast(inbox.tryReceive().getOrNull() ?: break)
                }
            }
        }
    }

    /** 타입 이벤트 전송(호출한 순서대로 전달, 어느 스레드에서 불러도 됨) */
    fun sendEvent(event: ViewModelEvent) {
        synchronized(inboxes) {
            if (inboxes.isEmpty()) {
                pendingEvents.addLast(event)
            } else {
                inboxes.forEach { it.trySend(event) }
            }
        }
    }

    /** [viewModelScope]에서 코루틴 실행 */
    protected fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job = viewModelScope.launch(context, start, block)

    override fun onCleared() {
        super.onCleared()
        Log.v(this::class.java.simpleName)
    }
}
