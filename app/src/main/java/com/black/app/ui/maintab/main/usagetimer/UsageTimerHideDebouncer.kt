package com.black.app.ui.maintab.main.usagetimer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 포그라운드 이벤트 버스트로 인한 타이머 깜빡임 방지 숨김 디바운서
 *
 * 앱 전환 시 시스템이 짧은 시간에 여러 창 이벤트를 보내며 숨김 직후 표시가 재요청돼
 * 오버레이가 remove→add 되며 깜빡이므로, 숨김을 [hideDelayMillis]만큼 지연 실행하고
 * 지연 중 표시 요청 시 [cancel]로 예약을 취소하여 깜빡임 제거
 */
class UsageTimerHideDebouncer(
    private val scope: CoroutineScope,
    private val hideDelayMillis: Long,
    private val onHide: () -> Unit,
) {
    private var hideJob: Job? = null

    /**
     * 지연 숨김 예약
     * 이미 예약된 숨김이 있으면 기한을 미루지 않고 그대로 유지
     */
    fun schedule() {
        if (hideJob?.isActive == true) return
        hideJob = scope.launch {
            delay(hideDelayMillis)
            onHide()
        }
    }

    /**
     * 예약된 지연 숨김 취소
     */
    fun cancel() {
        hideJob?.cancel()
        hideJob = null
    }
}
