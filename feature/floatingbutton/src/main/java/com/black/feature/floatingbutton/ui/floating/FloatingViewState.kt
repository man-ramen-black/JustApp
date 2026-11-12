package com.black.feature.floatingbutton.ui.floating

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.black.feature.floatingbutton.data.datastore.FloatingDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * [FloatingView]가 표시할 시계와 스타일 값
 *
 * 뷰 하나에 하나씩 만들고, 모든 값은 관찰자가 있는 동안만 수집하는 LiveData라 뷰를 떼면(관찰 종료 5초 뒤) 시계 갱신도 멈춤.
 * 프로세스 수명의 범위로 공유하면 버튼을 꺼도 메인 스레드에서 100ms마다 시계를 계속 만들므로 이 방식 유지
 */
class FloatingViewState(dataStore: FloatingDataStore) {

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

    private fun <T> Flow<T>.asLiveDataWithInitial(initialValue: T): LiveData<T> {
        return onStart { emit(initialValue) }.asLiveData()
    }
}
