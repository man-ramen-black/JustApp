package com.black.app.ui.main.calculator

import com.black.app.model.DilutionInputs
import com.black.app.model.RatioInputs
import com.black.app.model.RatioTerm
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** 희석 계산에서 구하는 양 */
enum class DilutionAmount { STOCK, DILUENT }

/** 희석 계산 결과 */
sealed interface DilutionResult {

    /** 농도 칸이 비었거나 숫자가 아님, 또는 두 양 칸이 모두 비었거나 모두 채워짐, 또는 채운 양 칸이 숫자가 아님 */
    data object Incomplete : DilutionResult

    /** 목표 농도가 0 */
    data object TargetZero : DilutionResult

    /** 목표 농도가 원액 농도보다 높음 */
    data object TargetAboveStock : DilutionResult

    /** 원액 양을 구하는데 목표 농도가 원액 농도와 같음 */
    data object SameConcentration : DilutionResult

    /** [solvedAmount] 칸의 양이 [amount]이고 총량이 [totalAmount]임 */
    data class Success(
        val solvedAmount: DilutionAmount,
        val amount: BigDecimal,
        val totalAmount: BigDecimal,
    ) : DilutionResult
}

/** 비율 계산 결과 */
sealed interface RatioResult {

    /** 빈 칸이 정확히 하나가 아니거나 숫자가 아닌 칸이 있음 */
    data object Incomplete : RatioResult

    /** 나눌 값이 0 */
    data object DivideByZero : RatioResult

    /** 빈 칸 [term] 자리에 들어갈 값 [value] */
    data class Success(val term: RatioTerm, val value: BigDecimal) : RatioResult
}

/** 희석·비율 계산과 입력 검증, 결과 표기 */
object CalculatorMath {

    const val MAX_INPUT_LENGTH = 10

    /** 나눗셈 결과의 소수 자릿수 */
    private const val DIVISION_SCALE = 20

    /**
     * 허용되는 입력 여부
     *
     * 길이 [MAX_INPUT_LENGTH] 이하이고 숫자와 소수점 하나만 있는 입력이며 빈 문자열도 허용.
     */
    fun isAllowedInput(input: String): Boolean =
        input.length <= MAX_INPUT_LENGTH && input.all { it.isAsciiDigit() || it == '.' } && input.count { it == '.' } <= 1

    /**
     * 입력 문자열의 숫자 값
     *
     * `.5`·`5.`는 읽고 `.`·빈 문자열은 null.
     */
    fun parseNumber(input: String): BigDecimal? = input.toBigDecimalOrNull()

