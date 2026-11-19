package com.black.feature.usagelog.model

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

/**
 * 문자 수집 진행 상태
 *
 * DataStore에 JSON 문자열로 저장됨.
 *
 * @property floor 이 시각 전 문자는 읽지 않음(수집을 켠 시각이나 문자를 처음 읽은 시각)
 * @property cursor 마지막으로 읽은 구간의 끝
 * @property seenKeys 마지막으로 읽은 구간에서 이미 기록한 문자 키
 */
@Serializable
data class SmsProgress(val floor: Long, val cursor: Long, val seenKeys: Set<String> = emptySet())
