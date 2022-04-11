package com.black.code.base.model

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Created by jinhyuk.lee on 2022/04/11
 **/
open class BasePreferences(private val context: Context, private val name: String? = null) {

    private val preferences by lazy {
        val appContext = context.applicationContext
        if (name.isNullOrBlank()) {
            PreferenceManager.getDefaultSharedPreferences(appContext.applicationContext)
        } else {
            appContext.getSharedPreferences("${appContext.packageName}.$name", Context.MODE_PRIVATE)
        }
    }

    fun put(key: String, value: String) {
        preferences.edit()
            .putString(key, value)
            .apply()
    }

    fun get(key: String, def: String) : String {
        return preferences.all.getOrDefault(key, def).toString()
    }

    fun remove(key: String) {
        preferences.edit()
            .remove(key)
            .apply()
    }
}