package com.black.feature.usagelog.data.usecase

import android.content.Context
import com.black.core.util.Log
import com.black.core.util.SerialEventQueue
import com.black.core.wifi.WifiConnectionMonitor
import com.black.core.wifi.WifiConnectionSession
import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.UsageLogSettingsRepository
import com.black.feature.usagelog.model.SendReason
import com.black.feature.usagelog.worker.UsageLogScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Wi-Fi 연결·해제를 기록하고, 집 도착·출발이면 [HomeTransitionListener]에 알린 뒤 즉시 전송을 예약하는 기록기
 *
 * [WifiConnectionMonitor]가 감시 세션마다 [openSession]으로 연 세션에 연결·해제 보고를 넘김.
 * 콜백 스레드에서 받은 보고는 대기열에 넣기만 하고, 저장소 상태(`lastWifiState`) 읽기·계산·저장은 단일 소비 코루틴이 도착 순서대로 처리함.
 * 기록과 알림 판정은 [WifiTransitionMachine]이 하며 세션마다 새로 만들어, 이전 세션의 코루틴이 새 상태를 덮어쓰지 못함.
 * 새 세션의 소비 코루틴은 이전 세션의 소비 코루틴이 끝난 뒤 처리를 시작해, 취소 중인 이전 세션의 저장과 겹치지 않음.
 * 그래서 다시 감시를 시작해도 같은 기록기로 세션을 열어야 함.
 * SSID는 로그에 남기지 않음(예외도 클래스 이름만).
 *
 * 집 전환마다 [listener]를 먼저 부르고, 이어서 [scheduleSend]로 집 도착은 [SendReason.HOME_ARRIVE], 출발은 [SendReason.HOME_LEAVE] 전송을 예약함.
 * 둘의 예외를 따로 잡아, 한쪽이 실패해도 다른 쪽과 다음 전환, 감시가 계속됨.
 *
 * 시작과 중지 시점은 앱 공용 알림 리스너(앱의 `AppNotificationListenerService`)가 정함.
 *
 * @param scheduleSend 집 도착·출발의 즉시 전송 예약([UsageLogScheduler.enqueueSend])
 */
