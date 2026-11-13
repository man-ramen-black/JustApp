package com.black.app.ui.main.calculator

import com.black.app.model.CalculatorRepository
import com.black.app.model.CalculatorTab
import com.black.app.ui.main.calculator.CalculatorScreen
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
    repository: CalculatorRepository,
) : EventViewModel() {

    private val mutableUiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = mutableUiState.asStateFlow()

    /** 저장된 탭 복원과 사용자가 고른 탭 저장(복원 전에 고르면 고른 탭 우선) */
    private val selectedTab = PersistedUserInput(
        launcher = { block -> launch { block() } },
        load = repository::getSelectedTab,
        save = repository::saveSelectedTab,
        saveLogName = "saveSelectedTab",
        updateState = { tab -> mutableUiState.update { it.copy(selectedTab = tab) } },
    )

    init {
        selectedTab.restore()
    }

    fun selectTab(tab: CalculatorTab) {
        selectedTab.change(tab)
    }
}
