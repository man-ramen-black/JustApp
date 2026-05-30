package com.black.app.ui.maintab.main.usagetimer

import com.black.app.model.UsageTimerModel
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [UsageTimerFragmentViewModel]의 선택 앱 로드/저장 동작 검증
 */
class UsageTimerFragmentViewModelTest : BaseTest() {

    @MockK
    lateinit var model: UsageTimerModel

    private lateinit var viewModel: UsageTimerFragmentViewModel

    @Before
    fun setupViewModel() {
        every { model.getPauseDuration() } returns 3
        every { model.getPauseEndTime() } returns 0L
        every { model.getSelectedApps() } returns listOf("com.a", "com.b")
        viewModel = UsageTimerFragmentViewModel(model)
    }

    /**
     * init 시 저장된 선택 앱을 LiveData로 로드하는지 검증
     *
     * Given: model이 선택 앱 [com.a, com.b] 반환
     * When: init 호출
     * Then: selectedApps LiveData가 [com.a, com.b]
     */
    @Test
    fun test_01_initLoadsSelectedApps() {
        /** When **/
        viewModel.init()

        /** Then **/
        assertEquals(listOf("com.a", "com.b"), viewModel.selectedApps.value)
    }

    /**
     * 앱 선택 결과를 저장하고 LiveData를 갱신하는지 검증
     *
     * Given: model.saveSelectedApps 모킹
     * When: onAppsSelected([com.x]) 호출
     * Then: model.saveSelectedApps 호출 + selectedApps가 [com.x]로 갱신
     */
    @Test
    fun test_02_onAppsSelectedSavesAndUpdates() {
        /** Given **/
        every { model.saveSelectedApps(any()) } just Runs

        /** When **/
        viewModel.onAppsSelected(listOf("com.x"))

        /** Then **/
        verify { model.saveSelectedApps(listOf("com.x")) }
        assertEquals(listOf("com.x"), viewModel.selectedApps.value)
    }

    /**
     * 선택 앱 유무에 따라 selectedAppsEmpty가 갱신되는지 검증
     *
     * Given: model.saveSelectedApps 모킹
     * When: 빈 목록 → 비어있지 않은 목록으로 onAppsSelected 호출
     * Then: selectedAppsEmpty가 true → false로 전환
     */
    @Test
    fun test_03_selectedAppsEmptyReflectsList() {
        /** Given **/
        every { model.saveSelectedApps(any()) } just Runs

        /** When / Then **/
        viewModel.onAppsSelected(emptyList())
        assertEquals(true, viewModel.selectedAppsEmpty.value)

        viewModel.onAppsSelected(listOf("com.x"))
        assertEquals(false, viewModel.selectedAppsEmpty.value)
    }
}
