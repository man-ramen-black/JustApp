package com.black.core.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel

open class EventViewModel : ViewModel()  {
    private val event = LiveEvent()

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

    fun <T> LiveData<T>.observe(observer: Observer<T>) {
        this.observeForever(observer)
        addCloseable { this.removeObserver(observer) }
    }
}

fun interface EventObserver {
    fun onReceivedEvent(action: String, data: Any?)
}