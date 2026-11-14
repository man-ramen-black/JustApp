package com.black.app.usagelog.record

import kotlinx.serialization.Serializable

/**
 * 아직 끝나지 않은 앱 사용 구간
 *
 * DataStore에 JSON 문자열로 저장됨. pausedAt은 이 앱의 마지막 ACTIVITY_PAUSED 시각으로, 같은 앱이 다시 실행되면 지워지고
 * 다른 앱 실행이나 화면 꺼짐으로 구간을 닫을 때 끝 시각이 됨.
 */
@Serializable
data class OpenUsageSession(val packageName: String, val start: Long, val pausedAt: Long? = null)

/**
 * 마지막으로 확인한 Wi-Fi 연결 상태
 *
 * DataStore에 JSON 문자열로 저장됨.
 */
@Serializable
data class WifiState(val connected: Boolean, val ssid: String? = null)
