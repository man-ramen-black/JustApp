package com.black.core.system

import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/** [MediaSessionPlaybackController]의 재생 상태 변환과 재생·일시정지 경로(세션 제어·미디어 키) 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MediaSessionPlaybackControllerTest : BaseTest() {

    private lateinit var realContext: Context
    private lateinit var audioManager: AudioManager
    private lateinit var sessionManager: MediaSessionManager
    private lateinit var context: Context
    private lateinit var controller: MediaSessionPlaybackController

    /** 앱 모듈이 선언하는 알림 리스너를 core 테스트에서 대신 등록한 컴포넌트 */
    private lateinit var listenerService: ComponentName

    @Before
    fun setupController() {
        realContext = RuntimeEnvironment.getApplication()
        // 컨트롤러가 생성 시 매니페스트 선언으로 리스너를 찾으므로 생성 전에 등록
        listenerService = ComponentName(realContext.packageName, "${realContext.packageName}.TestNotificationListenerService")
        shadowOf(realContext.packageManager).apply {
            addServiceIfNotPresent(listenerService)
            addIntentFilterForService(listenerService, IntentFilter(NotificationListenerService.SERVICE_INTERFACE))
        }
        audioManager = mockk(relaxed = true)
        sessionManager = mockk()
        every { sessionManager.getActiveSessions(any()) } returns emptyList()
        context = mockk {
            every { packageName } returns realContext.packageName
            every { contentResolver } returns realContext.contentResolver
            every { packageManager } returns realContext.packageManager
            every { getSystemService(Context.AUDIO_SERVICE) } returns audioManager
            every { getSystemService(Context.MEDIA_SESSION_SERVICE) } returns sessionManager
        }
        controller = MediaSessionPlaybackController(context, TARGET_PACKAGE)
    }

    private fun setNotificationAccess(granted: Boolean) {
        val value = if (granted) {
            listenerService.flattenToString()
        } else {
            null
        }
        Settings.Secure.putString(realContext.contentResolver, "enabled_notification_listeners", value)
    }

    /** transportControls 호출 중 바인더 메서드 이름 기록(세션 컨트롤러 인터페이스가 숨김 API라 프록시로 대신함) */
    private val binderCalls = mutableMapOf<MediaController, MutableList<String>>()

    /**
     * 실제 [MediaController] 생성
     *
     * 최종 클래스라 MockK로 대신할 수 없어, Robolectric 쉐도우로 패키지·재생 상태를 정하고 바인더는 호출 기록 프록시로 둠
     */
    private fun session(playbackStateValue: Int?, packageName: String = TARGET_PACKAGE): MediaController {
        val calls = mutableListOf<String>()
        val binderInterface = Class.forName("android.media.session.ISessionController")
        val binder = Proxy.newProxyInstance(binderInterface.classLoader, arrayOf(binderInterface)) { _, method, _ ->
            calls.add(method.name)
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        }
        val token = MediaSession.Token::class.java
            .getDeclaredConstructor(Integer.TYPE, binderInterface)
            .apply { isAccessible = true }
            .newInstance(0, binder)
        val controller = MediaController(realContext, token)
        shadowOf(controller).setPackageName(packageName)
        playbackStateValue?.let { shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(it, 0L, 1f).build()) }
        binderCalls[controller] = calls
        return controller
    }

    private fun callsOf(controller: MediaController): List<String> = binderCalls.getValue(controller)

    /**
     * 재생 상태가 PlaybackState 값별로 변환되는지 검증
     *
     * Given: 알림 접근 허용, 대상 앱 세션의 상태가 PLAYING·PAUSED·STOPPED·BUFFERING으로 바뀜
     * When: 상태마다 playbackState 호출
     * Then: PLAYING, PAUSED, PAUSED, OTHER
     */
    @Test
    fun test_01_playbackStateConversion() = runTest {
        /** Given **/
        setNotificationAccess(true)
        val states = listOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_BUFFERING,
        )

        /** When **/
        val results = states.map { state ->
            every { sessionManager.getActiveSessions(any()) } returns listOf(session(state))
            controller.playbackState()
        }

        /** Then **/
        assertEquals(
            listOf(
                MediaPlaybackState.PLAYING,
                MediaPlaybackState.PAUSED,
                MediaPlaybackState.PAUSED,
                MediaPlaybackState.OTHER,
            ),
            results,
        )
    }

    /**
     * 대상 앱 세션이 없거나 알림 접근이 없으면 OTHER인지 검증
     *
     * Given: 알림 접근 허용에 다른 앱 세션만 있음, 알림 접근 해제
     * When: 각 상태에서 playbackState 호출
     * Then: 둘 다 OTHER, canObserve는 허용 시 true, 해제 시 false
     */
    @Test
    fun test_02_playbackStateOtherWithoutSessionOrAccess() = runTest {
        /** Given **/
        setNotificationAccess(true)
        every { sessionManager.getActiveSessions(any()) } returns
            listOf(session(PlaybackState.STATE_PLAYING, packageName = "com.other.music"))

        /** When **/
        val withoutSession = controller.playbackState()
        val observableWithAccess = controller.canObserve()
        setNotificationAccess(false)
        every { sessionManager.getActiveSessions(any()) } returns
            listOf(session(PlaybackState.STATE_PLAYING))
        val withoutAccess = controller.playbackState()
        val observableWithoutAccess = controller.canObserve()

        /** Then **/
        assertEquals(MediaPlaybackState.OTHER, withoutSession)
        assertEquals(MediaPlaybackState.OTHER, withoutAccess)
        assertTrue(observableWithAccess)
        assertFalse(observableWithoutAccess)
    }

    /**
     * 알림 접근이 있고 대상 앱 세션이 있으면 세션의 transportControls로 제어하는지 검증
     *
     * Given: 알림 접근 허용, 대상 앱 세션과 다른 앱 세션
     * When: play, pause 호출
     * Then: 대상 앱 세션 바인더에 play·pause 호출, 다른 앱 세션과 미디어 키는 호출 없음
     */
    @Test
    fun test_03_controlsTargetSessionWithAccess() = runTest {
        /** Given **/
        setNotificationAccess(true)
        val other = session(PlaybackState.STATE_PLAYING, packageName = "com.other.music")
        val target = session(PlaybackState.STATE_PAUSED)
        every { sessionManager.getActiveSessions(any()) } returns listOf(other, target)

        /** When **/
        controller.play()
        controller.pause()

        /** Then **/
        assertEquals(listOf("play", "pause"), callsOf(target))
        assertEquals(emptyList<String>(), callsOf(other))
        verify(exactly = 0) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    /**
     * 알림 접근이 있어도 대상 앱 세션이 없으면 아무것도 하지 않는지 검증
     *
     * Given: 알림 접근 허용, 활성 세션 없음
     * When: play, pause 호출
     * Then: 미디어 키 전송 없음
     */
    @Test
    fun test_04_doesNothingWithAccessButNoSession() = runTest {
        /** Given **/
        setNotificationAccess(true)

        /** When **/
        controller.play()
        controller.pause()

        /** Then **/
        verify(exactly = 0) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    /**
     * 알림 접근이 없으면 미디어 키로 재생·일시정지하는지 검증
     *
     * Given: 알림 접근 없음
     * When: play, pause 호출
     * Then: PLAY의 DOWN·UP, PAUSE의 DOWN·UP 순으로 미디어 키 전송, 세션 조회 없음
     */
    @Test
    fun test_05_dispatchesMediaKeysWithoutAccess() = runTest {
        /** Given **/
        setNotificationAccess(false)
        val events = mutableListOf<Pair<Int, Int>>()
        every { audioManager.dispatchMediaKeyEvent(any()) } answers {
            val event = firstArg<KeyEvent>()
            events.add(event.action to event.keyCode)
        }

        /** When **/
        controller.play()
        controller.pause()

        /** Then **/
        assertEquals(
            listOf(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_MEDIA_PAUSE,
            ),
            events,
        )
        verify(exactly = 0) { sessionManager.getActiveSessions(any()) }
    }

    /**
     * canObserve가 생성 때 찾은 알림 리스너로 허용 목록만 확인하고 매니페스트를 다시 조회하지 않는지 검증
     *
     * Given: 알림 접근 허용, 생성 때 리스너 조회를 마친 컨트롤러
     * When: canObserve 세 번 호출
     * Then: 모두 true, packageManager 접근은 생성 때 한 번뿐
     */
    @Test
    fun test_06_canObserveReusesListenerFoundAtCreation() = runTest {
        /** Given **/
        setNotificationAccess(true)

        /** When **/
        val results = List(3) { controller.canObserve() }

        /** Then **/
        assertEquals(listOf(true, true, true), results)
        verify(exactly = 1) { context.packageManager }
    }

    private companion object {
        /** 제어 대상으로 넘기는 미디어 앱 패키지명 */
        const val TARGET_PACKAGE = "com.example.player"
    }
}
