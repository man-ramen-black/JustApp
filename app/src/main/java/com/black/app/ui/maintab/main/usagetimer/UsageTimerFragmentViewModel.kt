package com.black.app.ui.maintab.main.usagetimer

import androidx.lifecycle.MutableLiveData
import com.black.app.model.UsageTimerModel
import com.black.core.util.Log

/**
 * [UsageTimerFragment]
 * Created by jinhyuk.lee on 2022/05/08
 **/
class UsageTimerFragmentViewModel : com.black.core.viewmodel.EventViewModel() {
    companion object {
        const val EVENT_SHOW = "Show"
        const val EVENT_TOAST = "Toast"
        const val EVENT_DETACH_VIEW_IN_SERVICE = "DetachViewInService"
        const val EVENT_SHOW_ACCESSIBILITY = "ShowAccessibility"
        const val EVENT_SHOW_SELECT_APP = "ShowSelectApp"
    }

    val pauseRemainTime = MutableLiveData(0L)
    val pauseDurationMin = MutableLiveData(0)
    private var model : UsageTimerModel? = null

    fun setModel(model: UsageTimerModel) {
        this.model = model
    }

    fun init() {
        updatePauseRemainTime()
        pauseDurationMin.value = model?.getPauseDuration()
    }

    fun onClickShow() {
        sendEvent(EVENT_SHOW)
    }

    fun onClickSave() {
        model?.savePauseDuration(pauseDurationMin.value ?: 0)
        sendEvent(EVENT_TOAST, "Saved")
    }

    fun onClickPause() {
        val pauseDuration = pauseDurationMin.value ?: 0
        model?.savePauseDuration(pauseDuration)
        model?.pause(pauseDuration)
        updatePauseRemainTime()
        sendEvent(EVENT_DETACH_VIEW_IN_SERVICE)
        sendEvent(EVENT_TOAST, "Pause : ${pauseDuration}m")
    }

    fun onClickCancelPause() {
        model?.cancelPause()
        updatePauseRemainTime()
        sendEvent(EVENT_TOAST, "Pause canceled")
    }

    fun onFinishTimer() {
        Log.d("onFinishTimer")
        pauseRemainTime.value = 0
    }

    fun onClickAccessibility() {
        sendEvent(EVENT_SHOW_ACCESSIBILITY)
    }

    fun onClickSelectApp() {
        sendEvent(EVENT_SHOW_SELECT_APP)
    }

    private fun updatePauseRemainTime() {
        val pauseEndTime = model?.getPauseEndTime()?.takeIf { System.currentTimeMillis() < it }
        val pauseRemainTime = pauseEndTime?.let { it - System.currentTimeMillis() }
        Log.d("pauseEndTime : $pauseEndTime, pauseRemainTime : $pauseRemainTime")
        this.pauseRemainTime.value = pauseRemainTime
    }
}
