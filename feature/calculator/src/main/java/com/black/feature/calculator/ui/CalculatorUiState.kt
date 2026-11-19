package com.black.feature.calculator.ui

import com.black.feature.calculator.data.usecase.DilutionResult
import com.black.feature.calculator.data.usecase.RatioResult
import com.black.feature.calculator.model.CalculatorTab
import com.black.feature.calculator.model.DilutionInputs
import com.black.feature.calculator.model.RatioInputs

/** 계산기 화면 상태 */
data class CalculatorUiState(
    /** 선택된 탭. null이면 저장값을 읽기 전 */
    val selectedTab: CalculatorTab? = null,
)

/** 희석 탭 상태 */
data class DilutionUiState(
    /** 네 칸의 입력 문자열 */
    val inputs: DilutionInputs = DilutionInputs(),
    /** [inputs]로 계산한 결과 */
    val result: DilutionResult = DilutionResult.Incomplete,
)

/** 비율 탭 상태 */
data class RatioUiState(
    /** 네 칸의 입력 문자열 */
    val inputs: RatioInputs = RatioInputs(),
    /** [inputs]로 계산한 결과 */
    val result: RatioResult = RatioResult.Incomplete,
)
