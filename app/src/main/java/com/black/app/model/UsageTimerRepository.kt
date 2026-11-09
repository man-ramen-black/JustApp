package com.black.app.model

import android.content.Context
import com.black.app.model.datastore.UsageTimerDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Created by jinhyuk.lee on 2022/05/09
 **/
class UsageTimerRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = UsageTimerDataStore(context)

    suspend fun savePauseDuration(pauseDurationMin: Int): Result<Unit> {
        return dataStore.updatePauseDuration(pauseDurationMin)
    }

    suspend fun pause(pauseDurationMin: Int): Result<Unit> {
        // Int 곱셈은 약 35,792분부터 넘쳐 과거 시각이 저장되므로 Long으로 계산
        return dataStore.updatePauseEndTime(System.currentTimeMillis() + pauseDurationMin * 60_000L)
    }

    suspend fun cancelPause(): Result<Unit> {
        return dataStore.updatePauseEndTime(0)
    }

    suspend fun getPauseDuration() : Int {
        return dataStore.getPauseDuration()
    }

    suspend fun getPauseEndTime() : Long {
        return dataStore.getPauseEndTime()
    }

    suspend fun saveSelectedApps(packageNames: List<String>): Result<Unit> {
        return dataStore.updateSelectedApps(packageNames)
    }

    suspend fun getSelectedApps() : List<String> {
        return dataStore.getSelectedApps()
    }
}
