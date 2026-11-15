package com.black.app.sleeproutine

import android.content.Context
import android.media.AudioManager

/** 미디어 볼륨 최대 단계 조회와 단계 적용 */
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
}
