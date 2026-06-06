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

    suspend fun savePauseDuration(pauseDurationMin: Int) {
        dataStore.updatePauseDuration(pauseDurationMin)
    }

    suspend fun pause(pauseDurationMin: Int) {
        dataStore.updatePauseEndTime(System.currentTimeMillis() + (pauseDurationMin * 1000 * 60))
    }

    suspend fun cancelPause() {
        dataStore.updatePauseEndTime(0)
    }

    suspend fun getPauseDuration() : Int {
        return dataStore.getPauseDuration()
    }

    suspend fun getPauseEndTime() : Long {
        return dataStore.getPauseEndTime()
    }

    suspend fun saveSelectedApps(packageNames: List<String>) {
        dataStore.updateSelectedApps(packageNames)
    }

    suspend fun getSelectedApps() : List<String> {
        return dataStore.getSelectedApps()
    }
}
