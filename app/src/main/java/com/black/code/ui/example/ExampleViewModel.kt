package com.black.code.ui.example

import com.black.code.R
import com.black.code.base.viewmodel.EventViewModel

class ExampleViewModel : EventViewModel() {
    companion object {
        const val EVENT_SUBMIT_LIST = "SubmitList"
        const val EVENT_NAVIGATE_FRAGMENT = "NavigateFragment"
    }

    private val itemList = listOf(
        ExampleListAdapter.Item(R.string.fragment_name_text_editor, R.id.textEditorFragment, R.drawable.ic_editor),
        ExampleListAdapter.Item(R.string.fragment_name_service, R.id.serviceFragment, R.drawable.ic_android),
        ExampleListAdapter.Item(R.string.fragment_name_study_popup, R.id.studyPopupFragment, R.drawable.ic_quiz),
        ExampleListAdapter.Item(R.string.fragment_name_usage_timer, R.id.usageTimerFragment, R.drawable.ic_timer),
        ExampleListAdapter.Item(R.string.fragment_name_notification, R.id.notificationFragment, R.drawable.ic_notification),
        ExampleListAdapter.Item(R.string.fragment_name_recycler_view, R.id.recyclerViewFragment, R.drawable.ic_list),
        ExampleListAdapter.Item(R.string.fragment_name_retrofit, R.id.retrofitFragment, R.drawable.ic_http),
        ExampleListAdapter.Item(R.string.fragment_name_alarm, R.id.alarmFragment, R.drawable.ic_alarm),
        ExampleListAdapter.Item(R.string.fragment_name_architecture, R.id.architectureFragment, R.drawable.ic_architecture),
        ExampleListAdapter.Item(R.string.fragment_name_launcher, R.id.launcherFragment, R.drawable.ic_home),
        ExampleListAdapter.Item(R.string.fragment_name_etc, R.id.etcFragment, R.drawable.ic_etc)
    )

    fun initList() {
        sendEvent(EVENT_SUBMIT_LIST, itemList)
    }

    fun onItemClick(item: ExampleListAdapter.Item) {
        sendEvent(EVENT_NAVIGATE_FRAGMENT, item.fragmentId)
    }
}