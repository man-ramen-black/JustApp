package com.black.core.system

import android.content.Context
import android.media.AudioManager

/** 미디어 볼륨 최대 단계 조회, 단계 적용과 한 단계 올리기·내리기 */
class MediaVolumeController(context: Context) {
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * 기기의 미디어 볼륨 최대 단계
     *
     * 기기마다 값이 다름
     */
    fun maxLevel(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /**
     * 미디어 볼륨을 [level]로 적용
     *
     * 0부터 [maxLevel]까지만 허용하며 범위 밖이면 가까운 끝값으로 적용
     */
    fun apply(level: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, maxLevel()), 0)
    }

    /**
     * 미디어 볼륨 한 단계 올리기
     *
     * 시스템 볼륨 표시를 함께 띄움
     */
    fun raise() {
        adjust(AudioManager.ADJUST_RAISE)
    }

    /**
     * 미디어 볼륨 한 단계 내리기
     *
     * 시스템 볼륨 표시를 함께 띄움
     */
    fun lower() {
        adjust(AudioManager.ADJUST_LOWER)
    }

    private fun adjust(direction: Int) {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }
}
