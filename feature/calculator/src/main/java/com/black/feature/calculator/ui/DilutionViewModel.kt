package com.black.feature.calculator.ui

import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.PersistedUserInput
import com.black.feature.calculator.data.CalculatorRepository
import com.black.feature.calculator.data.usecase.CalculatorMath
import com.black.feature.calculator.model.DilutionInputs
import com.black.feature.calculator.ui.CalculatorScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** [CalculatorScreen]의 희석 탭 입력·결과 관리 ViewModel */
@HiltViewModel
class DilutionViewModel @Inject constructor(
    repository: CalculatorRepository,
) : EventViewModel() {

    private val mutableUiState = MutableStateFlow(DilutionUiState())
    val uiState: StateFlow<DilutionUiState> = mutableUiState.asStateFlow()

    /** 저장된 입력 복원과 사용자가 바꾼 입력 저장(복원 전에 바꾸면 바꾼 입력 우선) */
    private val persistedInputs = PersistedUserInput(
        launcher = { block -> launch { block() } },
        load = repository::getDilutionInputs,
        save = repository::saveDilutionInputs,
        saveLogName = "saveDilutionInputs",
        updateState = ::applyInputs,
    )

    init {
        persistedInputs.restore()
    }

    fun updateStockConcentration(input: String) {
        if (!CalculatorMath.isAllowedInput(input)) {
            return
        }
        changeInputs(mutableUiState.value.inputs.copy(stockConcentration = input))
    }

    fun updateTargetConcentration(input: String) {
        if (!CalculatorMath.isAllowedInput(input)) {
            return
        }
        changeInputs(mutableUiState.value.inputs.copy(targetConcentration = input))
    }

    fun updateStockAmount(input: String) {
        if (!CalculatorMath.isAllowedInput(input)) {
            return
        }
        changeInputs(mutableUiState.value.inputs.copy(stockAmount = input))
    }

    fun updateDiluentAmount(input: String) {
        if (!CalculatorMath.isAllowedInput(input)) {
            return
        }
        changeInputs(mutableUiState.value.inputs.copy(diluentAmount = input))
    }

    fun clearInputs() {
        changeInputs(DilutionInputs())
    }

    /** 사용자가 바꾼 [inputs]를 상태에 반영하고 저장 */
    private fun changeInputs(inputs: DilutionInputs) {
        persistedInputs.change(inputs)
    }

    private fun applyInputs(inputs: DilutionInputs) {
        mutableUiState.value = DilutionUiState(inputs, CalculatorMath.calculateDilution(inputs))
    }
}
