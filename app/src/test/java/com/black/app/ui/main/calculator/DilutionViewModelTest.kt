package com.black.app.ui.main.calculator

import com.black.app.model.CalculatorRepository
import com.black.app.model.DilutionInputs
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [DilutionViewModel]의 입력 복원·검증·계산·저장 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DilutionViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: CalculatorRepository

    @Before
    fun setupViewModel() {
        coEvery { repository.getDilutionInputs() } returns DilutionInputs()
        coEvery { repository.saveDilutionInputs(any()) } returns Result.success(Unit)
    }

    /** ViewModel 생성 후 init의 비동기 복원 완료까지 대기 */
    private fun createViewModel() = DilutionViewModel(repository).also { waitCoroutine() }

    /**
     * 저장된 입력이 복원되고 결과가 계산되는지 검증
     *
     * Given: 저장값 ("9.8", "3", "30", "")
     * When: ViewModel 생성
     * Then: inputs 그대로, result Success(DILUENT, "68")
     */
    @Test
    fun test_01_initRestoresSavedInputs() = runTest {
        /** Given **/
        coEvery { repository.getDilutionInputs() } returns DilutionInputs("9.8", "3", "30", "")

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        assertEquals(DilutionInputs("9.8", "3", "30", ""), viewModel.uiState.value.inputs)
        val result = viewModel.uiState.value.result as DilutionResult.Success
        assertEquals(DilutionAmount.DILUENT, result.solvedAmount)
        assertEquals("68", CalculatorMath.formatNumber(result.amount))
    }

    /**
     * 허용된 입력이 상태·결과를 바꾸고 저장되는지 검증
     *
     * Given: 빈 저장값
     * When: 원액 농도·목표 농도·원액 양 입력
     * Then: result Success, saveDilutionInputs(("9.8", "3", "30", "")) 호출
     */
    @Test
    fun test_02_allowedInputUpdatesResultAndSaves() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateStockConcentration("9.8")
        viewModel.updateTargetConcentration("3")
        viewModel.updateStockAmount("30")
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.result is DilutionResult.Success)
        coVerify { repository.saveDilutionInputs(DilutionInputs("9.8", "3", "30", "")) }
    }

    /**
     * 허용되지 않은 입력이 무시되는지 검증
     *
     * Given: 빈 저장값
     * When: "1,5"·"-1"·"1.2.3"·"a" 입력
     * Then: inputs 변화 없음, 저장 호출 없음
     */
    @Test
    fun test_03_disallowedInputIgnored() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateStockConcentration("1,5")
        viewModel.updateTargetConcentration("-1")
        viewModel.updateStockAmount("1.2.3")
        viewModel.updateDiluentAmount("a")
        waitCoroutine()

        /** Then **/
        assertEquals(DilutionInputs(), viewModel.uiState.value.inputs)
        coVerify(exactly = 0) { repository.saveDilutionInputs(any()) }
    }

    /**
     * 입력 지우기가 상태를 비우고 저장하는지 검증
     *
     * Given: 저장값 ("9.8", "3", "30", "")
     * When: clearInputs 호출
     * Then: inputs 빈 값, result Incomplete, saveDilutionInputs(빈 값) 호출
     */
    @Test
    fun test_04_clearInputsEmptiesAndSaves() = runTest {
        /** Given **/
        coEvery { repository.getDilutionInputs() } returns DilutionInputs("9.8", "3", "30", "")
        val viewModel = createViewModel()

        /** When **/
        viewModel.clearInputs()
        waitCoroutine()

        /** Then **/
        assertEquals(DilutionInputs(), viewModel.uiState.value.inputs)
        assertEquals(DilutionResult.Incomplete, viewModel.uiState.value.result)
        coVerify { repository.saveDilutionInputs(DilutionInputs()) }
    }

    /**
     * 저장이 실패해도 상태가 유지되는지 검증
     *
     * Given: saveDilutionInputs가 IOException 실패 반환
     * When: 원액 농도·목표 농도·원액 양 입력
     * Then: inputs·result 갱신 상태 유지
     */
    @Test
    fun test_05_saveFailureKeepsState() = runTest {
        /** Given **/
        coEvery { repository.saveDilutionInputs(any()) } returns Result.failure(IOException())
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateStockConcentration("9.8")
        viewModel.updateTargetConcentration("3")
        viewModel.updateStockAmount("30")
        waitCoroutine()

        /** Then **/
        assertEquals(DilutionInputs("9.8", "3", "30", ""), viewModel.uiState.value.inputs)
        assertTrue(viewModel.uiState.value.result is DilutionResult.Success)
    }

    /**
     * 복원 전에 입력하면 복원값을 버리는지 검증
     *
     * Given: 저장값 ("9.8", "3", "30", "")
     * When: 생성 직후 대기 없이 updateStockAmount("5")
     * Then: 대기 후 stockAmount "5"
     */
    @Test
    fun test_06_userInputBeforeRestoreWins() = runTest {
        /** Given **/
        coEvery { repository.getDilutionInputs() } returns DilutionInputs("9.8", "3", "30", "")
        val viewModel = DilutionViewModel(repository)

        /** When **/
        viewModel.updateStockAmount("5")
        waitCoroutine()

        /** Then **/
        assertEquals("5", viewModel.uiState.value.inputs.stockAmount)
    }

    /**
     * 농도 0 용액 양을 입력하면 원액 양을 구하고 저장하는지 검증
     *
     * Given: 저장값 원액 농도 9.8, 목표 농도 3
     * When: updateDiluentAmount("68")
     * Then: result Success(STOCK, "30"), saveDilutionInputs(("9.8", "3", "", "68")) 호출
     */
    @Test
    fun test_07_diluentAmountSolvesStockAmount() = runTest {
        /** Given **/
        coEvery { repository.getDilutionInputs() } returns DilutionInputs("9.8", "3", "", "")
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateDiluentAmount("68")
        waitCoroutine()

        /** Then **/
        val result = viewModel.uiState.value.result as DilutionResult.Success
        assertEquals(DilutionAmount.STOCK, result.solvedAmount)
        assertEquals("30", CalculatorMath.formatNumber(result.amount))
        coVerify { repository.saveDilutionInputs(DilutionInputs("9.8", "3", "", "68")) }
    }
}
