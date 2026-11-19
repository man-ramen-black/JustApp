package com.black.feature.calculator.data.datastore

import com.black.feature.calculator.model.DilutionInputs
import com.black.feature.calculator.model.RatioInputs
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [CalculatorDataStore]의 희석·비율 입력 네 칸 함께 저장 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CalculatorDataStoreTest : BaseTest() {

    private val dataStore = CalculatorDataStore(RuntimeEnvironment.getApplication())

    /** 각 테스트 전 저장값 전체 삭제(테스트 간 격리) */
    @Before
    fun clearDataStore() {
        runTest {
            dataStore.clear()
        }
    }

    /**
     * 희석 입력 네 칸이 함께 저장되는지 검증
     *
     * Given: 저장값 없음
     * When: 네 칸을 모두 채운 희석 입력 저장
     * Then: 저장 결과 성공, 네 칸 모두 저장한 값으로 조회
     */
    @Test
    fun test_01_updateDilutionInputsSavesAllFields() = runTest {
        /** Given **/
        val inputs = DilutionInputs("9.8", "3", "30", "68")

        /** When **/
        val result = dataStore.updateDilutionInputs(inputs)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(inputs, dataStore.getDilutionInputs())
    }

    /**
     * 희석 입력을 다시 저장하면 이전 값을 빈 칸까지 덮어쓰는지 검증
     *
     * Given: 네 칸을 모두 채운 희석 입력 저장
     * When: 원액 농도만 바꾸고 나머지 세 칸을 비운 입력 저장
     * Then: 바꾼 원액 농도와 빈 세 칸으로 조회
     */
    @Test
    fun test_02_updateDilutionInputsOverwritesWithEmptyFields() = runTest {
        /** Given **/
        dataStore.updateDilutionInputs(DilutionInputs("9.8", "3", "30", "68"))
        val inputs = DilutionInputs(stockConcentration = "5")

        /** When **/
        val result = dataStore.updateDilutionInputs(inputs)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(inputs, dataStore.getDilutionInputs())
    }

    /**
     * 비율 입력 네 칸이 함께 저장되는지 검증
     *
     * Given: 저장값 없음
     * When: 네 칸을 모두 채운 비율 입력 저장
     * Then: 저장 결과 성공, 네 칸 모두 저장한 값으로 조회
     */
    @Test
    fun test_03_updateRatioInputsSavesAllFields() = runTest {
        /** Given **/
        val inputs = RatioInputs("1", "2", "3", "6")

        /** When **/
        val result = dataStore.updateRatioInputs(inputs)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(inputs, dataStore.getRatioInputs())
    }

    /**
     * 비율 입력을 다시 저장하면 이전 값을 빈 칸까지 덮어쓰는지 검증
     *
     * Given: 네 칸을 모두 채운 비율 입력 저장
     * When: 넷째 칸만 비운 비율 입력 저장
     * Then: 첫째부터 셋째 칸은 새 값, 넷째 칸은 빈 값으로 조회
     */
    @Test
    fun test_04_updateRatioInputsOverwritesWithEmptyFields() = runTest {
        /** Given **/
        dataStore.updateRatioInputs(RatioInputs("1", "2", "3", "6"))
        val inputs = RatioInputs("4", "5", "8", "")

        /** When **/
        val result = dataStore.updateRatioInputs(inputs)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(inputs, dataStore.getRatioInputs())
    }
}
