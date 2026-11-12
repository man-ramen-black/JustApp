package com.black.app.ui.main.calculator

import com.black.app.model.CalculatorRepository
import com.black.app.model.RatioInputs
import com.black.app.model.RatioTerm
import com.black.app.ui.main.calculator.CalculatorScreen
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** [CalculatorScreen]의 비율 탭 입력·결과 관리 ViewModel */
@HiltViewModel
class RatioViewModel @Inject constructor(
    private val repository: CalculatorRepository,
) : EventViewModel() {

    private val mutableUiState = MutableStateFlow(RatioUiState())
    val uiState: StateFlow<RatioUiState> = mutableUiState.asStateFlow()

    /**
     * 저장값을 읽는 중에 사용자가 입력을 바꿨는지 여부
     *
     * true면 읽은 값을 버림.
     */
    private var isInputChangedByUser = false

    init {
        launch {
            val savedInputs = repository.getRatioInputs()
            if (isInputChangedByUser) return@launch
            applyInputs(savedInputs)
        }
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
        isInputChangedByUser = true
        applyInputs(inputs)
        launch {
            repository.saveRatioInputs(inputs).onFailure { Log.w("saveRatioInputs failed: $it") }
        }
    }

    private fun applyInputs(inputs: RatioInputs) {
        mutableUiState.value = RatioUiState(inputs, CalculatorMath.calculateRatio(inputs))
    }
}
