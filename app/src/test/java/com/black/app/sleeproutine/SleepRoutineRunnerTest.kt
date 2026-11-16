package com.black.app.sleeproutine

import android.content.ActivityNotFoundException
import com.black.test.BaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [SleepRoutineRunner]의 단계 순서·간격, YouTube 백그라운드 전환, 화면 끄기, 종료 시각 마무리, 단계 실패 뒤 진행을 가상 시간으로 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineRunnerTest : BaseTest() {

    companion object {
        private const val YOUTUBE_URL = "https://www.youtube.com/watch?v=sleep"
        private const val VOLUME_LEVEL = 6
        private const val END_TIME_MILLIS = 3_600_000L

        /**
         * 앱 실행 단계 중 화면 끄기 전까지의 기기 동작 순서
         *
         * YouTube 백그라운드 전환 포함
         */
        private val LAUNCH_CALL_NAMES = listOf(
            "enableDoNotDisturb",
            "startSleepTracking",
            "applyMediaVolume",
            "openYoutube",
            "pauseYoutube",
            "goHome",
            "playYoutube",
        )

        /** 앱 실행 단계가 모두 성공했을 때의 기기 동작 순서, 백그라운드 전환 뒤 화면 끄기 */
        private val SCREEN_OFF_LAUNCH_CALL_NAMES = LAUNCH_CALL_NAMES + "lockScreen"

        /** 마무리 단계의 기기 동작 순서 */
        private val FINISH_CALL_NAMES = listOf("pauseYoutube", "disableDoNotDisturb")

        /** 앞 단계 실패로 화면 끄기를 건너뛰고 마무리까지 진행했을 때의 기기 동작 순서 */
        private val COMPLETED_WITHOUT_SCREEN_OFF_CALL_NAMES = LAUNCH_CALL_NAMES + FINISH_CALL_NAMES
    }

    private val failures = mutableListOf<SleepRoutineFailure>()

    /**
     * 호출을 `이름@가상시각`으로 [calls]에 기록하는 가짜 기기 동작
     *
     * YouTube 상태는 마지막 명령으로 결정.
     * openYoutube 뒤 [playingDelayMillis]가 지나면 PLAYING, pauseYoutube 뒤 [pausedDelayMillis]가 지나면 PAUSED(그 전에는 일시정지 전 상태), playYoutube 뒤 PLAYING이고 처음 [ignorePlayCount]번의 playYoutube는 상태를 바꾸지 않음.
     * [pausedAfterOpen]이면 openYoutube 뒤에도 일시정지 상태로 남아 자동 재생하지 않고 playYoutube 뒤에야 PLAYING.
     * lockScreen은 [screenLockable]을 반환하고 [lockScreenFailure]가 있으면 던짐.
     * 상태 조회는 순서 단언을 흐리지 않도록 [calls] 대신 [playbackStateQueryCount]로 집계
     */
    private class FakeSleepRoutineActions(
        private val clock: () -> Long,
        private val doNotDisturbAllowed: Boolean = true,
        private val sleepTrackingFailure: Exception? = null,
        private val youtubeOpenable: Boolean = true,
        private val youtubeObservable: Boolean = true,
        private val playingDelayMillis: Long = 4_000L,
        private val pausedDelayMillis: Long = 0L,
        ignorePlayCount: Int = 0,
        private val pauseFailure: Exception? = null,
        private val pausedAfterOpen: Boolean = false,
        private val screenLockable: Boolean = true,
        private val lockScreenFailure: Exception? = null,
    ) : SleepRoutineActions {
        val calls = mutableListOf<String>()
        var playbackStateQueryCount = 0
            private set

        private var remainingIgnoredPlayCount = ignorePlayCount
        private var playbackState: () -> YoutubePlaybackState = { YoutubePlaybackState.OTHER }

        /** [name] 동작을 호출한 가상 시각 목록 */
        fun callTimes(name: String): List<Long> = calls
            .filter { callName(it) == name }
            .map { it.substringAfterLast('@').toLong() }

        /** 인자·시각을 뺀 호출 이름 순서 */
        fun callNames(): List<String> = calls.map(::callName)

        private fun callName(call: String): String = call.substringBefore('@').substringBefore('(')

        private fun record(call: String) {
            calls.add("$call@${clock()}")
        }

        override fun enableDoNotDisturb(): Boolean {
            record("enableDoNotDisturb")
            return doNotDisturbAllowed
        }

        override fun disableDoNotDisturb() {
            record("disableDoNotDisturb")
        }

        override fun startSleepTracking(): Boolean {
            record("startSleepTracking")
            sleepTrackingFailure?.let { throw it }
            return true
        }

        override fun applyMediaVolume(level: Int) {
            record("applyMediaVolume($level)")
        }

        override fun openYoutube(url: String): Boolean {
            record("openYoutube($url)")
            if (!youtubeOpenable) return false
            if (pausedAfterOpen) {
                playbackState = { YoutubePlaybackState.PAUSED }
                return true
            }
            val openedAt = clock()
            // 빼기로 비교해 playingDelayMillis가 Long.MAX_VALUE여도 넘치지 않음
            playbackState = {
                if (clock() - openedAt >= playingDelayMillis) YoutubePlaybackState.PLAYING else YoutubePlaybackState.OTHER
            }
            return true
        }

        override fun goHome() {
            record("goHome")
        }

        override fun canObserveYoutube(): Boolean = youtubeObservable

        override fun youtubePlaybackState(): YoutubePlaybackState {
            playbackStateQueryCount++
            return playbackState()
        }

        override fun playYoutube() {
            record("playYoutube")
            if (remainingIgnoredPlayCount > 0) {
                remainingIgnoredPlayCount--
                return
            }
            playbackState = { YoutubePlaybackState.PLAYING }
        }

        override fun pauseYoutube() {
            record("pauseYoutube")
            pauseFailure?.let { throw it }
            val pausedAt = clock()
            val stateBeforePause = playbackState
            playbackState = {
                if (clock() - pausedAt >= pausedDelayMillis) YoutubePlaybackState.PAUSED else stateBeforePause()
            }
        }

        override fun lockScreen(): Boolean {
            record("lockScreen")
            lockScreenFailure?.let { throw it }
            return screenLockable
        }
    }

    /** 대기 중 벽시계 변경을 재현하려고 [wallClockMillis]에 더하는 값 */
    private var wallClockOffsetMillis = 0L

    /**
     * 테스트 스케줄러의 가상 시각
     *
     * 가짜 동작의 기록 시각으로 사용.
     */
    private fun virtualTimeMillis(): Long = testDispatcherRule.testDispatcher.scheduler.currentTime

    /**
     * Runner에 넘기는 벽시계
     *
     * 가상 시각에 [wallClockOffsetMillis]를 더한 값이라 오프셋을 바꾸지 않으면 가상 시각과 같음
     */
    private fun wallClockMillis(): Long = virtualTimeMillis() + wallClockOffsetMillis

    private fun createRunner(
        actions: SleepRoutineActions,
        onFailure: (SleepRoutineFailure) -> Unit = { failures.add(it) },
    ) = SleepRoutineRunner(
        actions = actions,
        onFailure = onFailure,
        clock = ::wallClockMillis,
    )

    /**
     * 알림 접근이 있을 때 앱 실행 단계가 순서·간격대로 진행되는지 검증
     *
     * Given: 열고 4초 뒤 재생 중이 되는 YouTube, 상태 조회 가능
     * When: 볼륨 6단계로 시작 후 종료 시각 직전까지 진행
     * Then: 방해 금지·수면 기록(0) → 볼륨·YouTube 열기(3000) → 재생 확인 뒤 일시정지 → 일시정지 반영 즉시 홈 → 1초 뒤 재생 한 번 → 같은 시각 화면 끄기, 실패 없음
     */
    @Test
    fun test_01_startRunsStepsInOrderWhenPlaybackObservable() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(END_TIME_MILLIS - 1)

        /** Then **/
        val pauseTime = actions.callTimes("pauseYoutube").single()
        val homeTime = actions.callTimes("goHome").single()
        assertEquals(
            listOf(
                "enableDoNotDisturb@0",
                "startSleepTracking@0",
                "applyMediaVolume($VOLUME_LEVEL)@3000",
                "openYoutube($YOUTUBE_URL)@3000",
                "pauseYoutube@$pauseTime",
                "goHome@$homeTime",
                "playYoutube@${homeTime + 1_000}",
                "lockScreen@${homeTime + 1_000}",
            ),
            actions.calls,
        )
        // 로딩 대기가 끝난 5000이 아니라 PLAYING에 도달한 7000 이후, 제한 시간까지 기다리지 않고 상태 확인 한 간격 안에 일시정지
        assertTrue("pauseYoutube@$pauseTime", pauseTime in 7_000L..7_000L + SleepRoutineRunner.STATE_POLL_INTERVAL_MILLIS)
        // 일시정지가 곧바로 반영되면 제한 시간까지 기다리지 않고 같은 시각에 홈
        assertEquals(pauseTime, homeTime)
        assertEquals(emptyList<SleepRoutineFailure>(), failures)
    }

    /**
     * 볼륨 단계가 없으면 볼륨을 바꾸지 않고 진행하는지 검증
     *
     * Given: 기본 가짜 기기 동작
     * When: volumeLevel null로 시작해 끝까지 진행
     * Then: applyMediaVolume 호출 없음, YouTube는 3000에 열림
     */
    @Test
    fun test_02_startSkipsVolumeWhenLevelIsNull() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, volumeLevel = null, endTimeMillis = END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(emptyList<Long>(), actions.callTimes("applyMediaVolume"))
        assertEquals(listOf(3_000L), actions.callTimes("openYoutube"))
    }

    /**
     * 방해 금지를 켜지 못해도 실패를 알리고 다음 단계를 진행하는지 검증
     *
     * Given: 방해 금지 접근 권한이 없어 enableDoNotDisturb가 false
     * When: 시작해 끝까지 진행
     * Then: onFailure(DO_NOT_DISTURB)가 처음과 화면 끄기를 건너뛸 때 2회, 화면 끄기만 건너뛰고 나머지 단계와 마무리 모두 진행
     */
    @Test
    fun test_03_doNotDisturbFailureReportsAndContinues() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, doNotDisturbAllowed = false)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(listOf(SleepRoutineFailure.DO_NOT_DISTURB, SleepRoutineFailure.DO_NOT_DISTURB), failures)
        assertEquals(COMPLETED_WITHOUT_SCREEN_OFF_CALL_NAMES, actions.callNames())
    }

    /**
     * 수면 기록 시작이 예외를 던져도 실패를 알리고 다음 단계를 진행하는지 검증
     *
     * Given: Sleep as Android가 없어 startSleepTracking이 ActivityNotFoundException을 던짐
     * When: 시작해 끝까지 진행
     * Then: onFailure(SLEEP_TRACKING)가 처음과 화면 끄기를 건너뛸 때 2회, 화면 끄기만 건너뛰고 나머지 단계와 마무리 모두 진행
     */
    @Test
    fun test_04_sleepTrackingExceptionReportsAndContinues() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(
            ::virtualTimeMillis,
            sleepTrackingFailure = ActivityNotFoundException("Sleep as Android not installed"),
        )
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(listOf(SleepRoutineFailure.SLEEP_TRACKING, SleepRoutineFailure.SLEEP_TRACKING), failures)
        assertEquals(COMPLETED_WITHOUT_SCREEN_OFF_CALL_NAMES, actions.callNames())
    }

    /**
     * YouTube를 열지 못하면 백그라운드 전환을 건너뛰고 종료 시각에 마무리하는지 검증
     *
     * Given: openYoutube가 false
     * When: 시작해 끝까지 진행
     * Then: onFailure(YOUTUBE), 실행 단계의 일시정지·홈·재생·화면 끄기 없음, 마무리 일시정지만 종료 시각에 1회
     */
    @Test
    fun test_05_youtubeOpenFailureSkipsBackgroundSwitch() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, youtubeOpenable = false)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(listOf(SleepRoutineFailure.YOUTUBE), failures)
        assertEquals(
            listOf("enableDoNotDisturb", "startSleepTracking", "applyMediaVolume", "openYoutube", "pauseYoutube", "disableDoNotDisturb"),
            actions.callNames(),
        )
        assertEquals(listOf(END_TIME_MILLIS), actions.callTimes("pauseYoutube"))
    }

    /**
     * 알림 접근이 없으면 상태 조회 없이 고정 대기로 백그라운드 전환하는지 검증
     *
     * Given: canObserveYoutube가 false
     * When: 시작 후 종료 시각 직전까지 진행
     * Then: 상태 조회 0회, 일시정지 = 열기 + 5000, 홈 = 일시정지 + 1000, 재생 = 홈 + 1000이고 재생은 한 번, 재생 직후 같은 시각 화면 끄기
     */
    @Test
    fun test_06_unobservablePlaybackUsesFixedWaits() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, youtubeObservable = false)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(END_TIME_MILLIS - 1)

        /** Then **/
        val openTime = actions.callTimes("openYoutube").single()
        val pauseTime = actions.callTimes("pauseYoutube").single()
        val homeTime = actions.callTimes("goHome").single()
        val playTime = actions.callTimes("playYoutube").single()
        assertEquals(0, actions.playbackStateQueryCount)
        assertEquals(openTime + 5_000, pauseTime)
        assertEquals(pauseTime + 1_000, homeTime)
        assertEquals(homeTime + 1_000, playTime)
        assertEquals(SCREEN_OFF_LAUNCH_CALL_NAMES, actions.callNames())
        assertEquals(listOf(playTime), actions.callTimes("lockScreen"))
    }

    /**
     * 재생 중 상태가 오지 않아도 제한 시간 뒤 순서를 계속 진행하는지 검증
     *
     * Given: 열어도 재생 중이 되지 않는 YouTube(광고·느린 로딩)
     * When: 시작 후 종료 시각 직전까지 진행
     * Then: 일시정지가 열기 3000 + 로딩 대기 2000 + 제한 시간 20000 = 25000에 실행되고 홈(25000)·재생(26000)·화면 끄기까지 진행
     */
    @Test
    fun test_07_playingTimeoutStillContinues() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, playingDelayMillis = Long.MAX_VALUE)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(END_TIME_MILLIS - 1)

        /** Then **/
        assertEquals(SCREEN_OFF_LAUNCH_CALL_NAMES, actions.callNames())
        assertEquals(listOf(25_000L), actions.callTimes("pauseYoutube"))
        assertEquals(listOf(25_000L), actions.callTimes("goHome"))
        assertEquals(listOf(26_000L), actions.callTimes("playYoutube"))
    }

    /**
     * 홈 화면 이동 뒤 재생 명령이 무시되면 제한 시간 뒤 한 번 더 재생하는지 검증
     *
     * Given: playYoutube를 두 번 모두 무시하는 YouTube
     * When: 시작 후 종료 시각 직전까지 진행
     * Then: playYoutube는 홈 + 1000인 8000과 재생 확인 제한 시간 3000이 지난 11000에 2회만 실행, 두 번째 재생 직후 화면 끄기
     */
    @Test
    fun test_08_ignoredResumeRetriesPlayOnce() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, ignorePlayCount = 2)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(END_TIME_MILLIS - 1)

        /** Then **/
        assertEquals(LAUNCH_CALL_NAMES + "playYoutube" + "lockScreen", actions.callNames())
        assertEquals(listOf(8_000L, 11_000L), actions.callTimes("playYoutube"))
        assertEquals(listOf(11_000L), actions.callTimes("lockScreen"))
    }

    /**
     * 종료 시각 전에는 마무리하지 않고 종료 시각에 일시정지·방해 금지 끄기를 순서대로 한 번씩 하는지 검증
     *
     * Given: 종료 시각 3_600_000으로 시작
     * When: 종료 시각 직전까지 진행한 뒤 1ms 더 진행
     * Then: 직전에는 방해 금지 끄기 없음·일시정지는 실행 단계의 1회뿐, 종료 시각에 pauseYoutube → disableDoNotDisturb 1회씩 실행 후 종료
     */
    @Test
    fun test_09_finishRunsAtEndTime() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)
        val job = launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }

        /** When **/
        advanceCoroutineTime(END_TIME_MILLIS - 1)
        val disableTimesBeforeEnd = actions.callTimes("disableDoNotDisturb")
        val pauseCountBeforeEnd = actions.callTimes("pauseYoutube").size
        advanceCoroutineTime(1)

        /** Then **/
        assertEquals(emptyList<Long>(), disableTimesBeforeEnd)
        assertEquals(1, pauseCountBeforeEnd)
        assertEquals(listOf("pauseYoutube@$END_TIME_MILLIS", "disableDoNotDisturb@$END_TIME_MILLIS"), actions.calls.takeLast(2))
        assertEquals(2, actions.callTimes("pauseYoutube").size)
        assertEquals(listOf(END_TIME_MILLIS), actions.callTimes("disableDoNotDisturb"))
        assertTrue(job.isCompleted)
    }

    /**
     * finish가 두 번 호출돼도 마무리는 한 번만 실행되는지 검증
     *
     * Given: 시작 후 앱 실행 단계가 끝난 20_000 시점
     * When: finish로 미리 마무리한 뒤 종료 시각까지 진행해 Runner가 finish를 한 번 더 호출
     * Then: disableDoNotDisturb는 미리 마무리한 20_000에 1회, 마무리 일시정지도 20_000에 1회
     */
    @Test
    fun test_10_finishRunsOnlyOnce() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)
        val job = launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(20_000)

        /** When **/
        runner.finish()
        waitCoroutine()

        /** Then **/
        assertEquals(listOf(20_000L), actions.callTimes("disableDoNotDisturb"))
        val pauseTimes = actions.callTimes("pauseYoutube")
        assertEquals(2, pauseTimes.size)
        assertEquals(20_000L, pauseTimes.last())
        assertTrue(job.isCompleted)
    }

    /**
     * 종료 시각이 이미 지난 상태로 재개하면 앱 실행 단계 없이 바로 마무리하는지 검증
     *
     * Given: 가상 시각 10_000
     * When: 종료 시각 5_000으로 resume
     * Then: 10_000에 pauseYoutube → disableDoNotDisturb만 실행(화면 끄기 없음), 상태 조회·실패 없음
     */
    @Test
    fun test_11_resumeAfterEndTimeFinishesImmediately() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)
        advanceCoroutineTime(10_000)

        /** When **/
        launch { runner.resume(endTimeMillis = 5_000) }
        waitCoroutine()

        /** Then **/
        assertEquals(listOf("pauseYoutube@10000", "disableDoNotDisturb@10000"), actions.calls)
        assertEquals(0, actions.playbackStateQueryCount)
        assertEquals(emptyList<SleepRoutineFailure>(), failures)
    }

    /**
     * 마무리에서 YouTube 일시정지가 실패해도 방해 금지를 끄는지 검증
     *
     * Given: pauseYoutube가 예외를 던짐
     * When: finish 호출
     * Then: pauseYoutube 시도 뒤 disableDoNotDisturb 실행, 화면 끄기 없음
     */
    @Test
    fun test_12_finishDisablesDoNotDisturbWhenPauseFails() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, pauseFailure = IllegalStateException("YouTube session not found"))
        val runner = createRunner(actions)

        /** When **/
        runner.finish()

        /** Then **/
        assertEquals(listOf("pauseYoutube@0", "disableDoNotDisturb@0"), actions.calls)
    }

    /**
     * 일시정지가 늦게 반영되면 PAUSED를 확인한 뒤 홈 화면으로 가는지 검증
     *
     * Given: 일시정지 명령 1100 뒤에 PAUSED가 반영되는 YouTube
     * When: 시작 후 종료 시각 직전까지 진행
     * Then: 일시정지 7000, PAUSED가 된 8100 뒤 첫 상태 조회인 8250에 홈, 1초 뒤 9250에 재생
     */
    @Test
    fun test_13_goHomeWaitsUntilPauseReflected() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, pausedDelayMillis = 1_100L)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(END_TIME_MILLIS - 1)

        /** Then **/
        assertEquals(SCREEN_OFF_LAUNCH_CALL_NAMES, actions.callNames())
        assertEquals(listOf(7_000L), actions.callTimes("pauseYoutube"))
        assertEquals(listOf(8_250L), actions.callTimes("goHome"))
        assertEquals(listOf(9_250L), actions.callTimes("playYoutube"))
    }

    /**
     * 일시정지가 반영되지 않아도 제한 시간 뒤 홈 화면으로 가는지 검증
     *
     * Given: 일시정지 명령을 받아도 PAUSED가 되지 않는 YouTube
     * When: 시작 후 종료 시각 직전까지 진행
     * Then: 일시정지 7000, 일시정지 확인 제한 시간 3000이 지난 10000에 홈, 1초 뒤 11000에 재생
     */
    @Test
    fun test_14_goHomeAfterPausedTimeoutWhenPauseNotReflected() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, pausedDelayMillis = Long.MAX_VALUE)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(END_TIME_MILLIS - 1)

        /** Then **/
        assertEquals(SCREEN_OFF_LAUNCH_CALL_NAMES, actions.callNames())
        assertEquals(listOf(7_000L), actions.callTimes("pauseYoutube"))
        assertEquals(listOf(7_000L + SleepRoutineRunner.PAUSED_TIMEOUT_MILLIS), actions.callTimes("goHome"))
        assertEquals(listOf(11_000L), actions.callTimes("playYoutube"))
    }

    /**
     * 종료 대기 중 벽시계가 종료 시각을 넘어 앞당겨지면 최대 대기 간격 안에 마무리하는지 검증
     *
     * Given: 종료 시각 3_600_000으로 resume한 뒤 가상 시각 100_000까지 진행
     * When: 벽시계를 종료 시각만큼 앞당기고 최대 대기 간격 60_000만큼 진행
     * Then: 앞당기기 전에는 호출 없음, 다음 대기 간격이 끝나는 120_000에 pauseYoutube → disableDoNotDisturb 실행 후 종료
     */
    @Test
    fun test_15_wallClockJumpFinishesWithinWaitChunk() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)
        val job = launch { runner.resume(END_TIME_MILLIS) }
        advanceCoroutineTime(100_000)
        val callsBeforeJump = actions.calls.toList()

        /** When **/
        wallClockOffsetMillis = END_TIME_MILLIS
        advanceCoroutineTime(SleepRoutineRunner.MAX_WAIT_CHUNK_MILLIS)

        /** Then **/
        assertEquals(emptyList<String>(), callsBeforeJump)
        assertEquals(listOf("pauseYoutube@120000", "disableDoNotDisturb@120000"), actions.calls)
        assertTrue(job.isCompleted)
    }

    /**
     * 종료 시각이 남은 상태로 재개하면 앱 실행 단계 없이 정확히 종료 시각에 마무리하는지 검증
     *
     * Given: 가상 시각 10_000
     * When: 종료 시각 100_000으로 resume한 뒤 종료 시각 직전까지 진행하고 1ms 더 진행
     * Then: 직전에는 호출 없음, 100_000에 pauseYoutube → disableDoNotDisturb만 실행(화면 끄기 없음) 후 종료, 상태 조회·실패 없음
     */
    @Test
    fun test_16_resumeBeforeEndTimeFinishesAtEndTime() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)
        advanceCoroutineTime(10_000)

        /** When **/
        val job = launch { runner.resume(endTimeMillis = 100_000) }
        advanceCoroutineTime(100_000 - 10_000 - 1)
        val callsBeforeEnd = actions.calls.toList()
        advanceCoroutineTime(1)

        /** Then **/
        assertEquals(emptyList<String>(), callsBeforeEnd)
        assertEquals(listOf("pauseYoutube@100000", "disableDoNotDisturb@100000"), actions.calls)
        assertEquals(0, actions.playbackStateQueryCount)
        assertEquals(emptyList<SleepRoutineFailure>(), failures)
        assertTrue(job.isCompleted)
    }

    /**
     * 실패 알림이 예외를 던져도 루틴이 중단되지 않고 이후 단계와 마무리를 진행하는지 검증
     *
     * Given: 방해 금지·수면 기록·YouTube 열기가 모두 실패하고, onFailure는 기록 후 예외를 던짐
     * When: 시작해 끝까지 진행
     * Then: 세 실패 모두 알림, 볼륨·YouTube 열기 시도 뒤 종료 시각에 pauseYoutube → disableDoNotDisturb 실행 후 종료
     */
    @Test
    fun test_17_failureCallbackExceptionDoesNotStopRoutine() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(
            ::virtualTimeMillis,
            doNotDisturbAllowed = false,
            sleepTrackingFailure = ActivityNotFoundException("Sleep as Android not installed"),
            youtubeOpenable = false,
        )
        val runner = createRunner(
            actions,
            onFailure = { failure ->
                failures.add(failure)
                throw IllegalStateException("Toast failed")
            },
        )

        /** When **/
        val job = launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(
            listOf(SleepRoutineFailure.DO_NOT_DISTURB, SleepRoutineFailure.SLEEP_TRACKING, SleepRoutineFailure.YOUTUBE),
            failures,
        )
        assertEquals(
            listOf("enableDoNotDisturb", "startSleepTracking", "applyMediaVolume", "openYoutube", "pauseYoutube", "disableDoNotDisturb"),
            actions.callNames(),
        )
        assertEquals(listOf("pauseYoutube@$END_TIME_MILLIS", "disableDoNotDisturb@$END_TIME_MILLIS"), actions.calls.takeLast(2))
        assertTrue(job.isCompleted)
    }

    /**
     * 같은 영상이 일시정지로 남아 있으면 로딩 대기 직후 재생 명령을 보내 제한 시간까지 기다리지 않는지 검증
     *
     * Given: 열어도 자동 재생하지 않고 PAUSED로 남는 YouTube
     * When: 시작 후 종료 시각 직전까지 진행
     * Then: 로딩 대기 직후 5000에 playYoutube, PLAYING이 되자마자 같은 시각에 일시정지 → 홈, 1초 뒤 6000에 재생 → 화면 끄기
     */
    @Test
    fun test_18_pausedSameVideoGetsPlayCommandAfterLoadingDelay() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, pausedAfterOpen = true)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(END_TIME_MILLIS - 1)

        /** Then **/
        val openTime = actions.callTimes("openYoutube").single()
        val loadedTime = openTime + SleepRoutineRunner.YOUTUBE_LOADING_DELAY_MILLIS
        assertEquals(listOf(loadedTime, loadedTime + 1_000), actions.callTimes("playYoutube"))
        assertEquals(listOf(loadedTime), actions.callTimes("pauseYoutube"))
        assertEquals(listOf(loadedTime), actions.callTimes("goHome"))
        assertEquals(
            SCREEN_OFF_LAUNCH_CALL_NAMES.toMutableList().apply { add(indexOf("pauseYoutube"), "playYoutube") },
            actions.callNames(),
        )
    }

    /**
     * 세션이 없거나 버퍼링 중인 OTHER 상태에서는 추가 재생 명령 없이 재생 시작을 기다리는지 검증
     *
     * Given: 열고 4초 뒤 재생 중이 되기 전까지 OTHER인 YouTube
     * When: 시작 후 종료 시각 직전까지 진행
     * Then: 일시정지 전 playYoutube 없이 test_01 순서 그대로, playYoutube는 홈 뒤 1회뿐
     */
    @Test
    fun test_19_otherStateDoesNotSendExtraPlayCommand() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(END_TIME_MILLIS - 1)

        /** Then **/
        assertEquals(SCREEN_OFF_LAUNCH_CALL_NAMES, actions.callNames())
        assertEquals(1, actions.callTimes("playYoutube").size)
    }

    /**
     * 백그라운드 전환 뒤 화면을 한 번만 끄고 종료 시각 마무리에서는 다시 끄지 않는지 검증
     *
     * Given: 기본 가짜 기기 동작(모든 단계 성공)
     * When: 시작해 끝까지 진행
     * Then: 백그라운드 전환의 마지막 재생 직후 lockScreen 1회, 마무리는 pauseYoutube → disableDoNotDisturb만, 실패 없음
     */
    @Test
    fun test_20_lockScreenRunsOnceAfterBackgroundSwitch() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)

        /** When **/
        val job = launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(SCREEN_OFF_LAUNCH_CALL_NAMES + FINISH_CALL_NAMES, actions.callNames())
        assertEquals(listOf(actions.callTimes("playYoutube").last()), actions.callTimes("lockScreen"))
        assertEquals(emptyList<SleepRoutineFailure>(), failures)
        assertTrue(job.isCompleted)
    }

    /**
     * 화면을 끄지 못하면 SCREEN_OFF 실패를 알리고 종료 시각에 마무리하는지 검증
     *
     * Given: 접근성 서비스가 꺼져 lockScreen이 false
     * When: 시작해 끝까지 진행
     * Then: onFailure(SCREEN_OFF) 1회, 종료 시각에 pauseYoutube → disableDoNotDisturb 실행 후 종료
     */
    @Test
    fun test_21_lockScreenFalseReportsScreenOffFailure() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis, screenLockable = false)
        val runner = createRunner(actions)

        /** When **/
        val job = launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(listOf(SleepRoutineFailure.SCREEN_OFF), failures)
        assertEquals(SCREEN_OFF_LAUNCH_CALL_NAMES + FINISH_CALL_NAMES, actions.callNames())
        assertEquals(listOf("pauseYoutube@$END_TIME_MILLIS", "disableDoNotDisturb@$END_TIME_MILLIS"), actions.calls.takeLast(2))
        assertTrue(job.isCompleted)
    }

    /**
     * 화면 끄기가 예외를 던져도 SCREEN_OFF 실패를 알리고 종료 시각에 마무리하는지 검증
     *
     * Given: lockScreen이 SecurityException을 던짐
     * When: 시작해 끝까지 진행
     * Then: onFailure(SCREEN_OFF) 1회, 종료 시각에 pauseYoutube → disableDoNotDisturb 실행 후 종료
     */
    @Test
    fun test_22_lockScreenExceptionReportsScreenOffFailure() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(
            ::virtualTimeMillis,
            lockScreenFailure = SecurityException("broadcast not allowed"),
        )
        val runner = createRunner(actions)

        /** When **/
        val job = launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(listOf(SleepRoutineFailure.SCREEN_OFF), failures)
        assertEquals(SCREEN_OFF_LAUNCH_CALL_NAMES + FINISH_CALL_NAMES, actions.callNames())
        assertEquals(listOf("pauseYoutube@$END_TIME_MILLIS", "disableDoNotDisturb@$END_TIME_MILLIS"), actions.calls.takeLast(2))
        assertTrue(job.isCompleted)
    }

    /**
     * 방해 금지와 수면 기록이 모두 실패하면 화면 끄기를 건너뛸 때 두 실패를 순서대로 다시 알리는지 검증
     *
     * Given: 방해 금지 접근 권한이 없고 수면 기록 시작도 예외를 던짐
     * When: 시작해 끝까지 진행
     * Then: onFailure가 DO_NOT_DISTURB, SLEEP_TRACKING, DO_NOT_DISTURB, SLEEP_TRACKING 순서, lockScreen 호출 없음
     */
    @Test
    fun test_23_skippedScreenOffReportsFailedStepsAgainInOrder() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(
            ::virtualTimeMillis,
            doNotDisturbAllowed = false,
            sleepTrackingFailure = ActivityNotFoundException("Sleep as Android not installed"),
        )
        val runner = createRunner(actions)

        /** When **/
        launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        waitCoroutine()

        /** Then **/
        assertEquals(
            listOf(
                SleepRoutineFailure.DO_NOT_DISTURB,
                SleepRoutineFailure.SLEEP_TRACKING,
                SleepRoutineFailure.DO_NOT_DISTURB,
                SleepRoutineFailure.SLEEP_TRACKING,
            ),
            failures,
        )
        assertEquals(emptyList<Long>(), actions.callTimes("lockScreen"))
    }

    /**
     * 앱 실행 단계 중 지금 종료로 취소하면 화면을 끄지 않는지 검증
     *
     * Given: 앱 실행 단계 진행 중(YouTube를 연 뒤 백그라운드 전환 대기 중)
     * When: 실행 코루틴을 취소한 뒤 finish 호출
     * Then: lockScreen 호출 없음, 마무리의 pauseYoutube → disableDoNotDisturb만 실행
     */
    @Test
    fun test_24_cancelDuringLaunchDoesNotLockScreen() = runTest {
        /** Given **/
        val actions = FakeSleepRoutineActions(::virtualTimeMillis)
        val runner = createRunner(actions)
        val job = launch { runner.start(YOUTUBE_URL, VOLUME_LEVEL, END_TIME_MILLIS) }
        advanceCoroutineTime(SleepRoutineRunner.SLEEP_TO_YOUTUBE_DELAY_MILLIS + 1)

        /** When **/
        job.cancel()
        runner.finish()
        waitCoroutine()

        /** Then **/
        assertEquals(emptyList<Long>(), actions.callTimes("lockScreen"))
        assertEquals(FINISH_CALL_NAMES, actions.callNames().takeLast(2))
        assertTrue(job.isCancelled)
    }
}
