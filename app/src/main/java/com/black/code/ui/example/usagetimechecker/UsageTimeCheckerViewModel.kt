package com.black.code.ui.example.usagetimechecker

import androidx.lifecycle.MutableLiveData
import com.black.code.base.viewmodel.EventViewModel
import com.black.code.util.Util

/**
 * Created by jinhyuk.lee on 2022/05/08
 **/
class UsageTimeCheckerViewModel : EventViewModel() {
    companion object {
        const val EVENT_SHOW = "Show"
        const val EVENT_TOAST = "Toast"
    }

    val pauseRemainText = MutableLiveData("")
    val pauseDurationMin = MutableLiveData(0)
    private var model : UsageTimerModel? = null

    fun setModel(model: UsageTimerModel) {
        this.model = model
    }

    fun init() {
        updatePauseRemainText()
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
        updatePauseRemainText()
        sendEvent(EVENT_TOAST, "Pause complete")
    }

    fun onClickCancelPause() {
        model?.cancelPause()
        updatePauseRemainText()
        sendEvent(EVENT_TOAST, "Pause cancel")
    }

    private fun updatePauseRemainText() {
        val pauseEndTime = model?.getPauseEndTime()?.takeIf { System.currentTimeMillis() < it }
            ?: 0L
        pauseRemainText.value = pauseEndTime.takeIf { it != 0L }
            ?.let {
                val remainTime = it - System.currentTimeMillis()
                "Pause remain : ${Util.milliSecondsToTimeString("mm:ss", remainTime)}"
            }
            ?: ""
    }
}
