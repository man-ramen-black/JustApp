package com.black.app.sleeproutine

import com.black.core.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 수면 루틴 단계 순서·대기·마무리 진행
 *
 * 기기 동작은 [SleepRoutineActions]로 받아 가상 시간 유닛 테스트로 검증.
 * YouTube는 재생 중일 때 홈 화면으로 가면 PIP로 바뀌므로 일시정지 후 홈 화면으로 보내고 다시 재생해 백그라운드 재생으로 전환.
 * 백그라운드 재생 전환 뒤 화면 끄기.
 * 이번 실행에서 방해 금지·수면 기록이 실패했으면 화면을 끄지 않고 기기의 자동 꺼짐에 맡기며, 처음 띄운 실패 안내가 화면 끄기 시점까지 시간이 지나 사라져 있으므로 그 실패를 다시 알림
 *
 * @param onFailure 실패해 건너뛴 단계 알림(서비스의 토스트 안내)
 * @param clock 종료 시각과 비교하는 현재 시각(벽시계)
 */
class SleepRoutineRunner(
    private val actions: SleepRoutineActions,
    private val onFailure: (SleepRoutineFailure) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** 수면 기록 화면이 뜬 뒤 YouTube를 열기까지 대기, Tasker와 같은 3초 */
        const val SLEEP_TO_YOUTUBE_DELAY_MILLIS = 3_000L

        /** 이미 재생 중이던 영상의 상태를 새 영상의 재생으로 오인하지 않도록 상태 확인 전 대기 */
        const val YOUTUBE_LOADING_DELAY_MILLIS = 2_000L
        const val PLAYING_TIMEOUT_MILLIS = 20_000L
        const val PAUSED_TIMEOUT_MILLIS = 3_000L
        const val AFTER_HOME_DELAY_MILLIS = 1_000L
        const val RESUMED_TIMEOUT_MILLIS = 3_000L
        const val STATE_POLL_INTERVAL_MILLIS = 250L

        /** 알림 접근이 없어 상태를 읽지 못할 때 재생 시작을 기다리는 고정 대기 */
        const val FALLBACK_PLAYING_WAIT_MILLIS = 5_000L

        /** 알림 접근이 없을 때 일시정지 뒤 홈 화면 이동까지 고정 대기 */
        const val FALLBACK_PAUSED_WAIT_MILLIS = 1_000L

        /** 벽시계 변경을 반영하도록 종료 대기를 나누는 최대 간격 */
        const val MAX_WAIT_CHUNK_MILLIS = 60_000L
    }

    private val finished = AtomicBoolean(false)

    /**
     * 앱 실행 단계부터 종료 시각까지 진행한 뒤 마무리
     *
     * @param volumeLevel 맞출 미디어 볼륨 단계, null이면 볼륨을 바꾸지 않음
     */
    suspend fun start(youtubeUrl: String, volumeLevel: Int?, endTimeMillis: Long) {
        launchApps(youtubeUrl, volumeLevel)
        resume(endTimeMillis)
    }

    /**
     * 앱 실행 단계 없이 종료 시각까지 기다린 뒤 마무리
     *
     * 프로세스 정리 뒤 서비스가 다시 시작된 경우에 사용.
     */
    suspend fun resume(endTimeMillis: Long) {
        waitUntil(endTimeMillis)
        finish()
    }

    /**
     * YouTube 일시정지 후 방해 금지 끄기
     *
     * 미리 종료와 종료 시각 도달이 겹쳐도 한 번만 실행하고, 한 단계가 실패해도 다음 단계 진행.
     *
     * 진행 중인 실행 단계를 멈추지 않으므로 미리 종료할 때는 호출자가 [start]·[resume] 코루틴을 먼저 취소한 뒤 그 코루틴과 같은 스레드에서 호출하는 것이 전제.
     * 취소하지 않으면 마무리 뒤 남은 실행 단계가 YouTube를 다시 재생하고, 다른 스레드에서 부르면 실행 중이던 단계와 마무리가 겹칠 수 있음
     */
    fun finish() {
        if (!finished.compareAndSet(false, true)) return
        runStep { actions.pauseYoutube() }
        runStep { actions.disableDoNotDisturb() }
    }

    private suspend fun launchApps(youtubeUrl: String, volumeLevel: Int?) {
        // 방해 금지 켜기
        val doNotDisturbEnabled = runStep { actions.enableDoNotDisturb() } == true
        if (!doNotDisturbEnabled) reportFailure(SleepRoutineFailure.DO_NOT_DISTURB)
        // 수면 기록 시작
        val sleepTrackingStarted = runStep { actions.startSleepTracking() } == true
        if (!sleepTrackingStarted) reportFailure(SleepRoutineFailure.SLEEP_TRACKING)
        // 수면 기록 화면이 뜰 때까지 대기 뒤 볼륨 조정
        delay(SLEEP_TO_YOUTUBE_DELAY_MILLIS)
        if (volumeLevel != null) runStep { actions.applyMediaVolume(volumeLevel) }
        // YouTube 열기, 열지 못하면 백그라운드 전환·화면 끄기 없이 종료 대기
        if (runStep { actions.openYoutube(youtubeUrl) } != true) {
            reportFailure(SleepRoutineFailure.YOUTUBE)
            return
        }
        // 백그라운드 재생 전환
        moveYoutubeToBackground()
        // 앞 단계가 실패했으면 화면은 기기의 자동 꺼짐에 맡기고, 사라진 실패 안내를 다시 띄움
        if (!doNotDisturbEnabled || !sleepTrackingStarted) {
            if (!doNotDisturbEnabled) reportFailure(SleepRoutineFailure.DO_NOT_DISTURB)
            if (!sleepTrackingStarted) reportFailure(SleepRoutineFailure.SLEEP_TRACKING)
            return
        }
        // 화면 끄기
        if (runStep { actions.lockScreen() } != true) reportFailure(SleepRoutineFailure.SCREEN_OFF)
    }

    /**
     * 일시정지 → 홈 화면 → 재생 순서로 PIP 없이 백그라운드 재생 전환
     *
     * 같은 영상이 일시정지 상태로 남아 있으면 URL을 다시 열어도 YouTube가 자동 재생하지 않으므로, 로딩 대기 뒤 PAUSED면 재생 명령을 먼저 보냄.
     * 세션이 없거나 버퍼링 중인 상태는 재생 시작을 그대로 기다림
     */
    private suspend fun moveYoutubeToBackground() {
        if (runStep { actions.canObserveYoutube() } != true) {
            delay(FALLBACK_PLAYING_WAIT_MILLIS)
            runStep { actions.pauseYoutube() }
            delay(FALLBACK_PAUSED_WAIT_MILLIS)
            runStep { actions.goHome() }
            delay(AFTER_HOME_DELAY_MILLIS)
            runStep { actions.playYoutube() }
            return
        }

        delay(YOUTUBE_LOADING_DELAY_MILLIS)
        if (runStep { actions.youtubePlaybackState() } == YoutubePlaybackState.PAUSED) {
            runStep { actions.playYoutube() }
        }
        // 광고·느린 로딩으로 제한 시간 안에 재생 상태가 오지 않아도 순서 진행
        awaitYoutubeState(YoutubePlaybackState.PLAYING, PLAYING_TIMEOUT_MILLIS)
        runStep { actions.pauseYoutube() }
        awaitYoutubeState(YoutubePlaybackState.PAUSED, PAUSED_TIMEOUT_MILLIS)
        runStep { actions.goHome() }
        delay(AFTER_HOME_DELAY_MILLIS)
        runStep { actions.playYoutube() }
        if (!awaitYoutubeState(YoutubePlaybackState.PLAYING, RESUMED_TIMEOUT_MILLIS)) {
            runStep { actions.playYoutube() }
        }
    }

    /** YouTube 상태가 [expected]가 될 때까지 대기, 제한 시간을 넘기면 false */
    private suspend fun awaitYoutubeState(expected: YoutubePlaybackState, timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            while (runStep { actions.youtubePlaybackState() } != expected) {
                delay(STATE_POLL_INTERVAL_MILLIS)
            }
            true
        } ?: false

    private suspend fun waitUntil(endTimeMillis: Long) {
        while (true) {
            val remainingMillis = endTimeMillis - clock()
            if (remainingMillis <= 0L) return
            delay(remainingMillis.coerceAtMost(MAX_WAIT_CHUNK_MILLIS))
        }
    }

    /** 실패 알림, 알림(토스트)이 예외를 던져도 루틴은 다음 단계 진행 */
    private fun reportFailure(failure: SleepRoutineFailure) {
        runStep { onFailure(failure) }
    }

    /** 기기 동작 하나 실행, 예외는 기록하고 null을 반환해 다음 단계 진행 */
    private fun <T> runStep(block: () -> T): T? = try {
        block()
    } catch (exception: Exception) {
        Log.e(exception)
        null
    }
}
