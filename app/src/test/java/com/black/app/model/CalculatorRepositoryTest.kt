package com.black.app.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [CalculatorRepository]의 선택 탭과 탭별 입력값 저장/조회 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CalculatorRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = CalculatorRepository(context)

    /** 각 테스트 전 선택 탭과 입력값 초기화(테스트 간 격리) */
    @Before
    fun resetCalculator() {
        runTest {
            repository.saveSelectedTab(CalculatorTab.DILUTION)
            repository.saveDilutionInputs(DilutionInputs())
            repository.saveRatioInputs(RatioInputs())
        }
    }

    /**
     * 저장된 값이 없을 때 기본값으로 조회되는지 검증
     *
     * Given: 선택 탭과 입력값을 초기 상태로 저장
     * When: 선택 탭과 두 탭의 입력값 조회
     * Then: 희석 탭과 빈 입력값 반환
     */
    @Test
    fun test_01_defaultsAreEmptyAndDilution() = runTest {
        /** Given / When **/
        val tab = repository.getSelectedTab()
        val dilution = repository.getDilutionInputs()
        val ratio = repository.getRatioInputs()

        /** Then **/
        assertEquals(CalculatorTab.DILUTION, tab)
        assertEquals(DilutionInputs(), dilution)
        assertEquals(RatioInputs(), ratio)
    }

    /**
     * 희석 입력값을 빈 칸까지 그대로 다시 읽어오는지 검증
     *
     * Given: 원액 양 칸이 빈 희석 입력값 저장
     * When: 희석 입력값 조회
     * Then: 저장한 값 그대로(빈 칸 유지) 반환, 저장 결과는 성공
     */
    @Test
    fun test_02_dilutionInputsRoundTrip() = runTest {
        /** Given **/
        val inputs = DilutionInputs("9.8", "3", "", "68")

        /** When **/
        val result = repository.saveDilutionInputs(inputs)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(inputs, repository.getDilutionInputs())
    }

    /**
     * 비율 입력값을 빈 칸까지 그대로 다시 읽어오는지 검증
     *
     * Given: 두 번째 칸이 빈 비율 입력값 저장
     * When: 비율 입력값 조회
     * Then: 저장한 값 그대로(빈 칸 유지) 반환
     */
    @Test
    fun test_03_ratioInputsRoundTrip() = runTest {
        /** Given **/
        val inputs = RatioInputs("1", "", "3", "6")

        /** When **/
        repository.saveRatioInputs(inputs)

        /** Then **/
        assertEquals(inputs, repository.getRatioInputs())
    }

    /**
     * 선택 탭을 저장하면 같은 탭으로 조회되는지 검증
     *
     * Given: 비율 탭 저장
     * When: 선택 탭 조회
     * Then: 비율 탭 반환
     */
    @Test
    fun test_04_selectedTabRoundTrip() = runTest {
        /** Given / When **/
        repository.saveSelectedTab(CalculatorTab.RATIO)

        /** Then **/
        assertEquals(CalculatorTab.RATIO, repository.getSelectedTab())
    }
}
