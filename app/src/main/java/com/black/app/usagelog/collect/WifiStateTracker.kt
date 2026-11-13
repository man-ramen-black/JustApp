package com.black.app.usagelog.collect

import com.black.app.usagelog.record.WifiConnectedRecord
import com.black.app.usagelog.record.WifiDisconnectedRecord
import com.black.app.usagelog.record.WifiRecord
import com.black.app.usagelog.record.WifiState

/**
 * Wi-Fi 상태 변화에서 기록을 계산하는 순수 규칙
 *
 * SSID를 읽지 못하면(위치 권한·위치 설정 없음) ssid와 집 여부 모두 null로 기록됨.
 */
object WifiStateTracker {

    private const val UNKNOWN_SSID = "<unknown ssid>"

    /** 시스템이 주는 SSID의 앞뒤 큰따옴표를 벗기고, 알 수 없는 값과 빈 문자열은 null로 바꿈 */
    fun normalizeSsid(raw: String?): String? {
        val unquoted = raw?.removeSurrounding("\"")
        return unquoted?.takeUnless { it.isEmpty() || it == UNKNOWN_SSID }
    }

    /**
     * SSID가 등록된 집 Wi-Fi인지 판정
     *
     * SSID를 모르면 null.
     */
    fun isHome(ssid: String?, homeSsids: Set<String>): Boolean? = ssid?.let { it in homeSsids }

    /**
     * 연결 시 기록
     *
     * 이미 같은 SSID로 연결돼 있으면 없고, 다른 SSID였으면 해제 뒤 연결 순서.
     */
    fun onConnected(
        previous: WifiState,
        ssid: String?,
        homeSsids: Set<String>,
        time: Long,
        inferred: Boolean = false,
    ): List<WifiRecord> {
        if (previous.connected && previous.ssid == ssid) return emptyList()
        val connected = WifiConnectedRecord(time, ssid, isHome(ssid, homeSsids), inferred)
        return onDisconnected(previous, homeSsids, time, inferred) + connected
    }

    /**
     * 해제 시 기록
     *
     * 연결돼 있지 않았으면 없음.
     */
    fun onDisconnected(
        previous: WifiState,
        homeSsids: Set<String>,
        time: Long,
        inferred: Boolean = false,
    ): List<WifiRecord> {
        if (!previous.connected) return emptyList()
        return listOf(WifiDisconnectedRecord(time, previous.ssid, isHome(previous.ssid, homeSsids), inferred))
    }

    /** 저장된 상태와 현재 상태의 차이를 추정 기록으로 메움 */
    fun reconcile(
        persisted: WifiState,
        current: WifiState,
        homeSsids: Set<String>,
        time: Long,
    ): List<WifiRecord> =
        if (current.connected) {
            onConnected(persisted, current.ssid, homeSsids, time, inferred = true)
        } else {
            onDisconnected(persisted, homeSsids, time, inferred = true)
        }
}

/** 집 Wi-Fi 연결(도착)·해제(출발) 알림 수신자 */
fun interface HomeTransitionListener {
    fun onHomeTransition(arrived: Boolean)
}
