package com.black.feature.todo.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.black.core.viewmodel.ViewModelEvent
import com.black.feature.todo.R
import com.black.feature.todo.data.TodoRepository
import com.black.feature.todo.data.TodoSettingsRepository
import com.black.feature.todo.data.usecase.SyncTodoUseCase
import com.black.feature.todo.data.usecase.TodoStoreClearException
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoConnection
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.feature.todo.model.TodoSyncState
import com.black.feature.todo.model.TodoSyncStatus
import com.black.feature.todo.notification.TodoNotifier
import com.black.feature.todo.notification.TodoPermissionStatus
import com.black.feature.todo.notification.TodoReminderScheduler
import com.black.feature.todo.worker.TodoSyncScheduler
import com.black.test.BaseTest
import com.black.test.collectDuring
import com.black.test.collectValues
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone

/** [TodoViewModel]의 초기 로드·동기화·연결 저장 검증·구획 구성·이벤트 발송 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoViewModelTest : BaseTest() {

    @MockK
    lateinit var settings: TodoSettingsRepository

    @MockK
    lateinit var repository: TodoRepository

    @MockK
    lateinit var synchronizer: SyncTodoUseCase

    @MockK
    lateinit var scheduler: TodoSyncScheduler

    @MockK
    lateinit var permissionStatus: TodoPermissionStatus

    @MockK
    lateinit var alarms: TodoReminderScheduler

    @MockK
    lateinit var notifier: TodoNotifier

    private val connectionFlow = MutableStateFlow(TodoConnection("", ""))
    private val tasksFlow = MutableStateFlow(emptyList<Todo>())
    private val dataSourceId = "41cffbf344f88196a0dd000b5dbcdc02"

    @Before
    fun setupViewModel() {
        every { settings.connection } returns connectionFlow
        every { settings.lastSync } returns MutableStateFlow<TodoSyncState?>(null)
        coEvery { settings.getConnection() } answers { connectionFlow.value }
        every { repository.tasks } returns tasksFlow
        coEvery { synchronizer.sync() } returns TodoSyncStatus.SUCCESS
        coEvery { synchronizer.replaceConnection(any()) } returns Result.success(Unit)
        justRun { scheduler.schedulePeriodic() }
        justRun { scheduler.cancelPeriodic() }
        every { permissionStatus.canShowTodoNotifications() } returns true
        every { permissionStatus.canScheduleExactAlarms() } returns true
        coJustRun { alarms.reschedule() }
        justRun { notifier.cancelTimeReminder(any()) }
    }

    /** ViewModel 생성 후 init의 비동기 로드 완료까지 대기 */
    private fun createViewModel() = TodoViewModel(settings, repository, synchronizer, scheduler, permissionStatus, alarms, notifier)
        .also { waitCoroutine() }

    private fun task(id: String) = Todo(
        pageId = id,
        title = id,
        done = false,
        due = null,
        reminder = TodoReminder.NONE,
        content = "",
        lastEditedTime = Instant.EPOCH,
        url = "https://www.notion.so/$id",
    )

    /**
     * 연결이 없으면 설정이 펼쳐지고, 연결을 읽은 뒤에만 connectionLoaded가 켜지며, refresh가 아무것도 하지 않는지 검증
     *
     * Given: 빈 연결
     * When: ViewModel 생성 후 refresh 호출
     * Then: 로드 전 connectionLoaded false, 로드 후 true, configured false, settingsExpanded true,
     *   sync·schedulePeriodic 호출 없음, isRefreshing false
     */
    @Test
    fun test_01_notConfiguredExpandsSettingsAndSkipsSync() = runTest {
        /** Given **/
        val viewModel = TodoViewModel(settings, repository, synchronizer, scheduler, permissionStatus, alarms, notifier)
        assertFalse(viewModel.uiState.value.connectionLoaded)
        waitCoroutine()

        /** When **/
        viewModel.refresh()
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.connectionLoaded)
        assertFalse(viewModel.uiState.value.configured)
        assertTrue(viewModel.uiState.value.settingsExpanded)
        coVerify(exactly = 0) { synchronizer.sync() }
        verify(exactly = 0) { scheduler.schedulePeriodic() }
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    /**
     * 연결이 있으면 생성 때는 주기 작업 예약만 하고, refresh 호출 때 동기화하는지 검증
     *
     * Given: 저장된 연결
     * When: ViewModel 생성, 이어서 refresh 호출
     * Then: 생성 뒤 schedulePeriodic 1회·sync 0회, refresh 뒤 sync 1회, isRefreshing false
     */
    @Test
    fun test_02_configuredSchedulesOnInitAndSyncsOnRefresh() = runTest {
        /** Given **/
        connectionFlow.value = TodoConnection("tok", dataSourceId)

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        assertTrue(viewModel.uiState.value.configured)
        assertFalse(viewModel.uiState.value.settingsExpanded)
        verify(exactly = 1) { scheduler.schedulePeriodic() }
        coVerify(exactly = 0) { synchronizer.sync() }

        /** When **/
        viewModel.refresh()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { synchronizer.sync() }
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    /**
     * 동기화가 예외를 던져도 표시가 꺼지고 실패 토스트가 나오는지 검증
     *
     * Given: 연결 있는 ViewModel, sync가 IllegalStateException을 던짐
     * When: refresh 호출
     * Then: isRefreshing false, EventShowToast(todo_sync_failed) 수신
     */
    @Test
    fun test_03_refreshClearsIndicatorOnException() = runTest {
        /** Given **/
        connectionFlow.value = TodoConnection("tok", dataSourceId)
        coEvery { synchronizer.sync() } throws IllegalStateException()
        val viewModel = createViewModel()

        /** When **/
        viewModel.refresh()
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(
            TodoViewModel.EventShowToast(R.string.todo_sync_failed),
            viewModel.events.collectValues(1).single(),
        )
    }

    /**
     * 입력을 다듬어 저장하고 형식이 틀린 ID는 거르는지 검증
     *
     * Given: 토큰 "  tok \n", ID " 41cf... "
     * When: saveConnection 호출, 이어서 ID를 URL로 바꿔 다시 호출
     * Then: 다듬은 연결로 replaceConnection 1회, URL이면 저장 없이 invalid_data_source_id 토스트
     */
    @Test
    fun test_04_saveConnectionTrimsAndValidates() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.updateTokenInput("  tok \n")
        viewModel.updateDataSourceIdInput(" $dataSourceId ")

        /** When **/
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { synchronizer.replaceConnection(TodoConnection("tok", dataSourceId)) }
        verify(exactly = 1) { scheduler.schedulePeriodic() }

        /** When **/
        viewModel.updateDataSourceIdInput("https://www.notion.so/$dataSourceId")
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { synchronizer.replaceConnection(any()) }
        assertEquals(
            TodoViewModel.EventShowToast(R.string.todo_invalid_data_source_id),
            viewModel.events.collectValues(1).single(),
        )
    }

    /**
     * 한쪽만 입력하면 저장하지 않고 안내하는지 검증
     *
     * Given: 토큰만 입력
     * When: saveConnection 호출
     * Then: 저장 없음 + connection_incomplete 토스트
     */
    @Test
    fun test_05_saveConnectionWithOneBlankShowsToast() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.updateTokenInput("tok")

        /** When **/
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 0) { synchronizer.replaceConnection(any()) }
        assertEquals(
            TodoViewModel.EventShowToast(R.string.todo_connection_incomplete),
            viewModel.events.collectValues(1).single(),
        )
    }

    /**
     * 둘 다 비우면 연결을 해제하는지 검증
     *
     * Given: 저장된 연결이 있고 입력을 모두 비움
     * When: saveConnection 호출
     * Then: replaceConnection(빈 연결) 1회, cancelPeriodic 1회, settingsExpanded true, disconnected 토스트
     */
    @Test
    fun test_06_clearingBothDisconnects() = runTest {
        /** Given **/
        connectionFlow.value = TodoConnection("tok", dataSourceId)
        val viewModel = createViewModel()
        viewModel.updateTokenInput("  ")
        viewModel.updateDataSourceIdInput("")

        /** When **/
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { synchronizer.replaceConnection(TodoConnection("", "")) }
        verify(exactly = 1) { scheduler.cancelPeriodic() }
        assertTrue(viewModel.uiState.value.settingsExpanded)
        assertEquals(
            TodoViewModel.EventShowToast(R.string.todo_disconnected),
            viewModel.events.collectValues(1).single(),
        )
    }

    /**
     * 저장본 항목이 모두 구획에 들어가는지 검증
     *
     * Given: 기한 없는 할 일 2개
     * When: ViewModel 생성
     * Then: NO_DUE 구획 하나에 두 항목
     */
    @Test
    fun test_07_storedTasksGroupedIntoSections() = runTest {
        /** Given **/
        tasksFlow.value = listOf(task("a"), task("b"))

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val section = viewModel.uiState.value.taskSections.single()
        assertEquals(TodoSectionType.NO_DUE, section.type)
        assertEquals(listOf("a", "b"), section.tasks.map { it.pageId })
    }

    /**
     * 항목을 열면 노션 URL 이벤트가 나오는지 검증
     *
     * Given: 할 일 하나
     * When: openTask 호출
     * Then: EventOpenUrl(task.url) 수신
     */
    @Test
    fun test_08_openTaskSendsUrl() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val target = task("t")

        /** When **/
        viewModel.openTask(target)

        /** Then **/
        assertEquals(TodoViewModel.EventOpenUrl(target.url), viewModel.events.collectValues(1).single())
    }

    /**
     * refreshToday 호출 뒤 바뀐 날짜로 구획이 다시 계산되는지 검증
     *
     * Given: 현재 시각 공급자가 2026-11-18 12시(서울)이고 기한이 11-19인 할 일 1개(UPCOMING)
     * When: 공급자를 2026-11-19 12시로 바꾸고 refreshToday 호출
     * Then: 같은 할 일이 TODAY 구획으로 이동
     */
    @Test
    fun test_09_refreshTodayRegroupsSections() = runTest {
        /** Given **/
        val originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            tasksFlow.value = listOf(task("t").copy(due = TodoDue.Date(LocalDate.of(2026, 11, 19))))
            val viewModel = TodoViewModel(settings, repository, synchronizer, scheduler, permissionStatus, alarms, notifier)
            viewModel.now = { Instant.ofEpochMilli(1_794_970_800_000L) } // 2026-11-18T03:00:00Z = 서울 12:00
            viewModel.refreshToday()
            waitCoroutine()
            assertEquals(TodoSectionType.UPCOMING, viewModel.uiState.value.taskSections.single().type)

            /** When **/
            viewModel.now = { Instant.ofEpochMilli(1_795_057_200_000L) } // 2026-11-19T03:00:00Z = 서울 12:00
            viewModel.refreshToday()
            waitCoroutine()

            /** Then **/
            assertEquals(TodoSectionType.TODAY, viewModel.uiState.value.taskSections.single().type)
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    /**
     * 동기화 중에 새로고침 코루틴이 취소되면 표시만 꺼지고 토스트가 없는지 검증
     *
     * Given: 연결 있는 ViewModel, 취소될 때까지 끝나지 않는 sync로 새로고침 진행 중
     * When: 화면 종료처럼 ViewModelStore 정리로 ViewModel 범위 취소
     * Then: isRefreshing false, EventShowToast 이벤트 없음
     */
    @Test
    fun test_10_refreshCancellationClearsIndicatorWithoutToast() = runTest {
        /** Given **/
        connectionFlow.value = TodoConnection("tok", dataSourceId)
        coEvery { synchronizer.sync() } coAnswers { awaitCancellation() }
        val receivedEvents = mutableListOf<ViewModelEvent>()
        val viewModelStore = ViewModelStore()
        val viewModel = createStoredViewModel(viewModelStore)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(receivedEvents) }
        viewModel.refresh()
        waitCoroutine()
        assertTrue(viewModel.uiState.value.isRefreshing)

        /** When **/
        viewModelStore.clear()
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertTrue(receivedEvents.none { it is TodoViewModel.EventShowToast })
    }

    /**
     * 새로고침 진행 중에 연결을 저장하면 진행 중인 새로고침을 끝내고 새 연결로 다시 동기화하는지 검증
     *
     * Given: 연결 있는 ViewModel에서 첫 sync가 붙잡힌 채 새로고침 진행 중
     * When: 새 토큰으로 saveConnection 호출
     * Then: replaceConnection 1회, sync 2회, isRefreshing false
     */
    @Test
    fun test_11_saveConnectionDuringRefreshSyncsAgain() = runTest {
        /** Given **/
        connectionFlow.value = TodoConnection("tok", dataSourceId)
        val gate = CompletableDeferred<TodoSyncStatus>()
        var syncCount = 0
        coEvery { synchronizer.sync() } coAnswers {
            syncCount++
            if (syncCount == 1) gate.await() else TodoSyncStatus.SUCCESS
        }
        val viewModel = createViewModel()
        viewModel.refresh()
        waitCoroutine()
        assertTrue(viewModel.uiState.value.isRefreshing)

        /** When **/
        viewModel.updateTokenInput("tok-2")
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { synchronizer.replaceConnection(TodoConnection("tok-2", dataSourceId)) }
        coVerify(exactly = 2) { synchronizer.sync() }
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    /**
     * 토큰에 공백이나 출력 가능한 ASCII 밖의 문자가 있으면 저장하지 않고 안내하는지 검증
     *
     * Given: 중간에 줄바꿈이 든 토큰, 한글이 든 토큰과 올바른 data source ID
     * When: 각각 saveConnection 호출
     * Then: replaceConnection 호출 없음, invalid_token 토스트 2회
     */
    @Test
    fun test_12_saveConnectionRejectsInvalidToken() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.updateDataSourceIdInput(dataSourceId)

        /** When **/
        viewModel.updateTokenInput("secret\npart")
        viewModel.saveConnection()
        viewModel.updateTokenInput("secret토큰")
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 0) { synchronizer.replaceConnection(any()) }
        assertEquals(
            List(2) { TodoViewModel.EventShowToast(R.string.todo_invalid_token) },
            viewModel.events.collectValues(2),
        )
    }

    /** 연결이 설정된 상태에서 반영 대기 변경 호출을 허용하는 기본 응답 */
    private fun allowWrites() {
        connectionFlow.value = TodoConnection("tok", dataSourceId)
        coEvery { repository.markDone(any(), any(), any()) } returns Unit
        coEvery { repository.add(any()) } returns Unit
        justRun { scheduler.enqueueSyncNow() }
    }

    /**
     * toggleDone이 완료 여부를 뒤집어 저장하고 바로 동기화를 요청하는지 검증
     *
     * Given: 미완료 항목 t, 현재 시각 1000
     * When: toggleDone 호출
     * Then: markDone("t", true, 1000)과 enqueueSyncNow 1회
     */
    @Test
    fun test_13_toggleDoneMarksAndEnqueuesSync() = runTest {
        /** Given **/
        allowWrites()
        val viewModel = createViewModel()
        viewModel.now = { Instant.ofEpochMilli(1000L) }

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { repository.markDone("t", true, Instant.ofEpochMilli(1000L)) }
        verify(exactly = 1) { scheduler.enqueueSyncNow() }
    }

    /**
     * markDone이 실패하면 change_failed 토스트를 보내는지 검증
     *
     * Given: markDone이 예외를 던짐
     * When: toggleDone 호출
     * Then: change_failed 토스트, enqueueSyncNow와 다시 예약 호출 없음
     */
    @Test
    fun test_14_toggleDoneFailureShowsToast() = runTest {
        /** Given **/
        allowWrites()
        coEvery { repository.markDone(any(), any(), any()) } throws IllegalStateException()
        val viewModel = createViewModel()

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()

        /** Then **/
        assertEquals(TodoViewModel.EventShowToast(R.string.todo_change_failed), viewModel.events.collectValues(1).single())
        verify(exactly = 0) { scheduler.enqueueSyncNow() }
        coVerify(exactly = 0) { alarms.reschedule() }
    }

    /**
     * 이름이 비면 title_required 토스트만 보내고 저장하지 않는지 검증
     *
     * Given: 공백뿐인 이름
     * When: addTask 호출
     * Then: title_required 토스트, add 호출 없음
     */
    @Test
    fun test_15_addTaskBlankTitleShowsToast() = runTest {
        /** Given **/
        allowWrites()
        val viewModel = createViewModel()
        viewModel.updateNewTitle("   ")

        /** When **/
        viewModel.addTask()
        waitCoroutine()

        /** Then **/
        assertEquals(TodoViewModel.EventShowToast(R.string.todo_title_required), viewModel.events.collectValues(1).single())
        coVerify(exactly = 0) { repository.add(any()) }
    }

    /**
     * 시각 알림인데 기한 시각이 없으면 안내하는지 검증
     *
     * Given: 이름, 날짜만 있고 알림 TIME
     * When: addTask 호출
     * Then: time_reminder_needs_time 토스트, add 호출 없음
     */
    @Test
    fun test_16_addTaskTimeReminderWithoutTimeShowsToast() = runTest {
        /** Given **/
        allowWrites()
        val viewModel = createViewModel()
        viewModel.updateNewTitle("a")
        viewModel.updateNewDueDate(LocalDate.of(2026, 11, 20))
        viewModel.updateNewReminder(TodoReminder.TIME)

        /** When **/
        viewModel.addTask()
        waitCoroutine()

        /** Then **/
        assertEquals(
            TodoViewModel.EventShowToast(R.string.todo_time_reminder_needs_time),
            viewModel.events.collectValues(1).single(),
        )
        coVerify(exactly = 0) { repository.add(any()) }
    }

    /**
     * 날짜와 시각이 있는 항목을 추가하면 반영 대기 항목으로 저장하고 입력을 비우는지 검증
     *
     * Given: 이름 " 장보기 ", 날짜 2026-11-20, 시각 09:30, 알림 TIME, 서울 시간대
     * When: addTask 호출
     * Then: add 인자가 DateTime(1795134600000)·pendingSync true·local- ID, 입력 기본값, addExpanded false, enqueueSyncNow 1회
     */
    @Test
    fun test_17_addTaskSavesPendingTaskAndResetsInput() = runTest {
        /** Given **/
        allowWrites()
        val savedTask = slot<Todo>()
        coEvery { repository.add(capture(savedTask)) } returns Unit
        val defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            val viewModel = createViewModel()
            viewModel.now = { Instant.ofEpochMilli(2000L) }
            viewModel.toggleAddSection()
            viewModel.updateNewTitle(" 장보기 ")
            viewModel.updateNewDueDate(LocalDate.of(2026, 11, 20))
            viewModel.updateNewDueTime(LocalTime.of(9, 30))
            viewModel.updateNewReminder(TodoReminder.TIME)

            /** When **/
            viewModel.addTask()
            waitCoroutine()

            /** Then **/
            val added = savedTask.captured
            assertEquals(TodoDue.DateTime(Instant.ofEpochMilli(1795134600000L)), added.due)
            assertEquals("장보기", added.title)
            assertEquals(TodoReminder.TIME, added.reminder)
            assertEquals(Instant.ofEpochMilli(2000L), added.lastEditedTime)
            assertFalse(added.done)
            assertTrue(added.pendingSync)
            assertTrue(added.pageId.startsWith("local-"))
            val state = viewModel.uiState.value
            assertEquals("", state.newTitleInput)
            assertNull(state.newDueDate)
            assertNull(state.newDueTime)
            assertEquals(TodoReminder.NONE, state.newReminder)
            assertFalse(state.addExpanded)
            verify(exactly = 1) { scheduler.enqueueSyncNow() }
        } finally {
            TimeZone.setDefault(defaultZone)
        }
    }

    /**
     * 날짜만 정하면 날짜만 있는 기한으로 저장하는지 검증
     *
     * Given: 이름, 날짜 2026-11-20, 시각 없음
     * When: addTask 호출
     * Then: add 인자의 due가 Date(2026-11-20)
     */
    @Test
    fun test_18_addTaskDateOnlyDue() = runTest {
        /** Given **/
        allowWrites()
        val savedTask = slot<Todo>()
        coEvery { repository.add(capture(savedTask)) } returns Unit
        val viewModel = createViewModel()
        viewModel.updateNewTitle("a")
        viewModel.updateNewDueDate(LocalDate.of(2026, 11, 20))

        /** When **/
        viewModel.addTask()
        waitCoroutine()

        /** Then **/
        assertEquals(TodoDue.Date(LocalDate.of(2026, 11, 20)), savedTask.captured.due)
    }

    /**
     * 저장이 실패하면 add_failed 토스트를 보내고 입력을 지우지 않는지 검증
     *
     * Given: add가 예외를 던짐, 이름 입력과 펼친 상태
     * When: addTask 호출
     * Then: add_failed 토스트, 입력 유지, enqueueSyncNow와 다시 예약 호출 없음
     */
    @Test
    fun test_19_addTaskFailureKeepsInput() = runTest {
        /** Given **/
        allowWrites()
        coEvery { repository.add(any()) } throws IllegalStateException()
        val viewModel = createViewModel()
        viewModel.toggleAddSection()
        viewModel.updateNewTitle("a")

        /** When **/
        viewModel.addTask()
        waitCoroutine()

        /** Then **/
        assertEquals(TodoViewModel.EventShowToast(R.string.todo_add_failed), viewModel.events.collectValues(1).single())
        assertEquals("a", viewModel.uiState.value.newTitleInput)
        assertTrue(viewModel.uiState.value.addExpanded)
        verify(exactly = 0) { scheduler.enqueueSyncNow() }
        coVerify(exactly = 0) { alarms.reschedule() }
    }

    /**
     * URL이 빈 항목을 열어도 이벤트가 없는지 검증
     *
     * Given: url이 빈 항목
     * When: openTask 호출
     * Then: 이벤트 없음
     */
    @Test
    fun test_20_openTaskWithBlankUrlSendsNothing() = runTest {
        /** Given **/
        val receivedEvents = mutableListOf<ViewModelEvent>()
        val viewModel = TodoViewModel(settings, repository, synchronizer, scheduler, permissionStatus, alarms, notifier)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(receivedEvents) }
        waitCoroutine()

        /** When **/
        viewModel.openTask(task("t").copy(url = ""))
        waitCoroutine()

        /** Then **/
        assertTrue(receivedEvents.isEmpty())
    }

    /**
     * 기한 시각은 날짜가 있을 때만 받고, 날짜를 지우면 시각도 지우는지 검증
     *
     * Given: 날짜 없는 상태
     * When: updateNewDueTime(09:30), 날짜 입력 후 다시 09:30, updateNewDueDate(null)
     * Then: 첫 호출은 무시, 둘째는 반영, 날짜 지우면 시각도 null
     */
    @Test
    fun test_21_dueTimeRequiresDateAndClearsWithDate() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When / Then **/
        viewModel.updateNewDueTime(LocalTime.of(9, 30))
        assertNull(viewModel.uiState.value.newDueTime)
        viewModel.updateNewDueDate(LocalDate.of(2026, 11, 20))
        viewModel.updateNewDueTime(LocalTime.of(9, 30))
        assertEquals(LocalTime.of(9, 30), viewModel.uiState.value.newDueTime)
        viewModel.updateNewDueDate(null)
        assertNull(viewModel.uiState.value.newDueDate)
        assertNull(viewModel.uiState.value.newDueTime)
    }

    /**
     * 연결은 저장됐으나 이전 목록 비우기가 실패하면 성공 경로를 밟고 안내하는지 검증
     *
     * Given: replaceConnection이 TodoStoreClearException 실패
     * When: 올바른 연결로 saveConnection 호출
     * Then: schedulePeriodic 1회, clear_failed 토스트
     */
    @Test
    fun test_22_saveConnectionStoreClearFailureSchedulesAndNotifies() = runTest {
        /** Given **/
        coEvery { synchronizer.replaceConnection(any()) } returns Result.failure(TodoStoreClearException())
        val viewModel = createViewModel()
        viewModel.updateTokenInput("tok")
        viewModel.updateDataSourceIdInput(dataSourceId)

        /** When **/
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        verify(exactly = 1) { scheduler.schedulePeriodic() }
        assertFalse(viewModel.uiState.value.settingsExpanded)
        assertEquals(TodoViewModel.EventShowToast(R.string.todo_clear_failed), viewModel.events.collectValues(1).single())
    }

    /**
     * 연결 해제에서 이전 목록 비우기가 실패해도 해제 경로를 밟고 안내하는지 검증
     *
     * Given: 두 입력이 빈 상태, replaceConnection이 TodoStoreClearException 실패
     * When: saveConnection 호출
     * Then: cancelPeriodic 1회, 설정 펼침, disconnected 다음 clear_failed 토스트
     */
    @Test
    fun test_23_disconnectStoreClearFailureCancelsAndNotifies() = runTest {
        /** Given **/
        coEvery { synchronizer.replaceConnection(any()) } returns Result.failure(TodoStoreClearException())
        val viewModel = createViewModel()

        /** When **/
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        verify(exactly = 1) { scheduler.cancelPeriodic() }
        assertTrue(viewModel.uiState.value.settingsExpanded)
        assertEquals(
            listOf(
                TodoViewModel.EventShowToast(R.string.todo_disconnected),
                TodoViewModel.EventShowToast(R.string.todo_clear_failed),
            ),
            viewModel.events.collectValues(2),
        )
    }

    /**
     * 연결 해제에서 그 밖의 실패는 save_failed만 안내하는지 검증
     *
     * Given: 두 입력이 빈 상태, replaceConnection이 일반 예외 실패
     * When: saveConnection 호출
     * Then: save_failed 토스트, cancelPeriodic 호출 없음
     */
    @Test
    fun test_24_disconnectOtherFailureShowsSaveFailed() = runTest {
        /** Given **/
        coEvery { synchronizer.replaceConnection(any()) } returns Result.failure(IllegalStateException())
        val viewModel = createViewModel()

        /** When **/
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        verify(exactly = 0) { scheduler.cancelPeriodic() }
        assertEquals(TodoViewModel.EventShowToast(R.string.todo_save_failed), viewModel.events.collectValues(1).single())
    }

    /**
     * 연결 저장 전에 진행 중인 새로고침이 끝나는지 검증
     *
     * Given: 첫 sync가 붙잡힌 채 새로고침 진행 중
     * When: 새 토큰으로 saveConnection 호출
     * Then: replaceConnection이 불리는 시점에 isRefreshing이 false
     */
    @Test
    fun test_25_saveConnectionCancelsRefreshBeforeReplace() = runTest {
        /** Given **/
        connectionFlow.value = TodoConnection("tok", dataSourceId)
        coEvery { synchronizer.sync() } coAnswers { CompletableDeferred<TodoSyncStatus>().await() }
        val viewModel = createViewModel()
        viewModel.refresh()
        waitCoroutine()
        assertTrue(viewModel.uiState.value.isRefreshing)
        var refreshingAtReplace: Boolean? = null
        coEvery { synchronizer.replaceConnection(any()) } coAnswers {
            refreshingAtReplace = viewModel.uiState.value.isRefreshing
            Result.success(Unit)
        }

        /** When **/
        viewModel.updateTokenInput("tok-2")
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        assertEquals(false, refreshingAtReplace)
    }

    /**
     * 연결 해제 전에 진행 중인 새로고침이 끝나는지 검증
     *
     * Given: 첫 sync가 붙잡힌 채 새로고침 진행 중
     * When: 두 입력을 비우고 saveConnection 호출
     * Then: replaceConnection이 불리는 시점에 isRefreshing이 false
     */
    @Test
    fun test_26_disconnectCancelsRefreshBeforeReplace() = runTest {
        /** Given **/
        connectionFlow.value = TodoConnection("tok", dataSourceId)
        coEvery { synchronizer.sync() } coAnswers { CompletableDeferred<TodoSyncStatus>().await() }
        val viewModel = createViewModel()
        viewModel.refresh()
        waitCoroutine()
        assertTrue(viewModel.uiState.value.isRefreshing)
        var refreshingAtReplace: Boolean? = null
        coEvery { synchronizer.replaceConnection(any()) } coAnswers {
            refreshingAtReplace = viewModel.uiState.value.isRefreshing
            Result.success(Unit)
        }

        /** When **/
        viewModel.updateTokenInput("")
        viewModel.updateDataSourceIdInput("")
        viewModel.saveConnection()
        waitCoroutine()

        /** Then **/
        assertEquals(false, refreshingAtReplace)
    }

    /**
     * 완료된 항목에서 toggleDone이 미완료로 되돌리는지 검증
     *
     * Given: 완료 항목 t, 현재 시각 1000
     * When: toggleDone 호출
     * Then: markDone("t", false, 1000)
     */
    @Test
    fun test_27_toggleDoneOnDoneTaskMarksUndone() = runTest {
        /** Given **/
        allowWrites()
        val viewModel = createViewModel()
        viewModel.now = { Instant.ofEpochMilli(1000L) }

        /** When **/
        viewModel.toggleDone(task("t").copy(done = true))
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { repository.markDone("t", false, Instant.ofEpochMilli(1000L)) }
    }

    /**
     * 저장이 끝나기 전에 addTask를 다시 불러도 한 번만 저장하는지 검증
     *
     * Given: add가 풀어 줄 때까지 붙잡히는 상태, 이름 입력
     * When: addTask 두 번 호출 후 add를 풂
     * Then: add 1회, enqueueSyncNow 1회, 이름 입력 비움
     */
    @Test
    fun test_28_addTaskIgnoresRepeatWhileSaving() = runTest {
        /** Given **/
        allowWrites()
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.add(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        viewModel.updateNewTitle("a")

        /** When **/
        viewModel.addTask()
        waitCoroutine()
        viewModel.addTask()
        waitCoroutine()
        gate.complete(Unit)
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { repository.add(any()) }
        verify(exactly = 1) { scheduler.enqueueSyncNow() }
        assertEquals("", viewModel.uiState.value.newTitleInput)
    }

    /**
     * 연결이 없으면 toggleDone이 저장하지 않는지 검증
     *
     * Given: 빈 연결, markDone·enqueueSyncNow 허용
     * When: toggleDone 호출
     * Then: markDone·enqueueSyncNow 호출 없음
     */
    @Test
    fun test_29_toggleDoneIgnoredWhenNotConfigured() = runTest {
        /** Given **/
        allowWrites()
        connectionFlow.value = TodoConnection("", "")
        val viewModel = createViewModel()

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 0) { repository.markDone(any(), any(), any()) }
        verify(exactly = 0) { scheduler.enqueueSyncNow() }
    }

    /**
     * refreshPermissions가 Todo 알림 표시 가능 여부를 상태에 반영하는지 검증
     *
     * Given: Todo 알림 표시 가능 확인이 false(앱 알림 허용이어도 Todo 채널이 꺼진 경우의 값)
     * When: refreshPermissions 호출, 이어서 표시 가능으로 바뀐 뒤 다시 호출
     * Then: todoNotificationsEnabled가 false, 이어서 true
     */
    @Test
    fun test_30_refreshPermissionsReflectsTodoNotificationsState() = runTest {
        /** Given **/
        every { permissionStatus.canShowTodoNotifications() } returns false
        val viewModel = createViewModel()

        /** When **/
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.todoNotificationsEnabled)

        /** When **/
        every { permissionStatus.canShowTodoNotifications() } returns true
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.todoNotificationsEnabled)
    }

    /**
     * openNotificationSettings가 알림 설정 이동 이벤트를 보내는지 검증
     *
     * Given: ViewModel
     * When: openNotificationSettings 호출
     * Then: EventOpenNotificationSettings 수신
     */
    @Test
    fun test_31_openNotificationSettingsSendsEvent() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.openNotificationSettings()

        /** Then **/
        assertEquals(TodoViewModel.EventOpenNotificationSettings, viewModel.events.collectValues(1).single())
    }

    /**
     * Todo를 추가하면 저장 뒤 시각 알림을 한 번 다시 예약하는지 검증
     *
     * Given: 연결 설정, 이름 입력
     * When: addTask 호출
     * Then: add 뒤 reschedule 1회
     */
    @Test
    fun test_32_addTaskReschedulesAlarms() = runTest {
        /** Given **/
        allowWrites()
        val viewModel = createViewModel()
        viewModel.updateNewTitle("a")

        /** When **/
        viewModel.addTask()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.add(any())
            alarms.reschedule()
        }
    }

    /**
     * 완료를 바꾸면 저장 뒤 시각 알림을 한 번 다시 예약하는지 검증
     *
     * Given: 연결 설정, 미완료 항목 t
     * When: toggleDone 호출
     * Then: markDone 뒤 reschedule 1회
     */
    @Test
    fun test_33_toggleDoneReschedulesAlarms() = runTest {
        /** Given **/
        allowWrites()
        val viewModel = createViewModel()

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { alarms.reschedule() }
        coVerifyOrder {
            repository.markDone("t", true, any())
            alarms.reschedule()
        }
    }

    /**
     * 다시 예약이 끝나지 않아도 저장을 마쳤으면 다음 addTask를 받는지 검증
     *
     * Given: 연결 설정, 다시 예약이 풀어 줄 때까지 붙잡히는 상태
     * When: 이름 a로 addTask, 저장이 끝난 뒤 이름 b로 addTask
     * Then: add가 a·b 순서로 2회, enqueueSyncNow 2회
     */
    @Test
    fun test_34_addTaskNotBlockedByPendingReschedule() = runTest {
        /** Given **/
        allowWrites()
        val savedTitles = mutableListOf<String>()
        coEvery { repository.add(any()) } coAnswers { savedTitles += firstArg<Todo>().title }
        val gate = CompletableDeferred<Unit>()
        coEvery { alarms.reschedule() } coAnswers { gate.await() }
        val viewModel = createViewModel()
        try {
            /** When **/
            viewModel.updateNewTitle("a")
            viewModel.addTask()
            waitCoroutine()
            viewModel.updateNewTitle("b")
            viewModel.addTask()
            waitCoroutine()

            /** Then **/
            assertEquals(listOf("a", "b"), savedTitles)
            verify(exactly = 2) { scheduler.enqueueSyncNow() }
        } finally {
            // 붙잡힌 다시 예약이 테스트 뒤에 남지 않게 풂
            gate.complete(Unit)
            waitCoroutine()
        }
    }

    /**
     * refreshPermissions가 Todo 알림과 정확한 알람 허용 여부를 각자의 상태에 반영하는지 검증
     *
     * Given: Todo 알림 표시 불가, 정확한 알람 허용
     * When: refreshPermissions 호출, 이어서 두 값을 뒤바꾼 뒤 다시 호출
     * Then: todoNotificationsEnabled false·exactAlarmPermissionGranted true, 이어서 true·false
     */
    @Test
    fun test_35_refreshPermissionsReflectsBothStates() = runTest {
        /** Given **/
        every { permissionStatus.canShowTodoNotifications() } returns false
        every { permissionStatus.canScheduleExactAlarms() } returns true
        val viewModel = createViewModel()

        /** When **/
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.todoNotificationsEnabled)
        assertTrue(viewModel.uiState.value.exactAlarmPermissionGranted)

        /** When **/
        every { permissionStatus.canShowTodoNotifications() } returns true
        every { permissionStatus.canScheduleExactAlarms() } returns false
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.todoNotificationsEnabled)
        assertFalse(viewModel.uiState.value.exactAlarmPermissionGranted)
    }

    /**
     * 정확한 알람이 거부에서 허용으로 바뀌면 시각 알림을 한 번 다시 예약하는지 검증
     *
     * Given: 정확한 알람 거부 상태로 refreshPermissions를 한 번 호출(첫 확인 거부라 다시 예약 1회)
     * When: 허용으로 바뀐 뒤 refreshPermissions 호출, 이어서 한 번 더 호출
     * Then: 거부 확인 뒤 reschedule 1회, 허용으로 바뀐 뒤 2회, 다시 호출해도 2회
     */
    @Test
    fun test_36_exactAlarmNewlyGrantedReschedulesOnce() = runTest {
        /** Given **/
        every { permissionStatus.canScheduleExactAlarms() } returns false
        val viewModel = createViewModel()
        viewModel.refreshPermissions()
        waitCoroutine()
        coVerify(exactly = 1) { alarms.reschedule() }

        /** When **/
        every { permissionStatus.canScheduleExactAlarms() } returns true
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 2) { alarms.reschedule() }
        assertTrue(viewModel.uiState.value.exactAlarmPermissionGranted)

        /** When **/
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 2) { alarms.reschedule() }
    }

    /**
     * 첫 확인에서 정확한 알람이 허용이면 다시 예약하지 않고, 다시 확인해도 부르지 않는지 검증
     *
     * Given: 정확한 알람 허용
     * When: refreshPermissions 두 번 호출
     * Then: reschedule 호출 없음, exactAlarmPermissionGranted true
     */
    @Test
    fun test_37_exactAlarmAlreadyGrantedSkipsReschedule() = runTest {
        /** Given **/
        every { permissionStatus.canScheduleExactAlarms() } returns true
        val viewModel = createViewModel()

        /** When **/
        viewModel.refreshPermissions()
        waitCoroutine()
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 0) { alarms.reschedule() }
        assertTrue(viewModel.uiState.value.exactAlarmPermissionGranted)
    }

    /**
     * openExactAlarmSettings가 정확한 알람 설정 이동 이벤트를 보내는지 검증
     *
     * Given: ViewModel
     * When: openExactAlarmSettings 호출
     * Then: EventOpenExactAlarmSettings 수신
     */
    @Test
    fun test_38_openExactAlarmSettingsSendsEvent() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.openExactAlarmSettings()

        /** Then **/
        assertEquals(TodoViewModel.EventOpenExactAlarmSettings, viewModel.events.collectValues(1).single())
    }

    /**
     * [viewModelStore]에 담은 ViewModel 생성 후 init의 비동기 로드 완료까지 대기
     *
     * [ViewModelStore.clear]로 화면 종료처럼 ViewModel 범위를 취소하는 테스트용.
     */
    private fun createStoredViewModel(viewModelStore: ViewModelStore): TodoViewModel {
        val factory = viewModelFactory {
            initializer { TodoViewModel(settings, repository, synchronizer, scheduler, permissionStatus, alarms, notifier) }
        }
        return ViewModelProvider.create(viewModelStore, factory)[TodoViewModel::class].also { waitCoroutine() }
    }

    /**
     * Todo 추가 뒤 다시 예약이 잠금을 기다리는 사이 화면을 닫아도 다시 예약이 끝까지 실행되는지 검증
     *
     * Given: 연결 설정, 다시 예약이 풀어 줄 때까지 붙잡히는 상태(다른 다시 예약이 잠금을 잡은 상황)
     * When: addTask 호출, 다시 예약이 붙잡힌 사이 ViewModelStore 정리 후 다시 예약을 풂
     * Then: 다시 예약 본문이 끝까지 실행됨
     */
    @Test
    fun test_39_addTaskRescheduleFinishesAfterViewModelCleared() = runTest {
        /** Given **/
        allowWrites()
        val gate = CompletableDeferred<Unit>()
        var rescheduleFinished = false
        coEvery { alarms.reschedule() } coAnswers {
            gate.await()
            rescheduleFinished = true
        }
        val viewModelStore = ViewModelStore()
        val viewModel = createStoredViewModel(viewModelStore)
        viewModel.updateNewTitle("a")

        /** When **/
        viewModel.addTask()
        waitCoroutine()
        coVerify(exactly = 1) { alarms.reschedule() }
        viewModelStore.clear()
        gate.complete(Unit)
        waitCoroutine()

        /** Then **/
        assertTrue(rescheduleFinished)
    }

    /**
     * 완료 체크 뒤 다시 예약이 잠금을 기다리는 사이 화면을 닫아도 다시 예약이 끝까지 실행되는지 검증
     *
     * Given: 연결 설정, 다시 예약이 풀어 줄 때까지 붙잡히는 상태(다른 다시 예약이 잠금을 잡은 상황)
     * When: toggleDone 호출, 다시 예약이 붙잡힌 사이 ViewModelStore 정리 후 다시 예약을 풂
     * Then: 다시 예약 본문이 끝까지 실행됨
     */
    @Test
    fun test_40_toggleDoneRescheduleFinishesAfterViewModelCleared() = runTest {
        /** Given **/
        allowWrites()
        val gate = CompletableDeferred<Unit>()
        var rescheduleFinished = false
        coEvery { alarms.reschedule() } coAnswers {
            gate.await()
            rescheduleFinished = true
        }
        val viewModelStore = ViewModelStore()
        val viewModel = createStoredViewModel(viewModelStore)

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()
        coVerify(exactly = 1) { alarms.reschedule() }
        viewModelStore.clear()
        gate.complete(Unit)
        waitCoroutine()

        /** Then **/
        assertTrue(rescheduleFinished)
    }

    /**
     * Todo 저장 중에 화면을 닫아도 저장 뒤 동기화 요청과 다시 예약이 끝까지 실행되고 입력은 그대로인지 검증
     *
     * Given: 연결 설정, add가 풀어 줄 때까지 붙잡히는 상태, 이름 a 입력
     * When: addTask 호출, add가 붙잡힌 사이 ViewModelStore 정리 후 add를 풂
     * Then: enqueueSyncNow 1회, reschedule 1회, 이름 입력 a 유지
     */
    @Test
    fun test_41_addTaskFinishesSyncAndRescheduleWhenClearedDuringWrite() = runTest {
        /** Given **/
        allowWrites()
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.add(any()) } coAnswers { gate.await() }
        val viewModelStore = ViewModelStore()
        val viewModel = createStoredViewModel(viewModelStore)
        viewModel.updateNewTitle("a")

        /** When **/
        viewModel.addTask()
        waitCoroutine()
        coVerify(exactly = 1) { repository.add(any()) }
        viewModelStore.clear()
        gate.complete(Unit)
        waitCoroutine()

        /** Then **/
        verify(exactly = 1) { scheduler.enqueueSyncNow() }
        coVerify(exactly = 1) { alarms.reschedule() }
        assertEquals("a", viewModel.uiState.value.newTitleInput)
    }

    /**
     * 완료 변경 저장 중에 화면을 닫아도 저장 뒤 동기화 요청과 다시 예약이 끝까지 실행되는지 검증
     *
     * Given: 연결 설정, markDone이 풀어 줄 때까지 붙잡히는 상태
     * When: toggleDone 호출, markDone이 붙잡힌 사이 ViewModelStore 정리 후 markDone을 풂
     * Then: enqueueSyncNow 1회, reschedule 1회
     */
    @Test
    fun test_42_toggleDoneFinishesSyncAndRescheduleWhenClearedDuringWrite() = runTest {
        /** Given **/
        allowWrites()
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.markDone(any(), any(), any()) } coAnswers { gate.await() }
        val viewModelStore = ViewModelStore()
        val viewModel = createStoredViewModel(viewModelStore)

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()
        coVerify(exactly = 1) { repository.markDone("t", true, any()) }
        viewModelStore.clear()
        gate.complete(Unit)
        waitCoroutine()

        /** Then **/
        verify(exactly = 1) { scheduler.enqueueSyncNow() }
        coVerify(exactly = 1) { alarms.reschedule() }
    }

    /**
     * 첫 확인에서 정확한 알람이 거부면 시각 알림을 한 번 다시 예약하고, 거부가 이어지면 더 부르지 않는지 검증
     *
     * Given: 정확한 알람 거부(권한을 거둬 시스템이 정확한 알람을 지우고 앱을 종료한 뒤 새로 연 화면), 확인 전 상태는 허용 표시
     * When: refreshPermissions 호출, 이어서 거부인 채로 한 번 더 호출
     * Then: 첫 호출 뒤 reschedule 1회와 exactAlarmPermissionGranted false, 다시 호출해도 1회
     */
    @Test
    fun test_43_exactAlarmDeniedOnFirstCheckReschedulesOnce() = runTest {
        /** Given **/
        every { permissionStatus.canScheduleExactAlarms() } returns false
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.exactAlarmPermissionGranted)
        coVerify(exactly = 0) { alarms.reschedule() }

        /** When **/
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { alarms.reschedule() }
        assertFalse(viewModel.uiState.value.exactAlarmPermissionGranted)

        /** When **/
        viewModel.refreshPermissions()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { alarms.reschedule() }
    }

    /**
     * 완료로 바꿔 저장에 성공하면 그 Todo의 시각 알림을 저장 뒤에 지우는지 검증
     *
     * Given: 연결 설정, 미완료 항목 t
     * When: toggleDone 호출
     * Then: markDone 뒤 cancelTimeReminder("t") 1회
     */
    @Test
    fun test_44_toggleDoneToDoneCancelsTimeReminder() = runTest {
        /** Given **/
        allowWrites()
        val viewModel = createViewModel()

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()

        /** Then **/
        verify(exactly = 1) { notifier.cancelTimeReminder("t") }
        coVerifyOrder {
            repository.markDone("t", true, any())
            notifier.cancelTimeReminder("t")
        }
    }

    /**
     * 완료를 해제하면 시각 알림을 지우지 않는지 검증
     *
     * Given: 연결 설정, 완료 항목 t
     * When: toggleDone 호출
     * Then: markDone("t", false) 1회, cancelTimeReminder 호출 없음
     */
    @Test
    fun test_45_toggleDoneToUndoneKeepsTimeReminder() = runTest {
        /** Given **/
        allowWrites()
        val viewModel = createViewModel()

        /** When **/
        viewModel.toggleDone(task("t").copy(done = true))
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { repository.markDone("t", false, any()) }
        verify(exactly = 0) { notifier.cancelTimeReminder(any()) }
    }

    /**
     * 완료로 바꾸다 저장에 실패하면 시각 알림을 지우지 않는지 검증
     *
     * Given: 연결 설정, 미완료 항목 t, markDone이 예외를 던짐
     * When: toggleDone 호출
     * Then: change_failed 토스트, cancelTimeReminder 호출 없음
     */
    @Test
    fun test_46_toggleDoneFailureKeepsTimeReminder() = runTest {
        /** Given **/
        allowWrites()
        coEvery { repository.markDone(any(), any(), any()) } throws IllegalStateException()
        val viewModel = createViewModel()

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()

        /** Then **/
        assertEquals(TodoViewModel.EventShowToast(R.string.todo_change_failed), viewModel.events.collectValues(1).single())
        verify(exactly = 0) { notifier.cancelTimeReminder(any()) }
    }

    /**
     * 완료 저장 중에 화면을 닫아도 저장이 끝나면 그 Todo의 시각 알림을 지우는지 검증
     *
     * Given: 연결 설정, 미완료 항목 t, markDone이 풀어 줄 때까지 붙잡히는 상태
     * When: toggleDone 호출, markDone이 붙잡힌 사이 ViewModelStore 정리 후 markDone을 풂
     * Then: cancelTimeReminder("t") 1회
     */
    @Test
    fun test_47_toggleDoneCancelsTimeReminderWhenClearedDuringWrite() = runTest {
        /** Given **/
        allowWrites()
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.markDone(any(), any(), any()) } coAnswers { gate.await() }
        val viewModelStore = ViewModelStore()
        val viewModel = createStoredViewModel(viewModelStore)

        /** When **/
        viewModel.toggleDone(task("t"))
        waitCoroutine()
        verify(exactly = 0) { notifier.cancelTimeReminder(any()) }
        viewModelStore.clear()
        gate.complete(Unit)
        waitCoroutine()

        /** Then **/
        verify(exactly = 1) { notifier.cancelTimeReminder("t") }
    }

    /**
     * 하이픈을 표준 자리에 모두, 일부만 넣거나 대문자로 쓴 data source ID를 저장하는지 검증
     *
     * Given: 토큰 "tok", 표준 형식 ID, 첫 하이픈만 있는 ID, 하이픈 없는 대문자 ID
     * When: 각각 saveConnection 호출
     * Then: 세 ID 그대로 replaceConnection 1회씩, invalid_data_source_id 토스트 없음
     */
    @Test
    fun test_48_saveConnectionAcceptsDataSourceIdForms() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.updateTokenInput("tok")
        val dataSourceIds = listOf(
            "41cffbf3-44f8-8196-a0dd-000b5dbcdc02",
            "41cffbf3-44f88196a0dd000b5dbcdc02",
            "41CFFBF344F88196A0DD000B5DBCDC02",
        )

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) {
            dataSourceIds.forEach { id ->
                viewModel.updateDataSourceIdInput(id)
                viewModel.saveConnection()
                waitCoroutine()
            }
        }

        /** Then **/
        dataSourceIds.forEach { id ->
            coVerify(exactly = 1) { synchronizer.replaceConnection(TodoConnection("tok", id)) }
        }
        assertFalse(receivedEvents.contains(TodoViewModel.EventShowToast(R.string.todo_invalid_data_source_id)))
    }

    /**
     * 하이픈 자리가 틀리거나 자리 수가 모자라거나 16진수가 아닌 data source ID를 저장하지 않고 안내하는지 검증
     *
     * Given: 토큰 "tok", 표준이 아닌 자리의 하이픈, 연달은 하이픈, 맨 앞·맨 뒤 하이픈, 31자리, UUID 약식 "1-2-3-4-5",
     *        부호가 붙은 값, 16진수가 아닌 문자, 33자리
     * When: 각각 saveConnection 호출
     * Then: replaceConnection 호출 없음, 입력 수만큼 invalid_data_source_id 토스트
     */
    @Test
    fun test_49_saveConnectionRejectsMalformedDataSourceId() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.updateTokenInput("tok")
        val dataSourceIds = listOf(
            "41cffbf344-f88196a0dd000b5dbcdc02",
            "41cffbf3--44f8-8196-a0dd-000b5dbcdc02",
            "-41cffbf344f88196a0dd000b5dbcdc02",
            "41cffbf344f88196a0dd000b5dbcdc02-",
            "41cffbf3-44f8-8196-a0dd-000b5dbcdc0",
            "1-2-3-4-5",
            "+1cffbf344f88196a0dd000b5dbcdc02",
            "g1cffbf344f88196a0dd000b5dbcdc02",
            "41cffbf344f88196a0dd000b5dbcdc023",
        )

        /** When **/
        dataSourceIds.forEach { id ->
            viewModel.updateDataSourceIdInput(id)
            viewModel.saveConnection()
        }
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 0) { synchronizer.replaceConnection(any()) }
        assertEquals(
            List(dataSourceIds.size) { TodoViewModel.EventShowToast(R.string.todo_invalid_data_source_id) },
            viewModel.events.collectValues(dataSourceIds.size),
        )
    }

    /**
     * 서머타임 시작으로 없는 시각을 고르면 그 차이만큼 늦은 시각의 기한으로 저장하는지 검증
     *
     * Given: 뉴욕 시간대, 서머타임 시작일 2026-03-08(02:00→03:00)의 없는 시각 02:30, 알림 TIME
     * When: addTask 호출
     * Then: add 인자의 due가 03:30 EDT인 2026-03-08T07:30:00Z
     */
    @Test
    fun test_50_addTaskShiftsSkippedDaylightSavingTimeForward() = runTest {
        /** Given **/
        allowWrites()
        val savedTask = slot<Todo>()
        coEvery { repository.add(capture(savedTask)) } returns Unit
        val defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            val viewModel = createViewModel()
            viewModel.updateNewTitle("a")
            viewModel.updateNewDueDate(LocalDate.of(2026, 3, 8))
            viewModel.updateNewDueTime(LocalTime.of(2, 30))
            viewModel.updateNewReminder(TodoReminder.TIME)

            /** When **/
            viewModel.addTask()
            waitCoroutine()

            /** Then **/
            assertEquals(TodoDue.DateTime(Instant.parse("2026-03-08T07:30:00Z")), savedTask.captured.due)
        } finally {
            TimeZone.setDefault(defaultZone)
        }
    }

    /**
     * 서머타임이 시작되는 날에도 고른 벽시계 시각을 기한으로 저장하는지 검증
     *
     * Given: 뉴욕 시간대, 서머타임 시작일 2026-03-08(02:00→03:00), 시각 03:30
     * When: addTask 호출
     * Then: add 인자의 due가 03:30 EDT인 2026-03-08T07:30:00Z(자정에 210분을 더한 04:30 EDT가 아님)
     */
    @Test
    fun test_51_addTaskOnDaylightSavingStartUsesPickedWallTime() = runTest {
        /** Given **/
        allowWrites()
        val savedTask = slot<Todo>()
        coEvery { repository.add(capture(savedTask)) } returns Unit
        val defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            val viewModel = createViewModel()
            viewModel.updateNewTitle("a")
            viewModel.updateNewDueDate(LocalDate.of(2026, 3, 8))
            viewModel.updateNewDueTime(LocalTime.of(3, 30))

            /** When **/
            viewModel.addTask()
            waitCoroutine()

            /** Then **/
            assertEquals(TodoDue.DateTime(Instant.parse("2026-03-08T07:30:00Z")), savedTask.captured.due)
        } finally {
            TimeZone.setDefault(defaultZone)
        }
    }
}
