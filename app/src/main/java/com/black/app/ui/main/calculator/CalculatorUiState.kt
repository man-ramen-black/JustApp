package com.black.app.ui.main.calculator

import com.black.app.model.CalculatorTab

/** 계산기 화면 상태 */
data class CalculatorUiState(
    /** 선택된 탭. null이면 저장값을 읽기 전 */
    val selectedTab: CalculatorTab? = null,
)
