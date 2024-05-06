package com.black.app.ui.example

import com.black.app.base.viewmodel.EventViewModel

class ExampleViewModel : EventViewModel() {
    companion object {
        const val EVENT_NAVIGATE_DIRECTION = "NavigateDirection"
    }

    fun onItemClick(item: ExampleListAdapter.Item) {
        sendEvent(EVENT_NAVIGATE_DIRECTION, item.navDirection)
    }
}