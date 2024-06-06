package com.black.core.model

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.black.core.util.JsonUtil
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.lang.NumberFormatException

/**
 * Created by jinhyuk.lee on 2022/04/11
 **/
abstract class BasePreferences(private val context: Context) {
    abstract fun getPreferences(context: Context): SharedPreferences

    fun <T> flow(key: String, def: T, cls: Class<T>): Flow<T> {
        val preferences = getPreferences(context)
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                val value = get(key ?: return@OnSharedPreferenceChangeListener, def, cls)
                trySend(value)
            }

            // flow 생성 후 최초에 저장되어 있는 값 전달을 위해 직접 호출
            listener.onSharedPreferenceChanged(preferences, key)

            preferences.registerOnSharedPreferenceChangeListener(listener)

            awaitClose {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    fun put(key: String, value: String) {
        getPreferences(context).edit()
            .putString(key, value)
            .apply()
    }

    fun <T> put(key: String, value: T, cls: Class<T>) {
        getPreferences(context).edit()
            .apply {
                when (value) {
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    else -> putString(key, JsonUtil.to(value, cls) ?: return@apply)
                }
            }
            .apply()
    }

    fun get(key: String): String? {
        return getPreferences(context).all[key]?.toString()
    }
    fun <T> get(key: String, cls: Class<T>): T? {
        val value = get(key)
        return when (cls.name) {
            Int::class.java.name -> cls.cast(value?.toIntOrNull() ?: return null)
            Long::class.java.name -> cls.cast(value?.toLongOrNull() ?: return null)
            Float::class.java.name -> cls.cast(value?.toFloatOrNull() ?: return null)
            Boolean::class.java.name -> cls.cast(value?.toBoolean() ?: return null)
            String::class.java.name -> cls.cast(value ?: return null)
            else -> JsonUtil.from(value ?: return null, cls)
        }
    }

    fun <T> get(key: String, def: T, cls: Class<T>): T {
        return get(key, cls) ?: def
    }

    fun get(key: String, def: String) : String {
        return get(key) ?: def
    }

    fun put(key: String, value: Boolean) {
        getPreferences(context).edit()
            .putBoolean(key, value)
            .apply()
    }

    fun get(key: String, def: Boolean) : Boolean {
        val value = getPreferences(context).all[key]
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
        getPreferences(context).edit()
            .putInt(key, value)
            .apply()
    }

    fun get(key: String, def: Int) : Int {
        return try {
            getPreferences(context).all.getOrDefault(key, def).toString().toInt()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            def
        }
    }

    fun putLong(key: String, value: Long) {
        getPreferences(context).edit()
            .putLong(key, value)
            .apply()
    }

    fun getLong(key: String, def: Long) : Long {
        return try {
            getPreferences(context).all.getOrDefault(key, def).toString().toLong()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            def
        }
    }

    fun remove(key: String) {
        getPreferences(context).edit()
            .remove(key)
            .apply()
    }
}