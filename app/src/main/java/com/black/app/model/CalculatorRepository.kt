package com.black.app.model

import android.content.Context
import com.black.app.model.datastore.CalculatorDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 계산기의 마지막에 본 탭과 탭별 입력값 저장·조회
 **/
class CalculatorRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = CalculatorDataStore(context)

    suspend fun getSelectedTab(): CalculatorTab {
        return dataStore.getSelectedTab()
    }

    suspend fun saveSelectedTab(tab: CalculatorTab): Result<Unit> {
        return dataStore.updateSelectedTab(tab)
    }

    suspend fun getDilutionInputs(): DilutionInputs {
        return dataStore.getDilutionInputs()
    }

    suspend fun saveDilutionInputs(inputs: DilutionInputs): Result<Unit> {
        return dataStore.updateDilutionInputs(inputs)
    }

    suspend fun getRatioInputs(): RatioInputs {
        return dataStore.getRatioInputs()
    }

    suspend fun saveRatioInputs(inputs: RatioInputs): Result<Unit> {
        return dataStore.updateRatioInputs(inputs)
    }
}
