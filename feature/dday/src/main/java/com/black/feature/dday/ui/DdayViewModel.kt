package com.black.feature.dday.ui

import android.content.Context
import androidx.annotation.StringRes
import com.black.core.system.CalendarSource
import com.black.core.system.SystemCalendarSource
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import com.black.feature.dday.R
import com.black.feature.dday.data.DdayRepository
import com.black.feature.dday.data.usecase.LoadDdayItemsUseCase
import com.black.feature.dday.model.DdayStyleField
import com.black.feature.dday.widget.DdayWidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [DdayScreen]의 상태·이벤트 관리 ViewModel
 *
 * 불러오기와 저장은 [mutex]로 차례대로 처리해, 저장 중에 불러오기가 옛 값을 읽어 화면을 되돌리지 않음.
 * 위젯 갱신은 [WIDGET_REFRESH_DEBOUNCE_MILLIS] 디바운스로 모아 연타 조절 중에 매번 다시 그리지 않음.
 * [calendarSource]는 Hilt 모듈 없이 `@Inject` 보조 생성자에서 [SystemCalendarSource]로 만듦.
 */
@HiltViewModel
class DdayViewModel(
    private val repository: DdayRepository,
    private val loadItems: LoadDdayItemsUseCase,
    private val calendarSource: CalendarSource,
    private val refresher: DdayWidgetRefresher,
) : EventViewModel() {

    @Inject
    constructor(
        repository: DdayRepository,
        loadItems: LoadDdayItemsUseCase,
        refresher: DdayWidgetRefresher,
        @ApplicationContext context: Context,
    ) : this(repository, loadItems, SystemCalendarSource(context), refresher)

    /** D-Day 화면 일회성 이벤트 */
    sealed class DdayEvent : ViewModelEvent

    /** 문자열 리소스 토스트 표시 */
    data class EventShowToast(@StringRes val messageResId: Int) : DdayEvent()

    private val mutableUiState = MutableStateFlow(DdayUiState())
    val uiState: StateFlow<DdayUiState> = mutableUiState.asStateFlow()

    /** 캘린더 ContentResolver 조회를 돌리는 디스패처, 테스트에서만 바꿈 */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** 불러오기와 저장을 차례대로 처리하는 잠금 */
    private val mutex = Mutex()

    /** 대기 중인 위젯 갱신, 새 요청이 오면 취소하고 다시 기다림 */
    private var widgetRefreshJob: Job? = null

    /** 디바운스 대기 중이라 아직 실행하지 않은 위젯 갱신이 있는지 */
    private var widgetRefreshPending = false

    /** 권한·캘린더 목록·스타일·미리보기를 다시 읽음 */
    fun refresh() {
        launch {
            mutex.withLock {
                val permissionGranted = withContext(ioDispatcher) { calendarSource.canRead() }
                val excludedIds = repository.getExcludedCalendarIds()
                val calendars = if (permissionGranted) {
                    withContext(ioDispatcher) { calendarSource.queryCalendars() }.orEmpty()
                } else {
                    emptyList()
                }
                val style = repository.getStyle()
                val preview = loadItems.load()
                val previous = mutableUiState.value
                mutableUiState.update {
                    it.copy(
                        loaded = true,
                        permissionGranted = permissionGranted,
                        calendars = calendars.map { calendar ->
                            DdayCalendarOption(calendar, calendar.id !in excludedIds)
                        },
                        style = style,
                        preview = preview,
                    )
                }
                // 위젯의 권한 안내를 눌러 설정에서 권한을 바꾸고 돌아온 경우, 위젯이 자정·캘린더 변경까지 옛 상태로 남지 않도록 갱신
                if (!previous.loaded || previous.permissionGranted != permissionGranted) {
                    scheduleWidgetRefresh()
                }
            }
        }
    }

    /** [calendarId] 캘린더의 일정을 위젯에 올리거나 뺌 */
    fun setCalendarIncluded(calendarId: Long, included: Boolean) {
        launch {
            mutex.withLock {
                val result = repository.setCalendarExcluded(calendarId, !included)
                if (result.isFailure) {
                    sendEvent(EventShowToast(R.string.dday_save_failed))
                    return@withLock
                }
                val preview = loadItems.load()
                mutableUiState.update { state ->
                    state.copy(
                        calendars = state.calendars.map { option ->
                            if (option.calendar.id == calendarId) option.copy(included = included) else option
                        },
                        preview = preview,
                    )
                }
                scheduleWidgetRefresh()
            }
        }
    }

    /** [field] 값을 [delta]만큼 바꿔 저장, 범위 끝이면 아무것도 하지 않음 */
    fun adjustStyle(field: DdayStyleField, delta: Int) {
        launch {
            mutex.withLock {
                val previous = mutableUiState.value.style
                val next = previous.with(field, previous[field] + delta)
                if (next == previous) {
                    return@withLock
                }
                mutableUiState.update { it.copy(style = next) }
                if (repository.saveStyle(next).isSuccess) {
                    scheduleWidgetRefresh()
                } else {
                    mutableUiState.update { it.copy(style = previous) }
                    sendEvent(EventShowToast(R.string.dday_save_failed))
                }
            }
        }
    }

    private fun scheduleWidgetRefresh() {
        widgetRefreshJob?.cancel()
        widgetRefreshPending = true
        widgetRefreshJob = launch {
            delay(WIDGET_REFRESH_DEBOUNCE_MILLIS)
            refreshWidget()
            // 갱신 도중 화면을 나가 취소돼도 onCleared가 다시 갱신하도록 완료 뒤 해제
            widgetRefreshPending = false
        }
    }

    private suspend fun refreshWidget() {
        runSuspendCatching { refresher.refresh() }
            .onFailure { Log.w("[DdayViewModel] 위젯 갱신 실패: ${it::class.java.simpleName}") }
    }

    /**
     * 디바운스 대기 중에 화면을 나가 ViewModel이 지워지면 대기 중인 위젯 갱신을 앱 수명 범위에서 바로 실행
     *
     * 이 경우를 두지 않으면 설정은 저장됐는데 위젯은 옛 스타일로 남음.
     */
    override fun onCleared() {
        widgetRefreshJob?.cancel()
        if (widgetRefreshPending) {
            widgetRefreshPending = false
            CoroutineScope(SupervisorJob() + ioDispatcher).launch { refreshWidget() }
        }
        super.onCleared()
    }

    companion object {
        /** 위젯 갱신 디바운스 시간 */
        const val WIDGET_REFRESH_DEBOUNCE_MILLIS = 300L
    }
}
