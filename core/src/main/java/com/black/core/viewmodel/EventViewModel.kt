package com.black.core.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.black.core.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
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
}

fun interface EventObserver {
    fun onReceivedEvent(action: String, data: Any?)
}