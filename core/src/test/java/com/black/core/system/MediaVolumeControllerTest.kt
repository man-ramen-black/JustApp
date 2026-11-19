package com.black.core.system

import android.content.Context
import android.media.AudioManager
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [MediaVolumeController]의 최대 단계 조회, 볼륨 적용(범위 보정 포함), 한 단계 올리기·내리기 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MediaVolumeControllerTest : BaseTest() {

    private lateinit var audioManager: AudioManager
    private lateinit var controller: MediaVolumeController

    @Before
    fun setupController() {
        audioManager = mockk(relaxed = true)
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 15
        val context = mockk<Context> {
            every { getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        }
        controller = MediaVolumeController(context)
    }

    /**
     * 최대 단계가 기기의 미디어 볼륨 최대값인지 검증
     *
     * Given: STREAM_MUSIC 최대 단계가 15인 AudioManager
     * When: maxLevel 호출
     * Then: 15
     */
    @Test
    fun test_01_maxLevelMatchesAudioManager() = runTest {
        /** Given / When **/
        val maxLevel = controller.maxLevel()

        /** Then **/
        assertEquals(15, maxLevel)
    }

    /**
     * 범위 안의 값은 그대로 적용되는지 검증
     *
     * Given: 최대 단계 15
     * When: apply(6)
     * Then: setStreamVolume(STREAM_MUSIC, 6, 0)
     */
    @Test
    fun test_02_applyLevel() = runTest {
        /** Given / When **/
        controller.apply(6)

        /** Then **/
        verify(exactly = 1) { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 6, 0) }
    }

    /**
     * 범위 밖의 값은 가까운 끝값으로 적용되는지 검증
     *
     * Given: 최대 단계 15
     * When: apply(100), apply(-3)
     * Then: setStreamVolume이 15, 0으로 호출됨
     */
    @Test
    fun test_03_applyClampsOutOfRange() = runTest {
        /** Given / When **/
        controller.apply(100)
        controller.apply(-3)

        /** Then **/
        verify(exactly = 1) { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 15, 0) }
        verify(exactly = 1) { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
    }

    /**
     * 한 단계 올리기·내리기가 시스템 볼륨 표시와 함께 미디어 볼륨을 조절하는지 검증
     *
     * Given: 최대 단계 15
     * When: raise, lower 호출
     * Then: adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE, FLAG_SHOW_UI), adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER, FLAG_SHOW_UI) 각 1회
     */
    @Test
    fun test_04_raiseAndLowerAdjustWithVolumeUi() = runTest {
        /** Given / When **/
        controller.raise()
        controller.lower()

        /** Then **/
        verify(exactly = 1) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }
    }
}
