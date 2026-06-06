package com.black.app.ui.maintab.main.usagetimer

import android.graphics.drawable.Drawable
import com.black.app.model.UsageTimerModel
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.ui.common.selectapp.SelectedAppUiItem
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [UsageTimerViewModel]의 상태 로드·저장·이벤트 발송 동작 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsageTimerViewModelTest : BaseTest() {

    @MockK
    lateinit var model: UsageTimerModel

    @MockK
    lateinit var installedAppResolver: InstalledAppResolver

    private fun uiItem(packageName: String) =
        SelectedAppUiItem(packageName, packageName, mockk<Drawable>())

    @Before
    fun setupViewModel() {
        every { model.getPauseDuration() } returns 3
        every { model.getPauseEndTime() } returns 0L
        every { model.getSelectedApps() } returns listOf("com.a", "com.b")
        every { installedAppResolver.resolve(any()) } answers { uiItem(firstArg()) }
    }

    private fun createViewModel() = UsageTimerViewModel(model, installedAppResolver)

    /**
     * 생성 시 저장된 일시정지 시간·선택 앱이 UiState로 로드되는지 검증
     *
     * Given: model이 pauseDuration 3, 선택 앱 [com.a, com.b] 반환
     * When: ViewModel 생성
     * Then: uiState.pauseDurationInput "3", selectedApps 패키지명 [com.a, com.b]
     */
    @Test
    fun test_01_initLoadsSavedState() {
        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        assertEquals("3", viewModel.uiState.value.pauseDurationInput)
        assertEquals(
            listOf("com.a", "com.b"),
            viewModel.uiState.value.selectedApps.map { it.packageName },
        )
    }

    /**
     * 앱 선택 결과를 저장하고 UiState를 갱신하는지 검증
     *
     * Given: model.saveSelectedApps 모킹
     * When: onAppsSelected([com.x]) 호출
     * Then: model.saveSelectedApps 호출 + selectedApps가 [com.x]로 갱신
     */
    @Test
    fun test_02_onAppsSelectedSavesAndUpdates() {
        /** Given **/
        every { model.saveSelectedApps(any()) } just Runs
        val viewModel = createViewModel()

        /** When **/
        viewModel.onAppsSelected(listOf("com.x"))

        /** Then **/
        verify { model.saveSelectedApps(listOf("com.x")) }
        assertEquals(
            listOf("com.x"),
            viewModel.uiState.value.selectedApps.map { it.packageName },
        )
    }

    /**
     * Pause 클릭 시 저장·일시정지 후 DetachTimerView·ShowToast 이벤트가 발송되는지 검증
     *
     * Given: pauseDurationInput "5" 입력, model 저장·일시정지 모킹
     * When: onClickPause 호출
     * Then: model.savePauseDuration(5)·pause(5) 호출 + DetachTimerView, ShowToast 이벤트 수신
     */
    @Test
    fun test_03_onClickPauseSavesAndSendsEvents() = runTest {
        /** Given **/
        every { model.savePauseDuration(any()) } just Runs
        every { model.pause(any()) } just Runs
        val viewModel = createViewModel()
        viewModel.onPauseDurationInputChanged("5")
        val receivedEvents = mutableListOf<UsageTimerEvent>()

        /** When **/
        viewModel.onClickPause()
        viewModel.events.collectEvents(receivedEvents, count = 2)

        /** Then **/
        verify { model.savePauseDuration(5) }
        verify { model.pause(5) }
        assertEquals(UsageTimerEvent.DetachTimerView, receivedEvents[0])
        assertEquals(UsageTimerEvent.ShowToast("Pause : 5m"), receivedEvents[1])
    }

    /**
     * Select app 클릭 시 현재 선택 앱 목록을 담은 OpenSelectApp 이벤트가 발송되는지 검증
     *
     * Given: 선택 앱 [com.a, com.b] 로드 상태
     * When: onClickSelectApp 호출
     * Then: OpenSelectApp(checkedPackageNames=[com.a, com.b]) 이벤트 수신
     */
    @Test
    fun test_04_onClickSelectAppSendsCheckedPackages() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = mutableListOf<UsageTimerEvent>()

        /** When **/
        viewModel.onClickSelectApp()
        viewModel.events.collectEvents(receivedEvents, count = 1)

        /** Then **/
        assertEquals(
            UsageTimerEvent.OpenSelectApp(listOf("com.a", "com.b")),
            receivedEvents[0],
        )
    }

    /**
     * 잘못된 숫자 입력 시 0으로 저장되는지 검증
     *
     * Given: pauseDurationInput "abc" 입력
     * When: onClickSave 호출
     * Then: model.savePauseDuration(0) 호출
     */
    @Test
    fun test_05_invalidDurationInputSavesZero() {
        /** Given **/
        every { model.savePauseDuration(any()) } just Runs
        val viewModel = createViewModel()
        viewModel.onPauseDurationInputChanged("abc")

        /** When **/
        viewModel.onClickSave()

        /** Then **/
        verify { model.savePauseDuration(0) }
    }

    /**
     * Pause 클릭 후 잔여 시간이 양수로 갱신되는지 검증
     *
     * Given: pause 후 getPauseEndTime이 현재 시각+5분 반환
     * When: onClickPause 호출
     * Then: uiState.pauseRemainTimeMillis가 0 초과
     */
    @Test
    fun test_06_onClickPauseUpdatesRemainTime() {
        /** Given **/
        every { model.savePauseDuration(any()) } just Runs
        every { model.pause(any()) } just Runs
        val viewModel = createViewModel()
        every { model.getPauseEndTime() } returns System.currentTimeMillis() + 5 * 60 * 1000L

        /** When **/
        viewModel.onClickPause()

        /** Then **/
        assertTrue(viewModel.uiState.value.pauseRemainTimeMillis > 0L)
    }
}

/** Flow에서 [count]개 이벤트를 수집하는 테스트 헬퍼 */
private suspend fun Flow<UsageTimerEvent>.collectEvents(
    into: MutableList<UsageTimerEvent>,
    count: Int,
) {
    take(count).toList(into)
}
