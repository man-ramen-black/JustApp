package com.black.core.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.black.core.util.Extensions.collect
import com.black.core.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

open class EventViewModel : ViewModel()  {

    companion object {
        fun Flow<Event>.collectEvent(lifecycleOwner: LifecycleOwner, collector: EventCollector): Job {
            return collect(lifecycleOwner) { (action, data) -> collector.onEventCollected(action, data) }
        }
    }

    private val jobs = ConcurrentHashMap<String, Job>()
    private val event = LiveEvent()

    val eventFlow = MutableSharedFlow<Event>()

    private val pendingEvents = ArrayDeque<ViewModelEvent>()
    private val mutableEvents = MutableSharedFlow<ViewModelEvent>()

    /**
     * 타입 이벤트 스트림. 구독 전 누적분은 첫 구독자에게 순서대로 전달, 이후 구독자는 구독 후 발생분만 수신.
     * 구독자가 없는 동안의 이벤트는 ViewModel 수명 동안 누적되므로 화면(구독자)이 곧 붙는 일회성 이벤트 용도
     */
    val events: Flow<ViewModelEvent> = mutableEvents.onSubscription {
        while (true) {
            val pending = synchronized(pendingEvents) { pendingEvents.removeFirstOrNull() } ?: break
            emit(pending)
        }
    }

    /**
     * 타입 이벤트 전송. 구독자가 없으면 누적, 있으면 즉시 전달.
     * 전송은 [viewModelScope](Main)로 직렬화되어 호출 순서 보장 — Main 디스패처 단일 구독 전제의 계약
     */
    fun sendEvent(event: ViewModelEvent) {
        launch {
            if (mutableEvents.subscriptionCount.value == 0) {
                synchronized(pendingEvents) { pendingEvents.addLast(event) }
            } else {
                mutableEvents.emit(event)
            }
        }
    }

    @MainThread
    fun sendEvent(action: String = "", data: Any? = null) {
        launch { eventFlow.emit(Event(action, data)) }
        // TODO 삭제
        event.send(action, data)
    }

    /**
     * 백그라운드 스레드에서 전송 시 사용
     */
    fun postEvent(action: String = "", data: Any? = null)
        = event.post(action, data)

    @MainThread
    fun observeEvent(owner: LifecycleOwner, eventObserver: EventObserver)
        = event.observe(owner, eventObserver)

    @MainThread
    fun removeEventObservers(owner: LifecycleOwner)
        = event.removeObservers(owner)

    @MainThread
    fun observeEventForever(eventObserver: EventObserver)
        = event.observeForever(eventObserver)

    @MainThread
    fun removeEventObserver(observer: EventObserver)
        = event.removeObserver(observer)

    /** [viewModelScope]에서 코루틴 실행 */
    protected fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job = viewModelScope.launch(context, start, block)

    fun launchSingle(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ) {
        val methodName = Thread.currentThread().stackTrace[3].methodName
        if (jobs[methodName]?.isCompleted == false) {
            Log.v("$methodName job is not completed")
            return
        }
        jobs[methodName] = viewModelScope.launch(context, start, block)
    }

    override fun onCleared() {
        super.onCleared()
        Log.v(this::class.java.simpleName)
    }

    fun collectEvent(
        lifecycleOwner: LifecycleOwner,
        collector: EventCollector
    ): Job = collectEvent(lifecycleOwner.lifecycleScope, collector)

    fun collectEvent(
        lifecycleOwner: LifecycleOwner,
        coroutineContext: CoroutineContext = Dispatchers.Main,
        collector: EventCollector
    ): Job = collectEvent(lifecycleOwner.lifecycleScope, coroutineContext, collector)

    fun collectEvent(
        scope: CoroutineScope,
        collector: EventCollector
    ): Job = eventFlow.collect(scope) { (action, data) -> collector.onEventCollected(action, data) }

    fun collectEvent(
        scope: CoroutineScope,
        coroutineContext: CoroutineContext = Dispatchers.Main,
        collector: EventCollector
    ): Job = eventFlow.collect(scope, coroutineContext) { (action, data) -> collector.onEventCollected(action, data) }
}

fun interface EventObserver {
    fun onReceivedEvent(action: String, data: Any?)
}

fun interface EventCollector {
    suspend fun onEventCollected(action: String, data: Any?)
}