class WifiRecorder(
    private val settings: UsageLogSettingsRepository,
    private val repository: UsageLogRepository,
    private val scope: CoroutineScope,
    private val listener: HomeTransitionListener,
    private val scheduleSend: (SendReason) -> Unit,
) {

    /** Hilt 주입 없이 저장소를 직접 만드는 서비스용 생성자 */
    constructor(context: Context, scope: CoroutineScope, listener: HomeTransitionListener) : this(
        settings = UsageLogSettingsRepository(context.applicationContext),
        repository = UsageLogRepository(context.applicationContext),
        scope = scope,
        listener = listener,
        scheduleSend = UsageLogScheduler(context.applicationContext)::enqueueSend,
    )

    /**
     * 마지막으로 시작한 세션의 소비 코루틴
     *
     * 다음 세션의 소비 코루틴이 이 코루틴이 끝나기를 기다림.
     */
    private var consumerJob: Job? = null

    /**
     * 감시 세션 하나에 쓸 기록 세션
     *
     * [WifiConnectionMonitor.start]에 넘기며 콜백 등록 전에 불림.
     * 수집 켜짐 이벤트를 먼저 넣은 대기열과 판정기만 만들고, 소비 코루틴은 등록에 성공한 뒤([WifiConnectionSession.onRegistered]) 띄움.
     *
     * @param connectedHandles 콜백 등록 전에 이미 연결돼 있던 Wi-Fi 네트워크 핸들
     */
    fun openSession(connectedHandles: Set<Long>): WifiConnectionSession {
        val queue = SerialEventQueue<WifiEvent>()
        // 콜백 보고보다 먼저 처리해, 서비스가 멈춘 사이의 변화를 저장된 옛 상태 기준의 추정 기록으로 먼저 맞춤
        queue.offer(WifiEvent.CollectionEnabled(System.currentTimeMillis()))
        val machine = WifiTransitionMachine(connectedHandles)
        return RecordingSession(queue, machine, hasConnectedHandles = connectedHandles.isNotEmpty())
    }

    private suspend fun handle(machine: WifiTransitionMachine, event: WifiEvent) {
        val enabled = settings.isCollectionEnabled()
        val collectionContext = if (enabled) {
            WifiCollectionContext(settings.getLastWifiState(), settings.getHomeSsids())
        } else {
            null
        }
        val decision = when (event) {
            is WifiEvent.Reported -> machine.onReported(event.handle, event.ssid, event.time, collectionContext)
            is WifiEvent.Lost -> machine.onLost(event.handle, event.time, collectionContext)
            is WifiEvent.CollectionEnabled -> machine.onCollectionEnabled(event.time, collectionContext)
            is WifiEvent.InitialTimeout -> machine.onInitialTimeout(event.time, collectionContext)
            is WifiEvent.HomesChanged -> machine.onHomesChanged(event.time, collectionContext)
        }
        apply(decision)
    }

    /** 기록을 저장한 뒤 마지막 상태를 갱신하고, 집 도착·출발은 리스너에 알린 뒤 즉시 전송 예약 */
    private suspend fun apply(decision: WifiDecision) {
        if (decision.records.isNotEmpty()) {
            repository.add(decision.records)
        }
        decision.newPersisted?.let { state ->
            settings.setLastWifiState(state).exceptionOrNull()?.let {
                Log.w("[WifiRecorder] Wi-Fi 상태 저장 실패: ${it::class.java.simpleName}")
            }
        }
        decision.homeTransitions.forEach { transition ->
            try {
                listener.onHomeTransition(transition)
            } catch (failure: Exception) {
                // 리스너 실패가 전송 예약과 다음 알림, 감시를 막지 않도록 클래스 이름만 기록
                Log.w("[WifiRecorder] 집 전환 알림 실패: ${failure::class.java.simpleName}")
            }
            try {
                scheduleSend(if (transition.arrived) SendReason.HOME_ARRIVE else SendReason.HOME_LEAVE)
            } catch (failure: Exception) {
                // 전송 예약 실패가 다음 알림과 감시를 막지 않도록 클래스 이름만 기록
                Log.w("[WifiRecorder] 집 전환 전송 예약 실패: ${failure::class.java.simpleName}")
            }
        }
    }

    /** 읽기 예외로 흐름이 끝나 이후 변화를 놓치지 않도록 잠시 뒤 다시 구독 */
    private fun <T> Flow<T>.retryAfterDelay(name: String): Flow<T> = retry { cause ->
        Log.w("[WifiRecorder] $name 읽기 실패: ${cause::class.java.simpleName}")
        delay(SETTINGS_RETRY_DELAY)
        true
    }

    /**
     * 감시 세션 하나의 대기열, 판정기, 코루틴
     *
     * [openSession]마다 새로 만들어, 해제 뒤 늦게 도착한 이전 세션의 보고는 닫힌 이전 대기열로 가고 새 세션에 섞이지 않음.
     * [onRegistered]와 [onClosed]는 감시를 시작·중지하는 스레드에서, [onConnected]와 [onLost]는 콜백 스레드에서 불림.
     */
    private inner class RecordingSession(
        private val queue: SerialEventQueue<WifiEvent>,
        private val machine: WifiTransitionMachine,
        private val hasConnectedHandles: Boolean,
    ) : WifiConnectionSession {

        private var jobs: List<Job> = emptyList()

        override fun onRegistered() {
            val previousConsumer = consumerJob
            val consumer = scope.launch {
                // 이전 세션이 저장을 마친 뒤 저장된 상태를 읽도록 기다림
                previousConsumer?.join()
                // 단일 소비자라 연결·해제와 수집 켜짐이 도착 순서대로 처리됨
                // consume이 이벤트마다 취소를 확인해 중지된 세션의 남은 이벤트는 저장소를 건드리지 않음
                queue.consume(
                    // 한 건의 실패로 감시 전체가 멈추지 않도록 실패는 로그만 남기고 다음 이벤트로 넘어감
                    onFailure = { Log.w("[WifiRecorder] Wi-Fi 이벤트 처리 실패: ${it::class.java.simpleName}") },
                ) { event -> handle(machine, event) }
            }
            consumerJob = consumer

            val startedJobs = mutableListOf(consumer)
            startedJobs += scope.launch {
                settings.collectionEnabled
                    .retryAfterDelay("수집 설정")
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) {
                            queue.offer(WifiEvent.CollectionEnabled(System.currentTimeMillis()))
                        }
                    }
            }
            startedJobs += scope.launch {
                // 첫 값도 넣지만, 판정기가 마지막 기록에 쓴 집 SSID와 비교하므로 바뀌지 않았으면 기록하지 않음
                settings.homeSsids
                    .retryAfterDelay("집 SSID")
                    .distinctUntilChanged()
                    .collect { queue.offer(WifiEvent.HomesChanged(System.currentTimeMillis())) }
            }
            if (hasConnectedHandles) {
                startedJobs += scope.launch {
                    // 초기 네트워크의 첫 보고가 끝내 오지 않아도 수집 켜짐 처리가 막히지 않도록 함
                    delay(INITIAL_REPORT_TIMEOUT)
                    queue.offer(WifiEvent.InitialTimeout(System.currentTimeMillis()))
                }
            }
            jobs = startedJobs
        }

        override fun onConnected(handle: Long, ssid: String?, time: Long) {
            queue.offer(WifiEvent.Reported(handle, ssid, time))
        }

        override fun onLost(handle: Long, time: Long) {
            queue.offer(WifiEvent.Lost(handle, time))
        }

        /**
         * 대기열을 닫고 이 세션의 코루틴 취소
         *
         * 콜백 등록에 실패해 [onRegistered] 없이 불리면 띄운 코루틴이 없어 대기열만 닫음.
         */
        override fun onClosed() {
            queue.close()
            jobs.forEach { it.cancel() }
            jobs = emptyList()
        }
    }

    /**
     * 소비 코루틴이 처리하는 Wi-Fi 이벤트
     *
     * 시각은 발생 지점에서 찍음.
     */
    private sealed interface WifiEvent {
        class Reported(val handle: Long, val ssid: String?, val time: Long) : WifiEvent
        class Lost(val handle: Long, val time: Long) : WifiEvent
        class CollectionEnabled(val time: Long) : WifiEvent
        class InitialTimeout(val time: Long) : WifiEvent
        class HomesChanged(val time: Long) : WifiEvent
    }

    companion object {
        private val SETTINGS_RETRY_DELAY = 5.seconds
        private val INITIAL_REPORT_TIMEOUT = 5.seconds
    }
}
