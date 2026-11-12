package com.black.app.ui.main.calculator

import com.black.app.model.CalculatorRepository
import com.black.app.model.RatioInputs
import com.black.app.model.RatioTerm
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

/** [RatioViewModel]의 입력 복원·검증·계산·저장 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RatioViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: CalculatorRepository

    @Before
    fun setupViewModel() {
        coEvery { repository.getRatioInputs() } returns RatioInputs()
        coEvery { repository.saveRatioInputs(any()) } returns Result.success(Unit)
    }

    /** ViewModel 생성 후 init의 비동기 복원 완료까지 대기 */
    private fun createViewModel() = RatioViewModel(repository).also { waitCoroutine() }

    /**
     * 저장된 입력이 복원되고 결과가 계산되는지 검증
     *
     * Given: 저장값 ("1", "2", "3", "")
     * When: ViewModel 생성
     * Then: inputs 그대로, result Success(Y, "6")
     */
    @Test
    fun test_01_initRestoresSavedInputs() = runTest {
        /** Given **/
        coEvery { repository.getRatioInputs() } returns RatioInputs("1", "2", "3", "")

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        assertEquals(RatioInputs("1", "2", "3", ""), viewModel.uiState.value.inputs)
        val result = viewModel.uiState.value.result as RatioResult.Success
        assertEquals(RatioTerm.Y, result.term)
        assertEquals("6", CalculatorMath.formatNumber(result.value))
    }

    /**
     * 허용된 입력이 상태·결과를 바꾸고 저장되는지 검증
     *
     * Given: 빈 저장값
     * When: A·B·X 입력
     * Then: result Success, saveRatioInputs(("1", "2", "3", "")) 호출
     */
    @Test
    fun test_02_allowedInputUpdatesResultAndSaves() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateTerm(RatioTerm.A, "1")
        viewModel.updateTerm(RatioTerm.B, "2")
        viewModel.updateTerm(RatioTerm.X, "3")
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.result is RatioResult.Success)
        coVerify { repository.saveRatioInputs(RatioInputs("1", "2", "3", "")) }
    }

    /**
     * 허용되지 않은 입력이 무시되는지 검증
     *
     * Given: 빈 저장값
     * When: "1,5"·"-1"·"1.2.3" 입력
     * Then: inputs 변화 없음, 저장 호출 없음
     */
    @Test
    fun test_03_disallowedInputIgnored() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateTerm(RatioTerm.A, "1,5")
        viewModel.updateTerm(RatioTerm.B, "-1")
        viewModel.updateTerm(RatioTerm.X, "1.2.3")
        waitCoroutine()

        /** Then **/
        assertEquals(RatioInputs(), viewModel.uiState.value.inputs)
        coVerify(exactly = 0) { repository.saveRatioInputs(any()) }
    }

    /**
     * 입력 지우기가 상태를 비우고 저장하는지 검증
     *
     * Given: 저장값 ("1", "2", "3", "")
     * When: clearInputs 호출
     * Then: inputs 빈 값, result Incomplete, saveRatioInputs(빈 값) 호출
     */
    @Test
    fun test_04_clearInputsEmptiesAndSaves() = runTest {
        /** Given **/
        coEvery { repository.getRatioInputs() } returns RatioInputs("1", "2", "3", "")
        val viewModel = createViewModel()

        /** When **/
        viewModel.clearInputs()
        waitCoroutine()

        /** Then **/
        assertEquals(RatioInputs(), viewModel.uiState.value.inputs)
        assertEquals(RatioResult.Incomplete, viewModel.uiState.value.result)
        coVerify { repository.saveRatioInputs(RatioInputs()) }
    }

    /**
     * 저장이 실패해도 상태가 유지되는지 검증
     *
     * Given: saveRatioInputs가 IOException 실패 반환
     * When: A·B·X 입력
     * Then: inputs·result 갱신 상태 유지
     */
    @Test
    fun test_05_saveFailureKeepsState() = runTest {
        /** Given **/
        coEvery { repository.saveRatioInputs(any()) } returns Result.failure(IOException())
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateTerm(RatioTerm.A, "1")
        viewModel.updateTerm(RatioTerm.B, "2")
        viewModel.updateTerm(RatioTerm.X, "3")
        waitCoroutine()

        /** Then **/
        assertEquals(RatioInputs("1", "2", "3", ""), viewModel.uiState.value.inputs)
        assertTrue(viewModel.uiState.value.result is RatioResult.Success)
    }

    /**
     * 복원 전에 입력하면 복원값을 버리는지 검증
     *
     * Given: 저장값 ("1", "2", "3", "")
     * When: 생성 직후 대기 없이 updateTerm(A, "5")
     * Then: 대기 후 a "5"
     */
    @Test
    fun test_06_userInputBeforeRestoreWins() = runTest {
        /** Given **/
        coEvery { repository.getRatioInputs() } returns RatioInputs("1", "2", "3", "")
        val viewModel = RatioViewModel(repository)

        /** When **/
        viewModel.updateTerm(RatioTerm.A, "5")
        waitCoroutine()

        /** Then **/
        assertEquals("5", viewModel.uiState.value.inputs.a)
    }
}
