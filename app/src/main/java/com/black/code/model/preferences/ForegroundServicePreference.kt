package com.black.code.model.preferences

import android.content.Context
import com.black.code.base.model.BasePreferences

/**
 * Created by jinhyuk.lee on 2022/05/07
 **/
class ForegroundServicePreference(context: Context) : BasePreferences(context) {
    companion object {
        private const val KEY_FOREGROUND_SERVICE_ACTIVATED = "ForegroundServiceActivated"
        private const val KEY_TIME_CHECKER_PAUSE_TIME = "TimeCheckerPauseTime"
    }

    fun putForegroundServiceActivated(enabled: Boolean) {
        put(KEY_FOREGROUND_SERVICE_ACTIVATED, enabled)
    }

    fun getForegroundServiceActivated() : Boolean {
        return get(KEY_FOREGROUND_SERVICE_ACTIVATED, false)
    }

    fun putTimeCheckerPauseTimeMs(timeMs: Long) {
        putLong(KEY_TIME_CHECKER_PAUSE_TIME, timeMs)
    }

    fun getTimeCheckerPauseTimeMs() : Long {
        return getLong(KEY_TIME_CHECKER_PAUSE_TIME, 0)
    }
}