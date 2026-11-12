package com.black.app.ui.main.calculator

import com.black.app.model.DilutionInputs

/** 희석 탭 상태 */
data class DilutionUiState(
    /** 네 칸의 입력 문자열 */
    val inputs: DilutionInputs = DilutionInputs(),
    /** [inputs]로 계산한 결과 */
    val result: DilutionResult = DilutionResult.Incomplete,
)
