package com.black.app.model.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.black.app.model.CalculatorTab
import com.black.app.model.DilutionInputs
import com.black.app.model.RatioInputs
import com.black.core.model.BaseDataStore
import com.black.core.model.blackDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 계산기 로컬 데이터 저장소
 *
 * 마지막에 본 탭과 탭별 입력 문자열을 저장.
 */
class CalculatorDataStore @Inject constructor(
    @ApplicationContext context: Context
): BaseDataStore(context) {

    companion object {
        private val Context.calculatorDataStore: DataStore<Preferences> by blackDataStore(
            name = "calculator",
        )

        private val KEY_SELECTED_TAB = stringPreferencesKey("CalculatorSelectedTab")
        private val KEY_DILUTION_STOCK_CONCENTRATION = stringPreferencesKey("CalculatorDilutionStockConcentration")
        private val KEY_DILUTION_TARGET_CONCENTRATION = stringPreferencesKey("CalculatorDilutionTargetConcentration")
        private val KEY_DILUTION_STOCK_AMOUNT = stringPreferencesKey("CalculatorDilutionStockAmount")
        private val KEY_DILUTION_DILUENT_AMOUNT = stringPreferencesKey("CalculatorDilutionDiluentAmount")
        private val KEY_RATIO_A = stringPreferencesKey("CalculatorRatioA")
        private val KEY_RATIO_B = stringPreferencesKey("CalculatorRatioB")
        private val KEY_RATIO_X = stringPreferencesKey("CalculatorRatioX")
        private val KEY_RATIO_Y = stringPreferencesKey("CalculatorRatioY")
    }

    override fun getDataStore(context: Context): DataStore<Preferences>
        = context.calculatorDataStore

    suspend fun updateSelectedTab(tab: CalculatorTab): Result<Unit> {
        return update(KEY_SELECTED_TAB, tab.name)
    }

    /**
     * 저장된 탭
     *
     * 저장된 탭 이름이 없거나 [CalculatorTab]에 없는 이름이면 [CalculatorTab.DILUTION].
     */
    suspend fun getSelectedTab(): CalculatorTab {
        val name = get(KEY_SELECTED_TAB, CalculatorTab.DILUTION.name)
        return CalculatorTab.entries.firstOrNull { it.name == name } ?: CalculatorTab.DILUTION
    }

    /**
     * 희석 입력 네 칸 저장
     *
     * 칸마다 저장하고 첫 실패를 반환하며 모두 성공이면 성공.
     */
    suspend fun updateDilutionInputs(inputs: DilutionInputs): Result<Unit> {
        // 칸 순서대로 저장
        return updateAll(
            Pair(KEY_DILUTION_STOCK_CONCENTRATION, inputs.stockConcentration),
            Pair(KEY_DILUTION_TARGET_CONCENTRATION, inputs.targetConcentration),
            Pair(KEY_DILUTION_STOCK_AMOUNT, inputs.stockAmount),
            Pair(KEY_DILUTION_DILUENT_AMOUNT, inputs.diluentAmount),
        )
    }

    suspend fun getDilutionInputs(): DilutionInputs {
        return DilutionInputs(
            stockConcentration = get(KEY_DILUTION_STOCK_CONCENTRATION, ""),
            targetConcentration = get(KEY_DILUTION_TARGET_CONCENTRATION, ""),
            stockAmount = get(KEY_DILUTION_STOCK_AMOUNT, ""),
            diluentAmount = get(KEY_DILUTION_DILUENT_AMOUNT, ""),
        )
    }

    /**
     * 비율 입력 네 칸 저장
     *
     * 칸마다 저장하고 첫 실패를 반환하며 모두 성공이면 성공.
     */
    suspend fun updateRatioInputs(inputs: RatioInputs): Result<Unit> {
        // 칸 순서대로 저장
        return updateAll(
            Pair(KEY_RATIO_A, inputs.a),
            Pair(KEY_RATIO_B, inputs.b),
            Pair(KEY_RATIO_X, inputs.x),
            Pair(KEY_RATIO_Y, inputs.y),
        )
    }

    suspend fun getRatioInputs(): RatioInputs {
        return RatioInputs(
            a = get(KEY_RATIO_A, ""),
            b = get(KEY_RATIO_B, ""),
            x = get(KEY_RATIO_X, ""),
            y = get(KEY_RATIO_Y, ""),
        )
    }

    private suspend fun updateAll(vararg entries: Pair<Preferences.Key<String>, String>): Result<Unit> {
        for ((key, value) in entries) {
            val result = update(key, value)
            // 첫 실패에서 중단
            if (result.isFailure) return result
        }
        return Result.success(Unit)
    }
}
