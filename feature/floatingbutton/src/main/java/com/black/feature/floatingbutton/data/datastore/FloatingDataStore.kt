package com.black.feature.floatingbutton.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.black.core.model.BaseDataStore
import com.black.core.model.OverlayPosition
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.core.model.blackDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FloatingDataStore @Inject constructor(
    @ApplicationContext context: Context
): BaseDataStore(context) {
    companion object {
        val Context.floatingDataStore by blackDataStore("floating")

        private val KEY_FLOATING_MARGIN = floatPreferencesKey("floating_margin")
        private val KEY_FLOATING_OPACITY = floatPreferencesKey("floating_opacity")
        private val KEY_FLOATING_PADDING = floatPreferencesKey("floating_padding")
        private val KEY_FLOATING_SIZE = floatPreferencesKey("floating_size")
        private val KEY_FLOATING_RADIUS = floatPreferencesKey("floating_radius")
        private val KEY_FLOATING_POSITION = stringPreferencesKey("floating_position")

        /**
         * 저장된 위치가 없거나 읽을 수 없을 때의 표시 위치
         *
         * 화면 왼쪽 아래.
         */
        val DEFAULT_POSITION = OverlayPosition(HorizontalAnchor.LEFT, VerticalAnchor.BOTTOM, marginBottomDp = 25)
    }

    override fun getDataStore(context: Context): DataStore<Preferences> {
        return context.floatingDataStore
    }

    suspend fun putMargin(margin: Float): Result<Unit> {
        return update(KEY_FLOATING_MARGIN, margin)
    }

    fun getMarginFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_MARGIN)
    }

    suspend fun putOpacity(alpha: Float): Result<Unit> {
        return update(KEY_FLOATING_OPACITY, alpha)
    }

    fun getOpacityFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_OPACITY)
    }

    suspend fun putPadding(padding: Float): Result<Unit> {
        return update(KEY_FLOATING_PADDING, padding)
    }

    fun getPaddingFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_PADDING)
    }

    suspend fun putSize(size: Float): Result<Unit> {
        return update(KEY_FLOATING_SIZE, size)
    }

    fun getSizeFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_SIZE)
    }

    suspend fun putRadius(radius: Float): Result<Unit> {
        return update(KEY_FLOATING_RADIUS, radius)
    }

    fun getRadiusFlow(): Flow<Float?> {
        return flow(KEY_FLOATING_RADIUS)
    }

    /** 표시 위치 저장, JSON 변환에 실패하면 실패 결과 */
    suspend fun putPosition(position: OverlayPosition): Result<Unit> {
        return position.saveAsJson { update(KEY_FLOATING_POSITION, it) }
    }

    /** 표시 위치, 저장값이 없거나 읽을 수 없으면 [DEFAULT_POSITION] */
    fun getPositionFlow(): Flow<OverlayPosition> {
        return OverlayPosition.fromJsonFlow(flow(KEY_FLOATING_POSITION), DEFAULT_POSITION)
    }
}
