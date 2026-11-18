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

/**
 * 집 도착·출발 알림 한 건
 *
 * 추정 기록은 그 시각에 실제로 연결·해제된 것이 아니라 나중에 알게 된 상태를 맞춘 기록이며, 다음 경우에 생김.
 * - 감시를 시작하거나 수집을 켜면, 서비스가 멈췄거나 수집이 꺼진 사이의 변화를 저장된 마지막 상태와 지금 상태의 차이로 맞춤.
 *   감시 시작 전부터 연결돼 있던 네트워크는 그 첫 보고, 보고 없이 끊김, 보고 대기 만료 때 맞춤
 * - 지금 연결의 첫 보고에서 SSID가 null이었다가 다음 보고에서 보이면 그 SSID로 연결을 다시 기록함.
 *   위치 권한을 나중에 허용한 경우 등이며 도착만 생김
 * - 집 SSID를 새로 등록하거나 지워 지금 연결의 집 여부가 바뀌면 도착이나 출발을 기록함
 *
 * @property arrived 도착이면 true, 출발이면 false
 * @property inferred 위 경우의 추정 기록에서 나온 알림이면 true
 * @property time 기록의 시각(epoch 밀리초)
 * @see WifiTransitionMachine 추정 기록을 정하는 판정기
 */
data class HomeTransition(val arrived: Boolean, val inferred: Boolean, val time: Long)

/**
 * 집 도착·출발 알림 수신자
 *
 * 집 Wi-Fi 연결이 도착, 해제가 출발.
 */
fun interface HomeTransitionListener {
    fun onHomeTransition(transition: HomeTransition)
}
