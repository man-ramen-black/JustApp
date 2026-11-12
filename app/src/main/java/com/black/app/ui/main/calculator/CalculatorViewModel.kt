package com.black.app.ui.main.calculator

import com.black.app.model.CalculatorRepository
import com.black.app.model.CalculatorTab
import com.black.app.ui.main.calculator.CalculatorScreen
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** [CalculatorScreen]의 탭 선택 상태 관리 ViewModel */
@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val repository: CalculatorRepository,
) : EventViewModel() {

    private val mutableUiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = mutableUiState.asStateFlow()

    /**
     * 저장값을 읽는 중에 사용자가 탭을 골랐는지 여부
     *
     * true면 읽은 값을 버림.
     */
    private var isTabSelectedByUser = false

    init {
        launch {
            val savedTab = repository.getSelectedTab()
            if (isTabSelectedByUser) return@launch
            mutableUiState.update { it.copy(selectedTab = savedTab) }
        }
    }

    fun selectTab(tab: CalculatorTab) {
        isTabSelectedByUser = true
        mutableUiState.update { it.copy(selectedTab = tab) }
        launch {
            repository.saveSelectedTab(tab).onFailure { Log.w("saveSelectedTab failed: $it") }
        }
    }
}
