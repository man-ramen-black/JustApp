package com.black.feature.floatingbutton.ui.floating

import com.black.core.di.Hilt
import com.black.core.di.HiltModule
import com.black.core.viewmodel.EventViewModel
import com.black.feature.floatingbutton.data.datastore.FloatingDataStore
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** [FloatingView] */
@Singleton
class FloatingViewModel @Inject constructor(
    dataStore: FloatingDataStore,
    @Named(HiltModule.NAME_MAIN_SCOPE)
    private val mainScope: CoroutineScope
): EventViewModel() {

    @dagger.hilt.EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EntryPoint {
        @Singleton
        val floatingViewModel: FloatingViewModel
    }

    companion object {
        const val EVENT_HOME = "home"
        const val EVENT_BACK = "back"
        const val EVENT_VOLUME_UP = "volumeUp"
        const val EVENT_VOLUME_DOWN = "volumeDown"
    }

    val size = dataStore.getSizeFlow()
        .stateIn(35f)

    val padding = dataStore.getPaddingFlow()
        .mapNotNull { it ?: 3f }
        .stateIn(3f)

    val margin = dataStore.getMarginFlow()
        .mapNotNull { it ?: 2f }
        .stateIn(2f)

    val radius = dataStore.getRadiusFlow()
        .mapNotNull { it ?: 3f }
        .stateIn(3f)

    val opacity = dataStore.getOpacityFlow()
        .stateIn(0.3f)

    fun onClickHome() {
        Hilt
        sendEvent(EVENT_HOME)
    }

    fun onClickBack() {
        sendEvent(EVENT_BACK)
    }

    fun onClickVolumeUp() {
        sendEvent(EVENT_VOLUME_UP)
    }

    fun onClickVolumeDown() {
        sendEvent(EVENT_VOLUME_DOWN)
    }

    private fun <T> Flow<T>.stateIn(initialValue: T): StateFlow<T> {
        return stateIn(
            mainScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue
        )
    }
}