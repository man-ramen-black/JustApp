package com.black.code.base.viewmodel

import androidx.lifecycle.ViewModel

open class EventViewModel : ViewModel() {
    val event by lazy { LiveEvent() }
}

interface EventObserver {
    fun onReceivedEvent(action: String, data: Any?)
}