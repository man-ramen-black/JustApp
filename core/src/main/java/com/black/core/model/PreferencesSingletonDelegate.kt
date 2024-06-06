package com.black.core.model

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.GuardedBy
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceManager
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun preferences(name: String? = null): ReadOnlyProperty<Context, SharedPreferences> {
    return PreferencesSingletonDelegate(name)
}

/**
 * 참고 : [preferencesDataStore]
 */
class PreferencesSingletonDelegate(private val name: String? = null): ReadOnlyProperty<Context, SharedPreferences> {
    private val lock = Any()

    @GuardedBy("lock")
    @Volatile
    private var instance: SharedPreferences? = null

    /**
     * Gets the instance of the DataStore.
     *
     * @param thisRef must be an instance of [Context]
     * @param property not used
     */
    override fun getValue(thisRef: Context, property: KProperty<*>): SharedPreferences {
        return instance ?: synchronized(lock) {
            if (instance == null) {
                val applicationContext = thisRef.applicationContext
                instance = if (name == null) {
                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                } else {
                    applicationContext.getSharedPreferences("${applicationContext.packageName}.$name", Context.MODE_PRIVATE)
                }

            }
            instance!!
        }
    }
}