package com.black.code.model.preferences

import android.content.Context
import com.black.code.base.model.BasePreferences

/**
 * Created by jinhyuk.lee on 2022/05/07
 **/
class ForegroundServicePreference(context: Context) : BasePreferences(context) {
    companion object {
        private const val KEY_FOREGROUND_SERVICE_ACTIVATED = "ForegroundServiceActivated"
        private const val KEY_USAGE_TIMER_PAUSE_END_TIME = "UsageTimerPauseEndTime"
        private const val KEY_USAGE_TIMER_PAUSE_DURATION = "UsageTimerPauseDuration"
    }

    fun putForegroundServiceActivated(enabled: Boolean) {
        put(KEY_FOREGROUND_SERVICE_ACTIVATED, enabled)
    }

    fun getForegroundServiceActivated() : Boolean {
        return get(KEY_FOREGROUND_SERVICE_ACTIVATED, false)
    }

    fun putUsageTimerPauseEndTime(timestamp: Long) {
        putLong(KEY_USAGE_TIMER_PAUSE_END_TIME, timestamp)
    }

    fun getUsageTimerPauseEndTime() : Long {
        return getLong(KEY_USAGE_TIMER_PAUSE_END_TIME, 0)
    }

    fun putUsageTimerPauseDuration(durationMinutes: Int) {
        put(KEY_USAGE_TIMER_PAUSE_DURATION, durationMinutes)
    }

    fun getUsageTimerPauseDuration() : Int {
        return get(KEY_USAGE_TIMER_PAUSE_DURATION, 3)
    }
}