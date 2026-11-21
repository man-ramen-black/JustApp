package com.black.feature.usagelog.model

import kotlinx.serialization.Serializable

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
