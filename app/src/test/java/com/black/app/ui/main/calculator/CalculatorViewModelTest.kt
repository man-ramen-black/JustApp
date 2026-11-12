package com.black.app.ui.main.calculator

import com.black.app.model.CalculatorRepository
import com.black.app.model.CalculatorTab
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [CalculatorViewModel]의 탭 복원·선택·저장 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CalculatorViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: CalculatorRepository

    @Before
    fun setupViewModel() {
        coEvery { repository.getSelectedTab() } returns CalculatorTab.RATIO
        coEvery { repository.saveSelectedTab(any()) } returns Result.success(Unit)
    }

    /**
     * 저장된 탭이 복원되는지 검증
     *
     * Given: repository가 RATIO 반환
     * When: ViewModel 생성
     * Then: 대기 전에는 selectedTab null, 대기 후 RATIO
     */
    @Test
    fun test_01_initRestoresSelectedTab() = runTest {
        /** When **/
        val viewModel = CalculatorViewModel(repository)

        /** Then **/
        assertNull(viewModel.uiState.value.selectedTab)
        waitCoroutine()
        assertEquals(CalculatorTab.RATIO, viewModel.uiState.value.selectedTab)
    }

    /**
     * 탭 선택이 상태에 반영되고 저장되는지 검증
     *
     * Given: 저장된 탭 DILUTION
     * When: selectTab(RATIO) 호출
     * Then: selectedTab RATIO, saveSelectedTab(RATIO) 호출
     */
    @Test
    fun test_02_selectTabUpdatesAndSaves() = runTest {
        /** Given **/
        coEvery { repository.getSelectedTab() } returns CalculatorTab.DILUTION
        val viewModel = CalculatorViewModel(repository)
        waitCoroutine()

        /** When **/
        viewModel.selectTab(CalculatorTab.RATIO)
        waitCoroutine()

        /** Then **/
        assertEquals(CalculatorTab.RATIO, viewModel.uiState.value.selectedTab)
        coVerify { repository.saveSelectedTab(CalculatorTab.RATIO) }
    }

    /**
     * 복원 전에 탭을 고르면 복원값을 버리는지 검증
     *
     * Given: 저장된 탭 RATIO
     * When: 생성 직후 대기 없이 selectTab(DILUTION) 호출
     * Then: 대기 후에도 selectedTab DILUTION
     */
    @Test
    fun test_03_selectTabBeforeRestoreWins() = runTest {
        /** Given **/
        val viewModel = CalculatorViewModel(repository)

        /** When **/
        viewModel.selectTab(CalculatorTab.DILUTION)
        waitCoroutine()

        /** Then **/
        assertEquals(CalculatorTab.DILUTION, viewModel.uiState.value.selectedTab)
    }
}