    /**
     * 비워 둔 양 칸의 값과 총량
     *
     * 농도 0 용액 양을 구하면 `V1 × (C1 - C2) ÷ C2`, 원액 양을 구하면 `V0 × C2 ÷ (C1 - C2)`이고,
     * 총량은 각각 `V1 × C1 ÷ C2`, `V0 × C1 ÷ (C1 - C2)`.
     * 나눗셈은 소수 [DIVISION_SCALE]자리까지 HALF_UP으로 구하고, 총량은 구한 양에 더하지 않고 반올림 전 값으로 따로 계산.
     *
     * 판정 순서는 설계 문서 희석 표의 행 우선순위로, 위쪽 행이 먼저 적용됨.
     */
    fun calculateDilution(inputs: DilutionInputs): DilutionResult {
        // 1행: 농도 칸이 비었거나 숫자가 아니면 계산 불가
        val stockConcentration = parseNumber(inputs.stockConcentration) ?: return DilutionResult.Incomplete
        val targetConcentration = parseNumber(inputs.targetConcentration) ?: return DilutionResult.Incomplete

        // 2행: 양 칸이 둘 다 비었거나 둘 다 채워지면 구할 칸이 정해지지 않음
        val isStockAmountBlank = inputs.stockAmount.isEmpty()
        val isDiluentAmountBlank = inputs.diluentAmount.isEmpty()
        if (isStockAmountBlank == isDiluentAmountBlank) return DilutionResult.Incomplete
        // 채운 칸이 숫자가 아니면 계산 불가
        val solvedAmount = if (isStockAmountBlank) DilutionAmount.STOCK else DilutionAmount.DILUENT
        val givenAmount = parseNumber(if (isStockAmountBlank) inputs.diluentAmount else inputs.stockAmount)
            ?: return DilutionResult.Incomplete

        // 3행: 목표 농도 0
        if (targetConcentration.signum() == 0) return DilutionResult.TargetZero
        // 4행: 목표 농도가 원액 농도보다 높음(채운 양이 0이어도 적용)
        val concentrationGap = stockConcentration.compareTo(targetConcentration)
        if (concentrationGap < 0) return DilutionResult.TargetAboveStock
        // 5행: 농도가 같으면 원액 양은 정할 수 없고, 농도 0 용액 양은 0
        if (concentrationGap == 0) {
            if (solvedAmount == DilutionAmount.STOCK) return DilutionResult.SameConcentration
            return DilutionResult.Success(solvedAmount, BigDecimal.ZERO, givenAmount)
        }
        // 6행: 채운 양 0
        if (givenAmount.signum() == 0) return DilutionResult.Success(solvedAmount, BigDecimal.ZERO, BigDecimal.ZERO)

        // 7행: 구한 양과 총량 계산
        val concentrationDifference = stockConcentration.subtract(targetConcentration)
        // 구한 양에 곱하는 농도와 두 식이 공통으로 나누는 농도
        val (amountFactor, divisor) = when (solvedAmount) {
            DilutionAmount.DILUENT -> Pair(concentrationDifference, targetConcentration)
            DilutionAmount.STOCK -> Pair(targetConcentration, concentrationDifference)
        }
        val amount = givenAmount.multiply(amountFactor).divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP)
        val totalAmount = givenAmount.multiply(stockConcentration).divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP)
        return DilutionResult.Success(solvedAmount, amount, totalAmount)
    }

    /**
     * 빈 칸이 정확히 하나이고 나머지 셋이 숫자일 때 `a × y = b × x`로 빈 칸의 값
     *
     * 나눗셈은 소수 [DIVISION_SCALE]자리까지 HALF_UP으로 구함.
     */
    fun calculateRatio(inputs: RatioInputs): RatioResult {
        // 빈 칸이 정확히 하나일 때만 계산
        val blankTerms = RatioTerm.entries.filter { inputs.valueOf(it).isEmpty() }
        if (blankTerms.size != 1) return RatioResult.Incomplete
        val blankTerm = blankTerms.single()

        // 나머지 셋은 숫자여야 함
        val numbers = mutableMapOf<RatioTerm, BigDecimal>()
        for (term in RatioTerm.entries) {
            if (term == blankTerm) continue
            numbers[term] = parseNumber(inputs.valueOf(term)) ?: return RatioResult.Incomplete
        }
        val a = numbers[RatioTerm.A]
        val b = numbers[RatioTerm.B]
        val x = numbers[RatioTerm.X]
        val y = numbers[RatioTerm.Y]

        // 빈 칸별 (곱하는 두 값, 나눌 값)
        val (first, second, divisor) = when (blankTerm) {
            RatioTerm.A -> Triple(b, x, y)
            RatioTerm.B -> Triple(a, y, x)
            RatioTerm.X -> Triple(a, y, b)
            RatioTerm.Y -> Triple(b, x, a)
        }
        // 나눌 값 0 검사 후 계산
        if (divisor!!.signum() == 0) return RatioResult.DivideByZero
        return RatioResult.Success(blankTerm, first!!.multiply(second).divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP))
    }

    /**
     * 둘째 자리까지 HALF_UP 반올림하고 끝자리 0을 지운 문자열
     *
     * 0이 아닌데 반올림으로 0이 되는 값은 유효 숫자 두 자리로 표기.
     */
    fun formatNumber(value: BigDecimal): String {
        // 0은 그대로 표기
        if (value.signum() == 0) return "0"
        // 둘째 자리 반올림
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        // 반올림으로 0이 되면 유효 숫자 두 자리로 대체
        val shown = if (rounded.signum() == 0) value.round(MathContext(2, RoundingMode.HALF_UP)) else rounded
        // 끝자리 0 제거
        return shown.stripTrailingZeros().toPlainString()
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
