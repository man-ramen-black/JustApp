package com.black.app.ui.maintab.main.usagetimer

import android.graphics.drawable.Drawable
import com.black.app.R
import com.black.app.model.UsageTimerRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.ui.common.selectapp.SelectedAppUiItem
import com.black.core.viewmodel.ViewModelEvent
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import java.io.IOException
import org.junit.runners.MethodSorters

/** [UsageTimerViewModel]의 상태 로드·저장·이벤트 발송 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: UsageTimerRepository

    @MockK
    lateinit var installedAppResolver: InstalledAppResolver

    private fun uiItem(packageName: String) =
        SelectedAppUiItem(packageName, packageName, mockk<Drawable>())

    @Before
    fun setupViewModel() {
        coEvery { repository.getPauseDuration() } returns 3
        coEvery { repository.getPauseEndTime() } returns 0L
        coEvery { repository.getSelectedApps() } returns listOf("com.a", "com.b")
        every { installedAppResolver.resolve(any()) } answers { uiItem(firstArg()) }
    }

    /** ViewModel 생성 후 init의 비동기 초기 로드 완료까지 대기 */
    private fun createViewModel() = UsageTimerViewModel(repository, installedAppResolver)
        .also { waitCoroutine() }

    /**
     * 생성 시 저장된 일시정지 시간·선택 앱이 UiState로 로드되는지 검증
     *
     * Given: repository가 pauseDuration 3, 선택 앱 [com.a, com.b] 반환
     * When: ViewModel 생성(초기 로드 완료 대기)
     * Then: uiState.pauseDurationInput "3", selectedApps 패키지명 [com.a, com.b]
     */
    @Test
    fun test_01_initLoadsSavedState() = runTest {
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
     * Given: repository.saveSelectedApps 모킹
     * When: onAppsSelected([com.x]) 호출 후 코루틴 완료 대기
     * Then: repository.saveSelectedApps 호출 + selectedApps가 [com.x]로 갱신
     */
    @Test
    fun test_02_onAppsSelectedSavesAndUpdates() = runTest {
        /** Given **/
        coEvery { repository.saveSelectedApps(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()

        /** When **/
        viewModel.onAppsSelected(listOf("com.x"))
        waitCoroutine()

        /** Then **/
        coVerify { repository.saveSelectedApps(listOf("com.x")) }
        assertEquals(
            listOf("com.x"),
            viewModel.uiState.value.selectedApps.map { it.packageName },
        )
    }

    /**
     * Pause 클릭 시 저장·일시정지 후 DetachTimerView·ShowToast 이벤트가 발송되는지 검증
     *
     * Given: pauseDurationInput "5" 입력, repository 저장·일시정지 모킹
     * When: onClickPause 호출
     * Then: repository.savePauseDuration(5)·pause(5) 호출 + DetachTimerView, 5분 일시정지 문구 ShowToast 이벤트 수신
     */
    @Test
    fun test_03_onClickPauseSavesAndSendsEvents() = runTest {
        /** Given **/
        coEvery { repository.savePauseDuration(any()) } returns Result.success(Unit)
        coEvery { repository.pause(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        viewModel.onPauseDurationInputChanged("5")
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onClickPause()
        viewModel.events.collectEvents(receivedEvents, count = 2)

        /** Then **/
        coVerify { repository.savePauseDuration(5) }
        coVerify { repository.pause(5) }
        assertEquals(UsageTimerViewModel.EventDetachTimerView, receivedEvents[0])
        assertEquals(UsageTimerViewModel.EventShowToast(R.string.usage_timer_paused, listOf(5)), receivedEvents[1])
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
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onClickSelectApp()
        viewModel.events.collectEvents(receivedEvents, count = 1)

        /** Then **/
        assertEquals(
            UsageTimerViewModel.EventOpenSelectApp(listOf("com.a", "com.b")),
            receivedEvents[0],
        )
    }

    /**
     * 숫자가 아니거나 1분 미만인 입력은 저장·일시정지하지 않고 안내 토스트를 보내는지 검증
     *
     * Given: pauseDurationInput "abc"로 저장, "0"으로 일시정지
     * When: onClickSave, onClickPause 호출 후 코루틴 완료 대기
     * Then: savePauseDuration·pause 미호출, 안내 토스트 2개 수신
     */
    @Test
    fun test_05_invalidDurationInputIsRejected() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onPauseDurationInputChanged("abc")
        viewModel.onClickSave()
        viewModel.onPauseDurationInputChanged("0")
        viewModel.onClickPause()
        waitCoroutine()
        viewModel.events.collectEvents(receivedEvents, count = 2)

        /** Then **/
        coVerify(exactly = 0) {
            repository.savePauseDuration(any())
            repository.pause(any())
        }
        val invalidToast = UsageTimerViewModel.EventShowToast(R.string.usage_timer_pause_duration_invalid)
        assertEquals(listOf(invalidToast, invalidToast), receivedEvents)
    }

    /**
     * Pause 클릭 후 잔여 시간이 양수로 갱신되는지 검증
     *
     * Given: pause 후 getPauseEndTime이 현재 시각+5분 반환
     * When: onClickPause 호출 후 코루틴 완료 대기
     * Then: uiState.pauseRemainTimeMillis가 0 초과
     */
    @Test
    fun test_06_onClickPauseUpdatesRemainTime() = runTest {
        /** Given **/
        coEvery { repository.savePauseDuration(any()) } returns Result.success(Unit)
        coEvery { repository.pause(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        coEvery { repository.getPauseEndTime() } returns System.currentTimeMillis() + 5 * 60 * 1000L

        /** When **/
        viewModel.onClickPause()
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.pauseRemainTimeMillis > 0L)
    }

    /**
     * 일시정지 시각을 저장하지 못하면 타이머를 떼지 않고 실패 토스트만 보내는지 검증
     *
     * Given: pause가 실패 결과를 돌려주는 저장소, 입력 "5"
     * When: onClickPause 호출 뒤 구분용으로 onClickAccessibility 호출
     * Then: 저장 실패 토스트 다음에 바로 접근성 설정 이벤트 수신(EventDetachTimerView 없음)
     */
    @Test
    fun test_07_pauseSaveFailureShowsToastOnly() = runTest {
        /** Given **/
        coEvery { repository.savePauseDuration(any()) } returns Result.success(Unit)
        coEvery { repository.pause(any()) } returns Result.failure(IOException("disk full"))
        val viewModel = createViewModel()
        viewModel.onPauseDurationInputChanged("5")
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onClickPause()
        waitCoroutine()
        viewModel.onClickAccessibility()
        viewModel.events.collectEvents(receivedEvents, count = 2)

        /** Then **/
        assertEquals(
            listOf(
                UsageTimerViewModel.EventShowToast(R.string.usage_timer_save_failed),
                UsageTimerViewModel.EventOpenAccessibilitySettings,
            ),
            receivedEvents,
        )
    }
}

/** Flow에서 [count]개 이벤트를 수집하는 테스트 헬퍼 */
private suspend fun Flow<ViewModelEvent>.collectEvents(
    into: MutableList<ViewModelEvent>,
    count: Int,
) {
    take(count).toList(into)

}
