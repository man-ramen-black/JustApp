package com.black.app.model

import android.content.Context
import com.black.app.model.preferences.ForegroundServicePreference

/**
 * Created by jinhyuk.lee on 2022/05/09
 **/
class UsageTimerModel(context: Context) {
    private val preference = ForegroundServicePreference(context)

    fun savePauseDuration(pauseDurationMin: Int) {
        preference.putUsageTimerPauseDuration(pauseDurationMin)
    }

    fun pause(pauseDurationMin: Int) {
        preference.putUsageTimerPauseEndTime(System.currentTimeMillis() + (pauseDurationMin * 1000 * 60))
    }

    fun cancelPause() {
        preference.putUsageTimerPauseEndTime(0)
    }

    fun getPauseDuration() : Int {
        return preference.getUsageTimerPauseDuration()
    }

    fun getPauseEndTime() : Long {
        return preference.getUsageTimerPauseEndTime()
    }
}