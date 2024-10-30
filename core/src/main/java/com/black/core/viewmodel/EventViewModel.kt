package com.black.core.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.black.core.util.Extensions.collectOnMain
import com.black.core.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

open class EventViewModel : ViewModel()  {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val event = LiveEvent()

    val eventFlow = MutableSharedFlow<Event>()

    @MainThread
    fun sendEvent(action: String = "", data: Any? = null)
        = event.send(action, data)

    /**
     * 백그라운드 스레드에서 전송 시 사용
     */
    fun postEvent(action: String = "", data: Any? = null)
        = event.post(action, data)

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
    ): Job = eventFlow.collectOnMain(scope) { (action, data) -> collector.onEventCollected(action, data) }

    fun collectEvent(
        scope: CoroutineScope,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        collector: EventCollector
    ): Job = eventFlow.collectOnMain(scope, coroutineContext) { (action, data) -> collector.onEventCollected(action, data) }

    /** Event를 리턴하지 않고 특정 로직만 실행하는 Flow를 Flow<Event>로 변환 */
    fun Flow<*>.toEventFlow(): Flow<Event>
        = map { null }.filterNotNull()

    @Deprecated("Use collectEvent")
    @MainThread
    fun observeEvent(owner: LifecycleOwner, eventObserver: EventObserver)
        = event.observe(owner, eventObserver)

    @Deprecated("Use collectEvent")
    @MainThread
    fun removeEventObservers(owner: LifecycleOwner)
        = event.removeObservers(owner)

    @Deprecated("Use collectEvent")
    @MainThread
    fun observeEventForever(eventObserver: EventObserver)
        = event.observeForever(eventObserver)


    @Deprecated("Use collectEvent")
    @MainThread
    fun removeEventObserver(observer: EventObserver)
        = event.removeObserver(observer)

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

    fun <T> Flow<T>.stateIn(
        initialValue: T,
        // 수집이 시작될 때 발행 시작, Collector가 없으면 5초 대기 후 발행 중지
        // 화면 회전 시 뷰 재생성 등으로 일시적으로 구독이 끊겼을때 스트림이 즉시 재생성되는 것을 방지
        sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000),
        coroutineScope: CoroutineScope = viewModelScope
    ) = this.stateIn(
        coroutineScope,
        sharingStarted,
        initialValue
    )

    fun EventViewModel.launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ) = viewModelScope.launch(context, start, block)
}

@Deprecated("Use EventCollector")
fun interface EventObserver {
    fun onReceivedEvent(action: String, data: Any?)
}

fun interface EventCollector {
    suspend fun onEventCollected(action: String, data: Any?)
}

data class Event(val action: String, val data: Any?)