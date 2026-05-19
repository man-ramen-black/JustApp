package com.black.feature.floatingbutton.ui.floating

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.black.core.di.HiltModule
import com.black.core.viewmodel.EventViewModel
import com.black.feature.floatingbutton.data.datastore.FloatingDataStore
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
        const val EVENT_STATUS_BAR = "statusBar"
        const val EVENT_VOLUME_UP = "volumeUp"
        const val EVENT_VOLUME_DOWN = "volumeDown"
    }

    val clock: LiveData<String> = flow {
            while (true) {
                delay(100)
                emit(System.currentTimeMillis())
            }
        }
        .map { Calendar.getInstance().apply { timeInMillis = it } }
        .map {
            val pattern = if (it.get(Calendar.SECOND) % 2 == 0) {
                "hh:mm"
            } else {
                "hh mm"
            }
            SimpleDateFormat(pattern, Locale.getDefault(Locale.Category.FORMAT))
                .format(it.time)
        }
        .asLiveDataWithInitial("")

    val size: LiveData<Float> = dataStore.getSizeFlow()
        .filterNotNull()
        .asLiveDataWithInitial(45f)

    val padding: LiveData<Float> = dataStore.getPaddingFlow()
        .filterNotNull()
        .asLiveDataWithInitial(3f)

    val margin: LiveData<Float> = dataStore.getMarginFlow()
        .filterNotNull()
        .asLiveDataWithInitial(2f)

    val radius: LiveData<Float> = dataStore.getRadiusFlow()
        .filterNotNull()
        .asLiveDataWithInitial(3f)

    val opacity: LiveData<Float> = dataStore.getOpacityFlow()
        .filterNotNull()
        .asLiveDataWithInitial(0.3f)

    fun onClickHome() {
        sendEvent(EVENT_HOME)
    }

    fun onClickBack() {
        sendEvent(EVENT_BACK)
    }

    fun onClickStatusBar() {
        sendEvent(EVENT_STATUS_BAR)
    }

    fun onClickVolumeUp() {
        sendEvent(EVENT_VOLUME_UP)
    }

    fun onClickVolumeDown() {
        sendEvent(EVENT_VOLUME_DOWN)
    }

    private fun <T> Flow<T>.asLiveDataWithInitial(initialValue: T): LiveData<T> {
        return stateIn(
            mainScope,
            SharingStarted.Eagerly,
            initialValue
        ).asLiveData()
    }
}