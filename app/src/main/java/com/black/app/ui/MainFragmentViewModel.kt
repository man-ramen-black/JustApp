package com.black.app.ui

class MainFragmentViewModel : com.black.core.viewmodel.EventViewModel() {
    companion object {
        const val EVENT_NAVIGATE_DIRECTION = "NavigateDirection"
    }

    fun onItemClick(item: MainItem) {
        sendEvent(EVENT_NAVIGATE_DIRECTION, item.navDirection)
    }
}