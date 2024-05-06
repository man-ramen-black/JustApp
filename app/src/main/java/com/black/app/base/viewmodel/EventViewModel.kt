package com.black.app.base.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel

open class EventViewModel : ViewModel() {
    @Deprecated("Use method")
    val event by lazy { LiveEvent() }

    fun sendEvent(action: String = "", data: Any? = null) {
        event.send(action, data)
    }

    fun postEvent(action: String = "", data: Any? = null) {
        event.post(action, data)
    }

    @MainThread
    fun observeEvent(owner: LifecycleOwner, onEvent: (action: String, data: Any?) -> Unit) {
        event.observe(owner, onEvent)
    }

    @MainThread
    fun observeEvent(owner: LifecycleOwner, eventObserver: EventObserver) {
        event.observe(owner, eventObserver)
    }

    @MainThread
    fun observeEventForever(onEvent: (action: String, data: Any?) -> Unit) {
        event.observeForever(onEvent)
    }

    @MainThread
    fun observeEventForever(eventObserver: EventObserver) {
        event.observeForever(eventObserver)
    }

    @MainThread
    fun removeEventObserver() {
        event.removeObserver()
    }
}

interface EventObserver {
    fun onReceivedEvent(action: String, data: Any?)
}