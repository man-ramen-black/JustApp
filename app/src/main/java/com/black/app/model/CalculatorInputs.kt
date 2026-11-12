package com.black.app.model

/** 계산기 화면의 탭 */
enum class CalculatorTab { DILUTION, RATIO }

/**
 * 희석 계산의 입력 문자열
 *
 * 원액 농도, 목표 농도, 원액 양, 농도 0 용액 양 순으로 화면 칸 순서와 같음.
 * 두 양 칸 중 비워 둔 칸을 구함.
 */
data class DilutionInputs(
    val stockConcentration: String = "",
    val targetConcentration: String = "",
    val stockAmount: String = "",
    val diluentAmount: String = "",
)

/**
 * 비율식 `a : b = x : y`의 항
 *
 * 식의 위치 순서.
 */
enum class RatioTerm { A, B, X, Y }

/**
 * 비율 계산의 입력 문자열
 *
 * 식 `a : b = x : y`의 네 칸.
 */
data class RatioInputs(
    val a: String = "",
    val b: String = "",
    val x: String = "",
    val y: String = "",
) {

    /** [term] 칸의 입력 문자열 */
    fun valueOf(term: RatioTerm): String = when (term) {
        RatioTerm.A -> a
        RatioTerm.B -> b
        RatioTerm.X -> x
        RatioTerm.Y -> y
    }

    /** [term] 칸만 [value]로 바꾼 사본 */
    fun with(term: RatioTerm, value: String): RatioInputs = when (term) {
        RatioTerm.A -> copy(a = value)
        RatioTerm.B -> copy(b = value)
        RatioTerm.X -> copy(x = value)
        RatioTerm.Y -> copy(y = value)
    }
}
