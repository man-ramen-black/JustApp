package com.black.app.ui.main.calculator

import com.black.app.model.CalculatorRepository
import com.black.app.model.RatioInputs
import com.black.app.model.RatioTerm
import com.black.app.ui.main.calculator.CalculatorScreen
import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** [CalculatorScreen]의 비율 탭 입력·결과 관리 ViewModel */
@HiltViewModel
class RatioViewModel @Inject constructor(
    repository: CalculatorRepository,
) : EventViewModel() {

    private val mutableUiState = MutableStateFlow(RatioUiState())
    val uiState: StateFlow<RatioUiState> = mutableUiState.asStateFlow()

    /** 저장된 입력 복원과 사용자가 바꾼 입력 저장(복원 전에 바꾸면 바꾼 입력 우선) */
    private val persistedInputs = PersistedUserInput(
        launcher = { block -> launch { block() } },
        load = repository::getRatioInputs,
        save = repository::saveRatioInputs,
        saveLogName = "saveRatioInputs",
        updateState = ::applyInputs,
    )

    init {
        persistedInputs.restore()
    }

    fun updateTerm(term: RatioTerm, input: String) {
        if (!CalculatorMath.isAllowedInput(input)) return
        changeInputs(mutableUiState.value.inputs.with(term, input))
    }

    fun clearInputs() {
        changeInputs(RatioInputs())
    }

    /** 사용자가 바꾼 [inputs]를 상태에 반영하고 저장 */
    private fun changeInputs(inputs: RatioInputs) {
        persistedInputs.change(inputs)
    }

    private fun applyInputs(inputs: RatioInputs) {
        mutableUiState.value = RatioUiState(inputs, CalculatorMath.calculateRatio(inputs))
    }
}
