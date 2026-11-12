package com.black.app.ui.main.calculator

import com.black.app.model.RatioInputs

/** 비율 탭 상태 */
data class RatioUiState(
    /** 네 칸의 입력 문자열 */
    val inputs: RatioInputs = RatioInputs(),
    /** [inputs]로 계산한 결과 */
    val result: RatioResult = RatioResult.Incomplete,
)
