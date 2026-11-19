package com.black.core.wifi

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Wi-Fi 이름(SSID) 조회와 정리
 *
 * 위치 권한이나 위치 설정이 없으면 시스템이 실제 이름 대신 알 수 없음 표기를 주므로 null로 바꿈.
 */
object WifiSsid {

    private const val UNKNOWN_SSID = "<unknown ssid>"

    /** 시스템이 주는 SSID의 앞뒤 큰따옴표를 벗기고, 알 수 없는 값과 빈 문자열은 null로 바꿈 */
    fun normalize(raw: String?): String? {
        val unquoted = raw?.removeSurrounding("\"")
        return unquoted?.takeUnless { it.isEmpty() || it == UNKNOWN_SSID }
    }

    /**
     * 지금 연결된 Wi-Fi 이름
     *
     * 연결돼 있지 않거나 위치 권한·위치 설정이 없어 읽을 수 없으면 null.
     * 시스템 서비스가 던지는 예외(권한 회수 등)도 null로 바꿈.
     */
    @Suppress("DEPRECATION")
    fun current(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val rawSsid = try {
            wifiManager.connectionInfo?.ssid
        } catch (failure: RuntimeException) {
            null
        }
        return normalize(rawSsid)
    }
}
