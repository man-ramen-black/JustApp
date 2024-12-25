package com.black.feature.floatingbutton.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.black.core.model.BaseDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FloatingDataStore @Inject constructor(
    @ApplicationContext context: Context
): BaseDataStore(context) {
    companion object {
        val Context.floatingDataStore by preferencesDataStore(name = "floating")

        private val KEY_FLOATING_MARGIN = floatPreferencesKey("floating_margin")
        private val KEY_FLOATING_OPACITY = floatPreferencesKey("floating_opacity")
        private val KEY_FLOATING_PADDING = floatPreferencesKey("floating_padding")
        private val KEY_FLOATING_SIZE = floatPreferencesKey("floating_size")
        private val KEY_FLOATING_RADIUS = floatPreferencesKey("floating_radius")
    }

    override fun getDataStore(context: Context): DataStore<Preferences> {
        return context.floatingDataStore
    }

    suspend fun putMargin(margin: Float) {
        update(KEY_FLOATING_MARGIN, margin)
    }

    fun getMarginFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_MARGIN)
    }

    suspend fun putOpacity(alpha: Float) {
        update(KEY_FLOATING_OPACITY, alpha)
    }

    fun getOpacityFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_OPACITY)
    }

    suspend fun putPadding(padding: Float) {
        update(KEY_FLOATING_PADDING, padding)
    }

    fun getPaddingFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_PADDING)
    }

    suspend fun putSize(size: Float) {
        update(KEY_FLOATING_SIZE, size)
    }

    fun getSizeFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_SIZE)
    }

    suspend fun putRadius(radius: Float) {
        update(KEY_FLOATING_RADIUS, radius)
    }

    fun getRadiusFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_RADIUS)
    }
}