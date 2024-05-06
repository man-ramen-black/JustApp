package com.black.app.base.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

/**
 * https://github.com/android/architecture-samples/blob/todo-mvvm-live-kotlin/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/Event.kt
 * https://medium.com/prnd/mvvm%EC%9D%98-viewmodel%EC%97%90%EC%84%9C-%EC%9D%B4%EB%B2%A4%ED%8A%B8%EB%A5%BC-%EC%B2%98%EB%A6%AC%ED%95%98%EB%8A%94-%EB%B0%A9%EB%B2%95-6%EA%B0%80%EC%A7%80-31bb183a88ce
 * onClick 등의 이벤트 처리를 위해 observe하는 경우 Activity가 inactive -> active 되는 경우 등에 마지막 값을 다시 전달하게되므로,
 * 한 번 set한 값이 observe 시 한 번만 전달되도록 하는 LiveData Wrapper
 */
open class LiveEvent {
    private val liveData = MutableLiveData<Event>()
    private var foreverObserver : Observer<in Event>? = null

    fun send(action: String = "", data: Any? = null) {
        liveData.value = Event(action, data)
    }

    fun post(action: String = "", data: Any? = null) {
        liveData.postValue(Event(action, data))
    }

    @MainThread
    fun observe(owner: LifecycleOwner, onEvent: (action: String, data: Any?) -> Unit) {
        liveData.observe(owner) {
            if (!it.hasBeenHandled) {
                onEvent(it.action, it.data)
            }
        }
    }

    @MainThread
    fun observe(owner: LifecycleOwner, eventObserver: EventObserver) {
        observe(owner) { action, data ->
            eventObserver.onReceivedEvent(action, data)
        }
    }

    @MainThread
    fun observeForever(onEvent: (action: String, data: Any?) -> Unit) {
        removeObserver()
        foreverObserver = Observer {
            if (!it.hasBeenHandled) {
                onEvent(it.action, it.data)
            }
        }
        liveData.observeForever(foreverObserver!!)
    }

    @MainThread
    fun observeForever(eventObserver: EventObserver) {
        observeForever { action, data ->
            eventObserver.onReceivedEvent(action, data)
        }
    }

    @MainThread
    fun removeObserver() {
        liveData.removeObserver(foreverObserver ?: return)
    }

    private class Event(action: String, data: Any?) {
        var hasBeenHandled = false
            private set

        val action: String = action
            get() {
                hasBeenHandled = true
                return field
            }

        val data: Any? = data
            get() {
                hasBeenHandled = true
                return field
            }
    }
}