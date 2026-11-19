package com.black.feature.todo.ui

import androidx.annotation.StringRes
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.core.viewmodel.EventViewModel
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
import com.black.feature.todo.notification.TodoNotifier
import com.black.feature.todo.notification.TodoPermissionStatus
import com.black.feature.todo.notification.TodoReminderScheduler
import com.black.feature.todo.ui.TodoScreen
import com.black.feature.todo.worker.TodoSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * [TodoScreen]의 상태·이벤트 관리 ViewModel
 *
 * [alarms]는 Hilt 모듈 없이 `@Inject` 생성자로 주입하도록 구현 클래스로 받고, 다시 예약 창구로만 씀.
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val settings: TodoSettingsRepository,
    private val repository: TodoRepository,
    private val synchronizer: SyncTodoUseCase,
    private val scheduler: TodoSyncScheduler,
    private val permissionStatus: TodoPermissionStatus,
    private val alarms: TodoReminderScheduler,
    private val notifier: TodoNotifier,
) : EventViewModel() {

    /** Todo 화면 일회성 이벤트 */
    sealed class TodoEvent : ViewModelEvent

    /** 노션 페이지 열기 */
    data class EventOpenUrl(val url: String) : TodoEvent()

    /** 문자열 리소스 토스트 표시 */
    data class EventShowToast(@StringRes val messageResId: Int) : TodoEvent()

    /** 앱 알림 설정 화면 이동 */
    data object EventOpenNotificationSettings : TodoEvent()

    /** 정확한 알람 설정 화면 이동 */
    data object EventOpenExactAlarmSettings : TodoEvent()

    private val mutableUiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = mutableUiState.asStateFlow()

    /**
     * 오늘 날짜 계산과 수정 시각에 쓰는 현재 시각 공급자
     *
     * Hilt 생성자를 하나로 두기 위해 생성자 인자가 아니라 테스트에서만 바꾸는 속성으로 둠.
     */
    internal var now: () -> Instant = Instant::now

    /** 기기 시간대 기준 오늘 날짜 */
    private val today = MutableStateFlow(currentDate())

    /** 진행 중인 새로고침이며, 연결을 바꾸면 취소하고 새 연결로 다시 시작 */
    private var refreshJob: Job? = null

    /**
     * 진행 중인 Todo 저장
     *
     * 저장이 끝나 입력을 비우기 전에 추가 버튼을 다시 누르면 같은 Todo가 두 번 저장되므로, 진행 중이면 다음 추가를 무시함.
     */
    private var addJob: Job? = null

    /**
     * 이 ViewModel에서 정확한 알람 허용 여부를 확인한 적이 있는지 여부
     *
     * 화면 상태의 [TodoUiState.exactAlarmPermissionGranted]는 확인 전에도 허용 필요 표시가 깜박이지 않도록 true라,
     * [refreshPermissions]가 첫 확인을 그 값과 구분하려고 따로 둠.
     */
    private var exactAlarmChecked = false

    private data class TaskSnapshot(
        val tasks: List<Todo>,
        val connection: TodoConnection,
        val lastSync: TodoSyncState?,
        val today: LocalDate,
    )

    private fun currentDate(): LocalDate = now().atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * 오늘 날짜 다시 계산
     *
     * 자정을 넘겨도 구획이 갱신되도록 화면이 다시 보일 때 호출함.
     */
    fun refreshToday() {
        today.value = currentDate()
    }

    /**
     * Todo 알림·정확한 알람 허용 여부 다시 확인
     *
     * 설정 화면에서 돌아왔을 때 반영되도록 화면이 다시 보일 때 호출함.
     * 정확한 알람이 거부에서 허용으로 바뀌면 부정확한 알람으로 걸어 둔 시각 알림이 정확한 알람으로 바뀌도록 다시 예약함.
     * 정확한 알람을 거두면 시스템이 걸려 있던 정확한 알람을 지우고 앱을 종료해 그 뒤 화면에서는 허용에서 거부로 바뀌는 것을 볼 수 없으므로,
     * 첫 확인이 거부면 한 번 다시 예약해 부정확한 알람으로 다시 걺.
     * 권한 변경 수신기도 같은 때 다시 예약할 수 있으나 저장본 기준이라 두 번 돌아도 결과가 같음.
     */
    fun refreshPermissions() {
        val todoNotificationsEnabled = permissionStatus.canShowTodoNotifications()
        val exactAlarmGranted = permissionStatus.canScheduleExactAlarms()
        // 메인 스레드에서만 불리므로 이전 값 읽기와 갱신 사이에 다른 갱신이 끼지 않음
        val needsReschedule = if (exactAlarmChecked) {
            exactAlarmGranted && !mutableUiState.value.exactAlarmPermissionGranted
        } else {
            !exactAlarmGranted
        }
        exactAlarmChecked = true
        mutableUiState.update {
            it.copy(todoNotificationsEnabled = todoNotificationsEnabled, exactAlarmPermissionGranted = exactAlarmGranted)
        }
        if (needsReschedule) {
            launchAlarmReschedule()
        }
    }

    /**
     * 알림 허용이 필요할 때 앱 알림 설정 화면으로 이동
     *
     * 앱 알림이 허용돼도 Todo 채널이 꺼져 있을 수 있어, 채널 목록이 보이는 앱 알림 설정 화면을 엶.
     */
    fun openNotificationSettings() {
        sendEvent(EventOpenNotificationSettings)
    }

    /** 정확한 알람 허용이 필요할 때 정확한 알람 설정 화면으로 이동 */
    fun openExactAlarmSettings() {
        sendEvent(EventOpenExactAlarmSettings)
    }

    init {
        // 저장본·연결·마지막 동기화가 바뀔 때마다 구획과 표시용 값 다시 계산
        launch {
            combine(repository.tasks, settings.connection, settings.lastSync, today) { tasks, connection, lastSync, todayDate ->
                TaskSnapshot(tasks, connection, lastSync, todayDate)
            }.collect { (tasks, connection, lastSync, todayDate) ->
                val zone = ZoneId.systemDefault()
                mutableUiState.update {
                    it.copy(
                        taskSections = TodoSections.group(tasks, todayDate, zone),
                        configured = connection.isConfigured,
                        connectionLoaded = true,
                        lastSync = lastSync,
                    )
                }
            }
        }
        // 저장된 연결을 입력칸에 채우고, 연결돼 있으면 주기 작업 예약(첫 동기화는 화면 재개 때 refresh가 맡음)
        launch {
            val connection = settings.getConnection()
            mutableUiState.update {
                it.copy(
                    tokenInput = connection.token,
                    dataSourceIdInput = connection.dataSourceId,
                    settingsExpanded = !connection.isConfigured,
                )
            }
            if (connection.isConfigured) {
                scheduler.schedulePeriodic()
            }
        }
    }

    fun toggleDoneSection() {
        mutableUiState.update { it.copy(doneExpanded = !it.doneExpanded) }
    }

    fun toggleSettings() {
        mutableUiState.update { it.copy(settingsExpanded = !it.settingsExpanded) }
    }

    fun updateTokenInput(text: String) {
        mutableUiState.update { it.copy(tokenInput = text) }
    }

    fun updateDataSourceIdInput(text: String) {
        mutableUiState.update { it.copy(dataSourceIdInput = text) }
    }

    /**
     * 입력한 연결 정보 저장
     *
     * 앞뒤 공백을 제거한 뒤 둘 다 비면 연결 해제, 하나만 비거나 토큰·data source ID 형식이 틀리면 저장하지 않고 토스트로 안내함.
     * 저장은 [SyncTodoUseCase.replaceConnection]으로 해 data source ID가 바뀌면 저장본도 비움.
     * 저장되면 주기 작업을 예약하고, 옛 연결로 진행 중인 새로고침을 취소한 뒤 새 연결로 바로 동기화함. 토큰은 로그에 남기지 않음.
     */
    fun saveConnection() {
        val token = mutableUiState.value.tokenInput.trim()
        val dataSourceId = mutableUiState.value.dataSourceIdInput.trim()
        when {
            token.isEmpty() && dataSourceId.isEmpty() -> disconnect()
            token.isEmpty() || dataSourceId.isEmpty() -> sendEvent(EventShowToast(R.string.todo_connection_incomplete))
            !token.all { it in TOKEN_CHAR_RANGE } -> sendEvent(EventShowToast(R.string.todo_invalid_token))
            !isValidDataSourceId(dataSourceId) -> sendEvent(EventShowToast(R.string.todo_invalid_data_source_id))
            else -> launch {
                // 옛 연결로 진행 중인 새로고침이 저장본을 다시 채우지 않도록 교체 전에 끝냄
                refreshJob?.cancelAndJoin()
                val result = synchronizer.replaceConnection(TodoConnection(token, dataSourceId))
                if (result.isSuccess || result.exceptionOrNull() is TodoStoreClearException) {
                    scheduler.schedulePeriodic()
                    mutableUiState.update { it.copy(settingsExpanded = false) }
                    refresh()
                }
                notifyConnectionFailure(result)
            }
        }
    }

    /**
     * 연결 교체 실패 안내
     *
     * 저장본 비우기 실패는 연결 저장 뒤의 실패라 clear_failed로, 그 밖의 실패는 save_failed로 알림.
     */
    private fun notifyConnectionFailure(result: Result<Unit>) {
        val failure = result.exceptionOrNull() ?: return
        val messageResId = if (failure is TodoStoreClearException) {
            R.string.todo_clear_failed
        } else {
            R.string.todo_save_failed
        }
        sendEvent(EventShowToast(messageResId))
    }

    /** 빈 연결로 바꿔 저장본을 비우고 주기 작업 취소 */
    private fun disconnect() {
        launch {
            // 옛 연결로 진행 중인 새로고침이 저장본을 다시 채우지 않도록 교체 전에 끝냄
            refreshJob?.cancelAndJoin()
            val result = synchronizer.replaceConnection(TodoConnection("", ""))
            if (result.isSuccess || result.exceptionOrNull() is TodoStoreClearException) {
                scheduler.cancelPeriodic()
                mutableUiState.update { it.copy(settingsExpanded = true) }
                sendEvent(EventShowToast(R.string.todo_disconnected))
            }
            notifyConnectionFailure(result)
        }
    }

    /**
     * 노션과 동기화
     *
     * 이미 새로고침 중이거나 연결 설정이 없으면 아무것도 하지 않음.
     * 진행 표시는 설정을 확인한 뒤에 켜서 미설정 화면이 재개될 때 표시가 깜빡이지 않음.
     * [EventViewModel.launch]가 예외를 잡지 않으므로 [runSuspendCatching]이 실패로 담은 예외는 여기서 예외 클래스 이름만 로그로 남기고 토스트로 안내함.
     * 새로고침 코루틴이 취소되면 실패로 담기지 않아 토스트 없이 진행 표시만 끔.
     */
    fun refresh() {
        // 메인 스레드에서만 불리므로 확인과 시작 사이에 다른 호출이 끼지 않아 동시 호출에서도 한 번만 실행
        if (refreshJob?.isActive == true) {
            return
        }
        refreshJob = launch {
            if (!settings.getConnection().isConfigured) {
                return@launch
            }
            // 켜기와 try 사이에 중단 지점이 없어, 시작 전에 취소되면 켜지지 않고 켠 뒤에는 finally가 끔
            mutableUiState.update { it.copy(isRefreshing = true) }
            try {
                runSuspendCatching { synchronizer.sync() }.onFailure {
                    Log.w("[TodoViewModel] 동기화 실패: ${it.javaClass.simpleName}")
                    sendEvent(EventShowToast(R.string.todo_sync_failed))
                }
            } finally {
                mutableUiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun openTask(task: Todo) {
        if (task.url.isEmpty()) {
            return
        }
        sendEvent(EventOpenUrl(task.url))
    }

    fun toggleAddSection() {
        mutableUiState.update { it.copy(addExpanded = !it.addExpanded) }
    }

    fun updateNewTitle(text: String) {
        mutableUiState.update { it.copy(newTitleInput = text) }
    }

    /** 기한 날짜 바꾸기이며, null이면 기한 시각도 함께 지움 */
    fun updateNewDueDate(date: LocalDate?) {
        mutableUiState.update { it.copy(newDueDate = date, newDueTime = if (date == null) null else it.newDueTime) }
    }

    /** 기한 시각 바꾸기이며, 날짜가 없으면 무시 */
    fun updateNewDueTime(time: LocalTime?) {
        mutableUiState.update { if (it.newDueDate == null) it else it.copy(newDueTime = time) }
    }

    fun updateNewReminder(reminder: TodoReminder) {
        mutableUiState.update { it.copy(newReminder = reminder) }
    }

    /**
     * 입력한 Todo를 반영 대기 항목으로 저장하고 바로 동기화 요청과 시각 알림 다시 예약
     *
     * 미설정이거나 앞선 저장이 진행 중이면 무시하며, 이름이 비거나 시각 알림인데 기한 시각이 없으면 저장하지 않고 토스트로 안내함.
     * 저장에 실패하면 입력을 지우지 않음.
     */
    fun addTask() {
        // 메인 스레드에서만 불리므로 확인과 시작 사이에 다른 호출이 끼지 않아 빠르게 두 번 눌러도 한 번만 저장
        if (addJob?.isActive == true) {
            return
        }
        val state = mutableUiState.value
        if (!state.configured) {
            return
        }
        val title = state.newTitleInput.trim()
        // 입력 검증
        if (title.isEmpty()) {
            sendEvent(EventShowToast(R.string.todo_title_required))
            return
        }
        if (state.newReminder == TodoReminder.TIME && state.newDueTime == null) {
            sendEvent(EventShowToast(R.string.todo_time_reminder_needs_time))
            return
        }
        val due = buildNewDue(state)
        addJob = launch {
            val task = Todo(
                pageId = Todo.newLocalId(),
                title = title,
                done = false,
                due = due,
                reminder = state.newReminder,
                content = "",
                lastEditedTime = now(),
                url = "",
                pendingSync = true,
            )
            val added = writeThenRequestSyncAndReschedule("Todo 추가 실패") { repository.add(task) }
            // 화면을 닫았으면 입력 초기화·토스트 생략
            ensureActive()
            if (!added) {
                sendEvent(EventShowToast(R.string.todo_add_failed))
                return@launch
            }
            // 입력 초기화
            mutableUiState.update {
                it.copy(
                    addExpanded = false,
                    newTitleInput = "",
                    newDueDate = null,
                    newDueTime = null,
                    newReminder = TodoReminder.NONE,
                )
            }
        }
    }

    /**
     * [write]로 저장본을 바꾼 뒤 바로 동기화 요청과 시각 알림 다시 예약까지 화면을 닫아도 끝까지 실행하고 쓰기 성공 여부 반환
     *
     * 쓰기 중에 화면을 닫으면 쓰기는 커밋되는데 취소 예외로 뒤 단계를 건너뛰어,
     * 반영 대기 항목이 다음 주기 동기화까지 노션에 가지 않고 시각 알림도 걸리지 않으므로 세 단계를 [NonCancellable]로 묶음.
     * 쓰기 실패는 [failureMessage]와 예외 클래스 이름만 로그로 남기고 동기화 요청·다시 예약 없이 false 반환.
     * [NonCancellable] 안에서는 이 코루틴이 취소되지 않아, 쓰기에서 난 취소 예외도 [runSuspendCatching]이 실패로 담아 false 반환.
     */
    private suspend fun writeThenRequestSyncAndReschedule(failureMessage: String, write: suspend () -> Unit): Boolean =
        withContext(NonCancellable) {
            runSuspendCatching { write() }.getOrElse {
                Log.w("[TodoViewModel] $failureMessage: ${it.javaClass.simpleName}")
                return@withContext false
            }
            // 동기화 요청
            scheduler.enqueueSyncNow()
            // 시각 알림 다시 예약(추가 중복 방지 시간이 늘지 않도록 addJob이 기다리지 않는 별도 코루틴으로)
            launchAlarmReschedule()
            true
        }

    /**
     * 시각 알림 다시 예약을 별도 코루틴으로 시작
     *
     * 다시 예약은 저장본 전체를 읽고 프로세스 전역 잠금을 기다릴 수 있어, [addJob] 안에서 기다리면 다음 추가를 무시하는 시간이 그만큼 늘어남.
     * 화면을 닫아 ViewModel 범위가 취소된 뒤에 불려도 본문이 돌도록 [CoroutineStart.UNDISPATCHED]로 시작함(기본 시작은 취소된 범위에서 본문을 실행하지 않음).
     */
    private fun launchAlarmReschedule() {
        launch(start = CoroutineStart.UNDISPATCHED) { rescheduleAlarms() }
    }

    /**
     * 화면을 닫아도 끝까지 도는 시각 알림 다시 예약
     *
     * 다른 다시 예약이 예약기의 전역 잠금을 잡고 있을 때 화면을 닫으면 ViewModel 범위 취소로 잠금 대기 중에 취소돼,
     * 방금 바꾼 Todo(오프라인에서 추가한 시각 알림 Todo 등)의 알람이 다음 동기화 성공까지 걸리지 않음.
     * 다시 예약은 짧고 예외를 던지지 않으므로 [NonCancellable]로 끝까지 돌림.
     */
    private suspend fun rescheduleAlarms() {
        withContext(NonCancellable) { alarms.reschedule() }
    }

    /**
     * 입력한 날짜·시각으로 만든 기한이며, 날짜가 없으면 null
     *
     * 시각은 기기 시간대 기준 벽시계 시각이며, 서머타임 시작으로 없는 시각은 [ZonedDateTime]이 그 차이만큼 늦은 시각으로 옮김.
     */
    private fun buildNewDue(state: TodoUiState): TodoDue? {
        val date = state.newDueDate ?: return null
        val time = state.newDueTime ?: return TodoDue.Date(date)
        return TodoDue.DateTime(ZonedDateTime.of(date, time, ZoneId.systemDefault()).toInstant())
    }

    /**
     * 완료 여부를 뒤집어 반영 대기로 저장하고 바로 동기화 요청과 시각 알림 다시 예약
     *
     * 미설정이면 무시함(연결 전에 바꾼 항목은 연결하며 저장본을 비울 때 사라지기 때문).
     * 완료로 바꿔 저장했으면 이미 뜬 그 Todo의 시각 알림을 지우고, 완료 해제와 저장 실패는 알림을 그대로 둠.
     * 저장 중에 화면을 닫아도 저장은 커밋되므로 알림 제거는 화면 닫힘 확인보다 앞에 둠.
     */
    fun toggleDone(task: Todo) {
        if (!mutableUiState.value.configured) {
            return
        }
        launch {
            val newDone = !task.done
            val changed = writeThenRequestSyncAndReschedule("완료 변경 실패") {
                repository.markDone(task.pageId, newDone, now())
            }
            // 완료로 바꿨으면 그 Todo의 시각 알림 제거
            if (changed && newDone) {
                notifier.cancelTimeReminder(task.pageId)
            }
            // 화면을 닫았으면 토스트 생략
            ensureActive()
            if (!changed) {
                sendEvent(EventShowToast(R.string.todo_change_failed))
            }
        }
    }

    private companion object {
        /** 토큰이 HTTP 헤더에 그대로 실리므로 공백·제어 문자·비ASCII 없이 출력 가능한 ASCII(0x21~0x7E)만 허용 */
        val TOKEN_CHAR_RANGE = '!'..'~'

        /** 하이픈을 뺀 data source ID 길이 */
        const val DATA_SOURCE_ID_LENGTH = 32

        /** 하이픈을 뺀 ID에서 UUID 표준 형식(8·4·4·4·12)의 하이픈이 오는 자리 */
        val DATA_SOURCE_ID_HYPHEN_POSITIONS = setOf(8, 12, 16, 20)

        /**
         * 하이픈 유무와 무관한 32자리 16진수 ID인지 여부
         *
         * 하이픈은 표준 자리에만 하나씩 올 수 있고, 일부 자리에만 있어도 됨.
         * [UUID.fromString]은 자리 수가 모자란 조각과 부호(`+`)도 받아들이므로 해석 결과를 다시 문자열로 바꿔 입력과 비교함.
         */
        fun isValidDataSourceId(dataSourceId: String): Boolean {
            // 하이픈으로 나눈 조각(빈 조각이면 하이픈이 맨 앞·맨 뒤에 있거나 연달아 있음)
            val segments = dataSourceId.split('-')
            if (segments.any { it.isEmpty() }) {
                return false
            }
            // 하이픈을 뺀 길이
            val compact = segments.joinToString("")
            if (compact.length != DATA_SOURCE_ID_LENGTH) {
                return false
            }
            // 하이픈 자리(마지막 조각을 뺀 각 조각 끝까지의 길이)
            val hyphenPositions = segments.map { it.length }.runningReduce(Int::plus).dropLast(1)
            if (!DATA_SOURCE_ID_HYPHEN_POSITIONS.containsAll(hyphenPositions)) {
                return false
            }
            // 표준 자리에 하이픈을 넣어 UUID로 해석한 결과와 비교
            val canonical = buildString {
                compact.forEachIndexed { index, char ->
                    if (index in DATA_SOURCE_ID_HYPHEN_POSITIONS) {
                        append('-')
                    }
                    append(char)
                }
            }
            val uuid = try {
                UUID.fromString(canonical)
            } catch (exception: IllegalArgumentException) {
                return false
            }
            return uuid.toString().equals(canonical, ignoreCase = true)
        }
    }
}
