package com.black.app.model.preferences

import android.content.Context
import android.content.SharedPreferences
import com.black.core.model.BasePreferences
import com.black.core.model.preferences

/**
 * Created by jinhyuk.lee on 2022/05/07
 **/
class ForegroundServicePreference(context: Context) : BasePreferences(context) {
    companion object {
        private val Context.foregroundPreferences by preferences("Foreground")

        private const val KEY_FOREGROUND_SERVICE_ACTIVATED = "ForegroundServiceActivated"
    }

    override fun getPreferences(context: Context): SharedPreferences {
        return context.foregroundPreferences
    }

    fun putForegroundServiceActivated(enabled: Boolean) {
        put(KEY_FOREGROUND_SERVICE_ACTIVATED, enabled)
    }

    fun getForegroundServiceActivated() : Boolean {
        return get(KEY_FOREGROUND_SERVICE_ACTIVATED, false)
    }
}