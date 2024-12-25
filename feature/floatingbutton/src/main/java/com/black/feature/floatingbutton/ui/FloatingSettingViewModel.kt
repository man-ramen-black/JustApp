package com.black.feature.floatingbutton.ui

import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** [FloatingSettingFragment] */
@HiltViewModel
class FloatingSettingViewModel @Inject constructor(): EventViewModel() {
    companion object {
        const val EVENT_START = "start"
        const val EVENT_STOP = "stop"
    }

    fun onClickStart() {
        sendEvent(EVENT_START)
    }

    fun onClickStop() {
        sendEvent(EVENT_STOP)
    }
}