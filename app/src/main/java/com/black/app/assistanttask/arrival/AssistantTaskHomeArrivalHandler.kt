package com.black.app.assistanttask.arrival

import android.content.Context
import com.black.app.model.AssistantTaskSettingsRepository
import com.black.app.usagelog.collect.HomeTransition

/**
 * 집 전환 신호 처리
 *
 * 출발이면 실제·추정을 나눠 시각을 저장하고, 도착이 [AssistantTaskHomeArrival.shouldNotify]를 충족하면 도착 작업을 예약함.
 * `UsageLogListenerService`가 Hilt 없이 만들어 쓰므로 저장소를 직접 만듦.
 * 출발 시각 저장이 실패하면 다음 도착은 그 전에 저장된 출발 시각으로 판정함.
 * 출발·도착 처리는 로그로 남기지 않음.
 * 실사용 앱(`stable`)도 `matchingFallbacks`로 core의 debug 변형을 써 core `BuildConfig.DEBUG`가 true라 core `Log`가 찍히고, 그 logcat 시각이 곧 출발·도착 시각임.
 */
class AssistantTaskHomeArrivalHandler(
    private val settings: AssistantTaskSettingsRepository,
    private val enqueueArrival: () -> Unit,
) {
    constructor(context: Context) : this(
        AssistantTaskSettingsRepository(context.applicationContext),
        { AssistantTaskHomeArrivalWorker.enqueue(context.applicationContext) },
    )

    suspend fun onHomeTransition(transition: HomeTransition) {
        // 추정 출발의 시각은 감시 시작, 수집 켜짐, 집 SSID 삭제처럼 상태를 맞춘 시각이라 실제 출발이 아니므로 마지막 출발로 저장하지 않고 따로 저장
        if (!transition.arrived) {
            if (transition.inferred) {
                settings.saveLastInferredHomeLeaveTime(transition.time)
            } else {
                settings.saveLastHomeLeaveTime(transition.time)
            }
            return
        }
        // 도착은 판정을 충족할 때만 작업 예약
        val lastLeaveTime = settings.getLastHomeLeaveTime()
        val lastInferredLeaveTime = settings.getLastInferredHomeLeaveTime()
        if (AssistantTaskHomeArrival.shouldNotify(transition, lastLeaveTime, lastInferredLeaveTime)) enqueueArrival()
    }
}
