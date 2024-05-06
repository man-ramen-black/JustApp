package com.black.app.base.model

import android.content.Context
import androidx.preference.PreferenceManager
import java.lang.NumberFormatException

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

    fun put(key: String, value: Boolean) {
        preferences.edit()
            .putBoolean(key, value)
            .apply()
    }

    fun get(key: String, def: Boolean) : Boolean {
        val value = preferences.all[key]
        return when {
            value?.toString().equals("true", true) -> {
                true
            }
            value?.toString().equals("false", true) -> {
                false
            }
            else -> {
                def
            }
        }
    }

    fun put(key: String, value: Int) {
        preferences.edit()
            .putInt(key, value)
            .apply()
    }

    fun get(key: String, def: Int) : Int {
        return try {
            preferences.all.getOrDefault(key, def).toString().toInt()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            def
        }
    }

    fun putLong(key: String, value: Long) {
        preferences.edit()
            .putLong(key, value)
            .apply()
    }

    fun getLong(key: String, def: Long) : Long {
        return try {
            preferences.all.getOrDefault(key, def).toString().toLong()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            def
        }
    }

    fun remove(key: String) {
        preferences.edit()
            .remove(key)
            .apply()
    }
}