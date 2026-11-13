package com.black.app.usagelog.record

import kotlinx.serialization.Serializable

/**
 * 아직 끝나지 않은 앱 사용 구간
 *
 * DataStore에 JSON 문자열로 저장됨. pausedAt은 화면이 꺼져 구간이 멈춘 시각.
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
