package com.black.core.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

/**
 * https://github.com/android/architecture-samples/blob/todo-mvvm-live-kotlin/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/Event.kt
 * https://medium.com/prnd/mvvm%EC%9D%98-viewmodel%EC%97%90%EC%84%9C-%EC%9D%B4%EB%B2%A4%ED%8A%B8%EB%A5%BC-%EC%B2%98%EB%A6%AC%ED%95%98%EB%8A%94-%EB%B0%A9%EB%B2%95-6%EA%B0%80%EC%A7%80-31bb183a88ce
 * onClick 등의 이벤트 처리를 위해 observe하는 경우 Activity가 inactive -> active 되는 경우 등에 마지막 값을 다시 전달하게되므로,
 * set한 값이 observer 마다 한 번씩만 전달되도록 하는 LiveData Wrapper
 */
open class LiveEvent {
    private val liveData = SingleLiveData<Event>()

    @MainThread
    fun send(action: String = "", data: Any? = null) {
        liveData.value = Event(action, data)
    }

    fun post(action: String = "", data: Any? = null) {
        liveData.postValue(Event(action, data))
    }

    @MainThread
    fun observe(owner: LifecycleOwner, eventObserver: EventObserver) {
        liveData.observe(owner, object: Observer<Event> {
            override fun onChanged(value: Event) {
                eventObserver.onReceivedEvent(value.action, value.data)
            }

            override fun hashCode(): Int {
                return eventObserver.hashCode()
            }
        })
    }

    @MainThread
    fun removeObservers(owner: LifecycleOwner) {
        liveData.removeObservers(owner)
    }

    @MainThread
    fun observeForever(eventObserver: EventObserver) {
        liveData.observeForever(object: Observer<Event> {
            override fun onChanged(value: Event) {
                eventObserver.onReceivedEvent(value.action, value.data)
            }
            override fun hashCode(): Int {
                return eventObserver.hashCode()
            }
        })
    }

    @MainThread
    fun removeObserver(eventObserver: EventObserver) {
        liveData.removeObserver(object: Observer<Event> {
            override fun onChanged(value: Event) {
                eventObserver.onReceivedEvent(value.action, value.data)
            }
            override fun hashCode(): Int {
                return eventObserver.hashCode()
            }
        })
    }
}