package com.black.app.ui.main.calculator

import com.black.app.model.DilutionInputs
import com.black.app.model.RatioInputs
import com.black.app.model.RatioTerm
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.math.BigDecimal

/** [CalculatorMath]의 입력 검증, 희석 계산, 비율 계산, 결과 표기 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CalculatorMathTest : BaseTest() {

    /** 희석 결과의 구한 칸과 구한 양·총량을 [CalculatorMath.formatNumber]로 바꾼 값 */
    private fun dilutionOf(
        stockConcentration: String,
        targetConcentration: String,
        stockAmount: String,
        diluentAmount: String,
    ): Triple<DilutionAmount, String, String> {
        val result = CalculatorMath.calculateDilution(
            DilutionInputs(stockConcentration, targetConcentration, stockAmount, diluentAmount),
        )
        assertTrue(result is DilutionResult.Success)
        result as DilutionResult.Success
        return Triple(result.solvedAmount, CalculatorMath.formatNumber(result.amount), CalculatorMath.formatNumber(result.totalAmount))
    }

    /** 네 칸 입력으로 만든 희석 입력의 계산 결과 */
    private fun dilutionResultOf(
        stockConcentration: String,
        targetConcentration: String,
        stockAmount: String,
        diluentAmount: String,
    ): DilutionResult = CalculatorMath.calculateDilution(
        DilutionInputs(stockConcentration, targetConcentration, stockAmount, diluentAmount),
    )

    /** 비율 결과의 빈 칸 위치와 값을 [CalculatorMath.formatNumber]로 바꾼 쌍 */
    private fun ratioOf(a: String, b: String, x: String, y: String): Pair<RatioTerm, String> {
        val result = CalculatorMath.calculateRatio(RatioInputs(a, b, x, y))
        assertTrue(result is RatioResult.Success)
        result as RatioResult.Success
        return result.term to CalculatorMath.formatNumber(result.value)
    }

    /**
     * 숫자와 소수점 하나, 최대 길이 이하만 허용하는지 검증
     *
     * Given: 허용·불허 입력 문자열
     * When: isAllowedInput 호출
     * Then: 허용 입력은 true, 불허 입력은 false
     */
    @Test
    fun test_01_isAllowedInputAcceptsOnlyDigitsAndOneDot() = runTest {
        /** Given **/
        val allowed = listOf("", "12", "9.8", ".5", "5.", "1234567890")
        val rejected = listOf("1,5", "1.2.3", "-1", "1a", " 30", "12345678901")

        /** When / Then **/
        allowed.forEach { assertTrue(it, CalculatorMath.isAllowedInput(it)) }
        rejected.forEach { assertFalse(it, CalculatorMath.isAllowedInput(it)) }
    }

    /**
     * 소수점이 앞뒤에 붙은 입력은 읽고 소수점만 있는 입력은 null인지 검증
     *
     * Given: ".5", "5.", ".", ""
     * When: parseNumber 호출
     * Then: 0.5, 5, null, null
     */
    @Test
    fun test_02_parseNumberReadsEdgeDots() = runTest {
        /** When / Then **/
        assertEquals(0, BigDecimal("0.5").compareTo(CalculatorMath.parseNumber(".5")))
        assertEquals(0, BigDecimal("5").compareTo(CalculatorMath.parseNumber("5.")))
        assertNull(CalculatorMath.parseNumber("."))
        assertNull(CalculatorMath.parseNumber(""))
    }

    /**
     * 원액 양을 알 때 섞을 농도 0 용액 양과 총량이 계산되는지 검증
     *
     * Given: 원액 농도 9.8, 목표 농도 3, 원액 양 30
     * When: calculateDilution 호출
     * Then: DILUENT, 구한 양 68, 총량 98
     */
    @Test
    fun test_03_dilutionSolvesDiluentAmount() = runTest {
        /** When / Then **/
        assertEquals(Triple(DilutionAmount.DILUENT, "68", "98"), dilutionOf("9.8", "3", "30", ""))
    }

    /**
     * 농도 0 용액 양을 알 때 넣을 원액 양과 총량이 계산되는지 검증
     *
     * Given: 원액 농도 9.8, 목표 농도 3, 농도 0 용액 양 68
     * When: calculateDilution 호출
     * Then: STOCK, 구한 양 30, 총량 98
     */
    @Test
    fun test_04_dilutionSolvesStockAmount() = runTest {
        /** When / Then **/
        assertEquals(Triple(DilutionAmount.STOCK, "30", "98"), dilutionOf("9.8", "3", "", "68"))
    }

    /**
     * 칸이 비었거나 숫자가 아니면 Incomplete인지 검증
     *
     * Given: 농도 칸이 빈 입력, 양 칸이 둘 다 빈 입력, 둘 다 채운 입력, 채운 양이 "."인 입력, 농도가 "."인 입력
     * When: calculateDilution 호출
     * Then: 모두 Incomplete
     */
    @Test
    fun test_05_dilutionIncompleteWhenBlankOrInvalid() = runTest {
        /** When / Then **/
        assertEquals(DilutionResult.Incomplete, dilutionResultOf("", "3", "30", ""))
        assertEquals(DilutionResult.Incomplete, dilutionResultOf("9.8", "", "", "68"))
        assertEquals(DilutionResult.Incomplete, dilutionResultOf("9.8", "3", "", ""))
        assertEquals(DilutionResult.Incomplete, dilutionResultOf("9.8", "3", "30", "68"))
        assertEquals(DilutionResult.Incomplete, dilutionResultOf("9.8", "3", "", "."))
        assertEquals(DilutionResult.Incomplete, dilutionResultOf("9.8", "3", ".", ""))
        assertEquals(DilutionResult.Incomplete, dilutionResultOf(".", "3", "30", ""))
    }

    /**
     * 목표 농도가 0이면 TargetZero이고 위쪽 행이 먼저 적용되는지 검증
     *
     * Given: 목표 농도 "0", "0.0", 목표 농도만 "0"이고 원액 농도가 빈 입력, 양 칸이 둘 다 빈 입력
     * When: calculateDilution 호출
     * Then: TargetZero, TargetZero, Incomplete, Incomplete
     */
    @Test
    fun test_06_dilutionTargetZero() = runTest {
        /** When / Then **/
        assertEquals(DilutionResult.TargetZero, dilutionResultOf("9.8", "0", "", "10"))
        assertEquals(DilutionResult.TargetZero, dilutionResultOf("9.8", "0.0", "30", ""))
        assertEquals(DilutionResult.Incomplete, dilutionResultOf("", "0", "30", ""))
        assertEquals(DilutionResult.Incomplete, dilutionResultOf("9.8", "0", "", ""))
    }

    /**
     * 목표 농도가 원액 농도보다 높으면 채운 양이 0이어도 TargetAboveStock인지 검증
     *
     * Given: 원액 농도 3, 목표 농도 6, 원액 양 30·0, 농도 0 용액 양 10
     * When: calculateDilution 호출
     * Then: 모두 TargetAboveStock
     */
    @Test
    fun test_07_dilutionTargetAboveStock() = runTest {
        /** When / Then **/
        assertEquals(DilutionResult.TargetAboveStock, dilutionResultOf("3", "6", "30", ""))
        assertEquals(DilutionResult.TargetAboveStock, dilutionResultOf("3", "6", "0", ""))
        assertEquals(DilutionResult.TargetAboveStock, dilutionResultOf("3", "6", "", "10"))
    }

    /**
     * 농도가 같으면 원액 양은 SameConcentration이고 농도 0 용액 양은 0인지 검증
     *
     * Given: 원액 농도 3, 목표 농도 3·3.0
     * When: calculateDilution 호출
     * Then: 농도 0 용액 양 10·0을 채우면 SameConcentration, 원액 양 30을 채우면 DILUENT 구한 양 0·총량 30
     */
    @Test
    fun test_08_dilutionSameConcentration() = runTest {
        /** When / Then **/
        assertEquals(DilutionResult.SameConcentration, dilutionResultOf("3", "3", "", "10"))
        assertEquals(DilutionResult.SameConcentration, dilutionResultOf("3", "3", "", "0"))
        assertEquals(Triple(DilutionAmount.DILUENT, "0", "30"), dilutionOf("3", "3.0", "30", ""))
    }

    /**
     * 채운 양이 0이면 구한 양과 총량이 모두 0인지 검증
     *
     * Given: 원액 농도 9.8, 목표 농도 3, 원액 양 0 또는 농도 0 용액 양 0
     * When: calculateDilution 호출
     * Then: 각각 DILUENT·STOCK, 구한 양 0, 총량 0
     */
    @Test
    fun test_09_dilutionZeroAmount() = runTest {
        /** When / Then **/
        assertEquals(Triple(DilutionAmount.DILUENT, "0", "0"), dilutionOf("9.8", "3", "0", ""))
        assertEquals(Triple(DilutionAmount.STOCK, "0", "0"), dilutionOf("9.8", "3", "", "0"))
    }

    /**
     * 나누어떨어지지 않는 값이 둘째 자리까지 표기되는지 검증
     *
     * Given: 원액 농도 12, 목표 농도 9, 원액 양 10 또는 농도 0 용액 양 10
     * When: calculateDilution 호출
     * Then: 섞을 양 3.33·총량 13.33, 넣을 원액 30·총량 40
     */
    @Test
    fun test_10_dilutionRepeatingDecimal() = runTest {
        /** When / Then **/
        assertEquals(Triple(DilutionAmount.DILUENT, "3.33", "13.33"), dilutionOf("12", "9", "10", ""))
        assertEquals(Triple(DilutionAmount.STOCK, "30", "40"), dilutionOf("12", "9", "", "10"))
    }

    /**
     * 구한 양이 0.005 미만이면 유효 숫자 두 자리로 표기되고 총량은 따로 계산되는지 검증
     *
     * Given: 원액 농도 3.01, 목표 농도 3, 원액 양 1
     * When: calculateDilution 호출
     * Then: 섞을 양 0.0033, 총량 1
     */
    @Test
    fun test_17_dilutionTinyAmountUsesSignificantDigits() = runTest {
        /** When / Then **/
        assertEquals(Triple(DilutionAmount.DILUENT, "0.0033", "1"), dilutionOf("3.01", "3", "1", ""))
    }

    /**
     * 비운 칸마다 그 칸의 값이 계산되고 Success.term이 비운 칸인지 검증
     *
     * Given: 1 : 2 = 3 : 6에서 한 칸씩 비운 입력
     * When: calculateRatio 호출
     * Then: A 1, B 2, X 3, Y 6
     */
    @Test
    fun test_11_ratioSolvesEachBlankTerm() = runTest {
        /** When / Then **/
        assertEquals(RatioTerm.A to "1", ratioOf("", "2", "3", "6"))
        assertEquals(RatioTerm.B to "2", ratioOf("1", "", "3", "6"))
        assertEquals(RatioTerm.X to "3", ratioOf("1", "2", "", "6"))
        assertEquals(RatioTerm.Y to "6", ratioOf("1", "2", "3", ""))
    }

    /**
     * 나눌 값이 0이면 DivideByZero인지 검증
     *
     * Given: y를 구하는데 a가 0, a를 구하는데 y가 0인 입력
     * When: calculateRatio 호출
     * Then: 모두 DivideByZero
     */
    @Test
    fun test_12_ratioDivideByZero() = runTest {
        /** When / Then **/
        assertEquals(RatioResult.DivideByZero, CalculatorMath.calculateRatio(RatioInputs("0", "2", "3", "")))
        assertEquals(RatioResult.DivideByZero, CalculatorMath.calculateRatio(RatioInputs("", "2", "3", "0")))
    }

    /**
     * 빈 칸이 정확히 하나가 아니거나 숫자가 아닌 칸이 있으면 Incomplete인지 검증
     *
     * Given: 빈 칸 둘, 모두 채움, 숫자 아님 입력
     * When: calculateRatio 호출
     * Then: 모두 Incomplete
     */
    @Test
    fun test_13_ratioIncomplete() = runTest {
        /** When / Then **/
        assertEquals(RatioResult.Incomplete, CalculatorMath.calculateRatio(RatioInputs("1", "", "3", "")))
        assertEquals(RatioResult.Incomplete, CalculatorMath.calculateRatio(RatioInputs("1", "2", "3", "6")))
        assertEquals(RatioResult.Incomplete, CalculatorMath.calculateRatio(RatioInputs("1", "2", ".", "")))
    }

    /**
     * 비율 결과가 0.005 미만이면 유효 숫자 두 자리로 표기되는지 검증
     *
     * Given: 1 : 400 = x : 1
     * When: calculateRatio 호출
     * Then: X 0.0025
     */
    @Test
    fun test_14_ratioTinyValueUsesSignificantDigits() = runTest {
        /** When / Then **/
        assertEquals(RatioTerm.X to "0.0025", ratioOf("1", "400", "", "1"))
    }

    /**
     * 둘째 자리 HALF_UP 반올림과 끝자리 0 제거, 작은 값의 유효 숫자 표기 검증
     *
     * Given: 68.00, 12.50, 3.333, 1.005, 0.005, 0.0049, 0
     * When: formatNumber 호출
     * Then: 68, 12.5, 3.33, 1.01, 0.01, 0.0049, 0
     */
    @Test
    fun test_15_formatNumberRoundsHalfUpAndStripsZeros() = runTest {
        /** When / Then **/
        assertEquals("68", CalculatorMath.formatNumber(BigDecimal("68.00")))
        assertEquals("12.5", CalculatorMath.formatNumber(BigDecimal("12.50")))
        assertEquals("3.33", CalculatorMath.formatNumber(BigDecimal("3.333")))
        assertEquals("1.01", CalculatorMath.formatNumber(BigDecimal("1.005")))
        assertEquals("0.01", CalculatorMath.formatNumber(BigDecimal("0.005")))
        assertEquals("0.0049", CalculatorMath.formatNumber(BigDecimal("0.0049")))
        assertEquals("0", CalculatorMath.formatNumber(BigDecimal("0")))
    }

    /**
     * 큰 값의 나눗셈이 정수 자리까지 정확한지 검증
     *
     * Given: 원액 농도 9999999999, 목표 농도 1, 원액 양 9999999999
     * When: calculateDilution 호출
     * Then: 섞을 양 99999999970000000002, 총량 99999999980000000001
     */
    @Test
    fun test_16_dilutionLargeValuesKeepIntegerDigits() = runTest {
        /** When / Then **/
        assertEquals(Triple(DilutionAmount.DILUENT, "99999999970000000002", "99999999980000000001"), dilutionOf("9999999999", "1", "9999999999", ""))
    }
}
