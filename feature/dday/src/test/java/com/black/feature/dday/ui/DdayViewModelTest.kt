package com.black.feature.dday.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.black.core.system.CalendarSource
import com.black.core.system.DeviceCalendar
import com.black.feature.dday.R
import com.black.feature.dday.data.DdayRepository
import com.black.feature.dday.data.usecase.DdayLoadResult
import com.black.feature.dday.data.usecase.LoadDdayItemsUseCase
import com.black.feature.dday.model.DdayStyle
import com.black.feature.dday.model.DdayStyleField
import com.black.feature.dday.widget.DdayWidgetRefresher
import com.black.test.BaseTest
import com.black.test.collectDuring
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [DdayViewModel]의 불러오기, 캘린더 선택 저장, 스타일 조절 저장과 위젯 갱신 디바운스 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DdayViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: DdayRepository

    @MockK
    lateinit var loadItems: LoadDdayItemsUseCase

    @MockK
    lateinit var calendarSource: CalendarSource

    @MockK
    lateinit var refresher: DdayWidgetRefresher

    private val calendars = listOf(
        DeviceCalendar(id = 3, displayName = "개인", accountName = "me@example.com", color = 0xFF112233.toInt()),
        DeviceCalendar(id = 5, displayName = "회사", accountName = "work@example.com", color = 0xFF445566.toInt()),
    )
    private val loadedPreview = DdayLoadResult.Loaded(emptyList())

    @Before
    fun setupDefaults() {
        every { calendarSource.canRead() } returns true
        every { calendarSource.queryCalendars() } returns calendars
        coEvery { repository.getExcludedCalendarIds() } returns setOf(5L)
        coEvery { repository.getStyle() } returns DdayStyle()
        coEvery { repository.saveStyle(any()) } returns Result.success(Unit)
        coEvery { repository.setCalendarExcluded(any(), any()) } returns Result.success(emptySet())
        coEvery { loadItems.load() } returns loadedPreview
        coJustRun { refresher.refresh() }
    }

    private fun createViewModel() = DdayViewModel(repository, loadItems, calendarSource, refresher).also {
        it.ioDispatcher = testDispatcherRule.testDispatcher
    }

    /** 첫 불러오기를 끝내고, 그 불러오기가 예약한 위젯 갱신까지 지나간 뒤 기록을 비워 이후 호출만 세도록 함 */
    private fun loadScreen(viewModel: DdayViewModel) {
        viewModel.refresh()
        waitCoroutine()
        clearMocks(refresher, answers = false)
    }

    /**
     * 불러오기가 권한, 캘린더 선택 상태, 스타일, 미리보기를 상태에 반영하는지 검증
     *
     * Given: 읽기 권한 있음, 캘린더 2개(5번 제외), 제목 글씨 30인 스타일, 미리보기 Loaded
     * When: refresh
     * Then: permissionGranted true, included는 [true, false], style·preview 반영, loaded true
     */
    @Test
    fun test_01_refreshLoadsPermissionCalendarsStylePreview() = runTest {
        /** Given **/
        val style = DdayStyle().with(DdayStyleField.TITLE_TEXT_SIZE, 30)
        coEvery { repository.getStyle() } returns style
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.loaded)

        /** When **/
        viewModel.refresh()
        waitCoroutine()

        /** Then **/
        val state = viewModel.uiState.value
        assertTrue(state.loaded)
        assertTrue(state.permissionGranted)
        assertEquals(listOf(true, false), state.calendars.map { it.included })
        assertEquals(calendars, state.calendars.map { it.calendar })
        assertEquals(style, state.style)
        assertEquals(loadedPreview, state.preview)
    }

    /**
     * 권한이 없으면 캘린더 목록을 조회하지 않고 비워 두는지 검증
     *
     * Given: canRead false, 미리보기 NoPermission
     * When: refresh
     * Then: permissionGranted false, calendars 비어 있음, queryCalendars 호출 없음, preview NoPermission
     */
    @Test
    fun test_02_refreshWithoutPermission() = runTest {
        /** Given **/
        every { calendarSource.canRead() } returns false
        coEvery { loadItems.load() } returns DdayLoadResult.NoPermission
        val viewModel = createViewModel()

        /** When **/
        viewModel.refresh()
        waitCoroutine()

        /** Then **/
        val state = viewModel.uiState.value
        assertTrue(state.loaded)
        assertFalse(state.permissionGranted)
        assertTrue(state.calendars.isEmpty())
        assertEquals(DdayLoadResult.NoPermission, state.preview)
        verify(exactly = 0) { calendarSource.queryCalendars() }
    }

    /**
     * 제외했던 캘린더를 다시 켜면 저장하고 상태·미리보기·위젯을 갱신하는지 검증
     *
     * Given: 5번 캘린더가 제외된 불러오기 완료 상태
     * When: setCalendarIncluded(5, true)
     * Then: setCalendarExcluded(5, false) 호출, 5번 included true, 미리보기 다시 조회(총 2회), 위젯 갱신 1회
     */
    @Test
    fun test_03_setCalendarIncludedSavesAndRefreshesWidget() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        loadScreen(viewModel)

        /** When **/
        viewModel.setCalendarIncluded(5, true)
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { repository.setCalendarExcluded(5, false) }
        assertEquals(listOf(true, true), viewModel.uiState.value.calendars.map { it.included })
        coVerify(exactly = 2) { loadItems.load() }
        coVerify(exactly = 1) { refresher.refresh() }
    }

    /**
     * 범위 끝에서는 저장하지 않고, 범위 안에서는 값을 바꿔 저장한 뒤 위젯을 갱신하는지 검증
     *
     * Given: 제목 글씨 48(최댓값)로 불러온 상태
     * When: adjustStyle(TITLE_TEXT_SIZE, +1), 이어서 제목 글씨 16에서 +1
     * Then: 48은 그대로이고 saveStyle 호출 없음, 16은 17로 saveStyle 호출 뒤 위젯 갱신
     */
    @Test
    fun test_04_adjustStyleClampsAndSaves() = runTest {
        /** Given **/
        coEvery { repository.getStyle() } returns DdayStyle().with(DdayStyleField.TITLE_TEXT_SIZE, 48)
        val maxViewModel = createViewModel()
        loadScreen(maxViewModel)

        /** When **/
        maxViewModel.adjustStyle(DdayStyleField.TITLE_TEXT_SIZE, 1)
        advanceCoroutineTime(1_000)

        /** Then **/
        assertEquals(48, maxViewModel.uiState.value.style.titleTextSize)
        coVerify(exactly = 0) { repository.saveStyle(any()) }
        coVerify(exactly = 0) { refresher.refresh() }

        /** Given **/
        coEvery { repository.getStyle() } returns DdayStyle()
        val viewModel = createViewModel()
        loadScreen(viewModel)

        /** When **/
        viewModel.adjustStyle(DdayStyleField.TITLE_TEXT_SIZE, 1)
        advanceCoroutineTime(1_000)

        /** Then **/
        assertEquals(17, viewModel.uiState.value.style.titleTextSize)
        coVerify(exactly = 1) { repository.saveStyle(DdayStyle().with(DdayStyleField.TITLE_TEXT_SIZE, 17)) }
        coVerify(exactly = 1) { refresher.refresh() }
    }

    /**
     * 스타일 저장이 실패하면 토스트 이벤트를 보내고 값을 되돌리는지 검증
     *
     * Given: 저장이 실패를 돌려주는 상태
     * When: adjustStyle(TITLE_TEXT_SIZE, +1)
     * Then: EventShowToast(R.string.dday_save_failed), 제목 글씨 16 유지, 위젯 갱신 없음
     */
    @Test
    fun test_05_saveFailureShowsToast() = runTest {
        /** Given **/
        coEvery { repository.saveStyle(any()) } returns Result.failure(IllegalStateException("저장 실패"))
        val viewModel = createViewModel()
        loadScreen(viewModel)

        /** When **/
        val events = collectDuring(viewModel.events) {
            viewModel.adjustStyle(DdayStyleField.TITLE_TEXT_SIZE, 1)
            advanceCoroutineTime(1_000)
        }

        /** Then **/
        assertEquals(listOf(DdayViewModel.EventShowToast(R.string.dday_save_failed)), events)
        assertEquals(16, viewModel.uiState.value.style.titleTextSize)
        coVerify(exactly = 0) { refresher.refresh() }
    }

    /**
     * 짧은 시간에 여러 번 조절해도 위젯 갱신은 디바운스로 한 번만 하는지 검증
     *
     * Given: 불러오기 완료 상태
     * When: 300ms 안에 adjustStyle 3번 호출 후 디바운스 시간 경과
     * Then: saveStyle 3번, 디바운스 시간 전에는 갱신 없음, 이후 refresher.refresh() 1번
     */
    @Test
    fun test_06_rapidAdjustRefreshesWidgetOnce() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        loadScreen(viewModel)

        /** When **/
        viewModel.adjustStyle(DdayStyleField.TITLE_TEXT_SIZE, 1)
        advanceCoroutineTime(100)
        viewModel.adjustStyle(DdayStyleField.TITLE_TEXT_SIZE, 1)
        advanceCoroutineTime(100)
        viewModel.adjustStyle(DdayStyleField.TITLE_TEXT_SIZE, 1)
        advanceCoroutineTime(DdayViewModel.WIDGET_REFRESH_DEBOUNCE_MILLIS - 1)

        /** Then **/
        coVerify(exactly = 3) { repository.saveStyle(any()) }
        coVerify(exactly = 0) { refresher.refresh() }

        /** When **/
        advanceCoroutineTime(1)

        /** Then **/
        coVerify(exactly = 1) { refresher.refresh() }
        assertEquals(19, viewModel.uiState.value.style.titleTextSize)
    }

    /**
     * 캘린더 선택 저장이 실패하면 토스트를 보내고 상태와 위젯을 그대로 두는지 검증
     *
     * Given: 5번 캘린더가 제외된 불러오기 완료 상태, 저장 실패
     * When: setCalendarIncluded(5, true)
     * Then: EventShowToast(R.string.dday_save_failed), included [true, false] 유지, 미리보기 재조회와 위젯 갱신 없음
     */
    @Test
    fun test_07_calendarSaveFailureShowsToastAndKeepsState() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        loadScreen(viewModel)
        coEvery { repository.setCalendarExcluded(any(), any()) } returns Result.failure(IllegalStateException("저장 실패"))

        /** When **/
        val events = collectDuring(viewModel.events) {
            viewModel.setCalendarIncluded(5, true)
            advanceCoroutineTime(1_000)
        }

        /** Then **/
        assertEquals(listOf(DdayViewModel.EventShowToast(R.string.dday_save_failed)), events)
        assertEquals(listOf(true, false), viewModel.uiState.value.calendars.map { it.included })
        coVerify(exactly = 1) { loadItems.load() }
        coVerify(exactly = 0) { refresher.refresh() }
    }

    /**
     * 디바운스 대기 중에 ViewModel이 지워져도 위젯 갱신이 실행되는지 검증
     *
     * Given: 불러오기 완료 후 스타일을 조절해 위젯 갱신이 대기 중인 ViewModelStore 소유 ViewModel
     * When: 디바운스 시간 전에 ViewModelStore.clear()
     * Then: refresher.refresh() 1번, 이후 시간이 지나도 추가 호출 없음
     */
    @Test
    fun test_08_clearedWhilePendingStillRefreshesWidget() = runTest {
        /** Given **/
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            viewModelFactory { initializer { createViewModel() } },
        )[DdayViewModel::class.java]
        loadScreen(viewModel)
        viewModel.adjustStyle(DdayStyleField.TITLE_TEXT_SIZE, 1)
        advanceCoroutineTime(100)
        coVerify(exactly = 0) { refresher.refresh() }

        /** When **/
        store.clear()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { refresher.refresh() }
        advanceCoroutineTime(1_000)
        coVerify(exactly = 1) { refresher.refresh() }
    }

    /**
     * 첫 불러오기에는 위젯 갱신을 예약하고, 권한이 그대로면 다시 불러와도 예약하지 않는지 검증
     *
     * Given: 읽기 권한 있음
     * When: refresh 두 번(두 번째는 권한 변화 없음)
     * Then: 첫 불러오기 뒤 refresher.refresh() 1번, 두 번째 불러오기 뒤에도 1번
     */
    @Test
    fun test_09_firstLoadRefreshesWidgetOnlyOnce() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.refresh()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { refresher.refresh() }

        /** When **/
        viewModel.refresh()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { refresher.refresh() }
    }

    /**
     * 불러온 뒤 권한이 바뀌면 위젯 갱신을 예약하는지 검증
     *
     * Given: 권한 없음으로 불러온 상태
     * When: 권한이 허용된 채 refresh
     * Then: permissionGranted true, refresher.refresh() 1번 추가
     */
    @Test
    fun test_10_permissionChangeRefreshesWidget() = runTest {
        /** Given **/
        every { calendarSource.canRead() } returns false
        val viewModel = createViewModel()
        loadScreen(viewModel)
        every { calendarSource.canRead() } returns true

        /** When **/
        viewModel.refresh()
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.permissionGranted)
        coVerify(exactly = 1) { refresher.refresh() }
    }
}
