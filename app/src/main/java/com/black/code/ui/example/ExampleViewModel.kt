package com.black.code.ui.example

import androidx.navigation.NavDestination
import com.black.code.base.viewmodel.EventViewModel

class ExampleViewModel : EventViewModel() {
    companion object {
        const val EVENT_SUBMIT_LIST = "SubmitList"
        const val EVENT_NAVIGATE_FRAGMENT = "NavigateFragment"
    }

    private val list = ArrayList<NavDestination>()

    fun initList(list: List<NavDestination>) {
        this.list.addAll(list)
        sendEvent(EVENT_SUBMIT_LIST, list)
    }

    fun onItemClick(destination: NavDestination) {
        sendEvent(EVENT_NAVIGATE_FRAGMENT, destination)
    }
}