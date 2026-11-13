package com.black.app.ui.main.usagelog

import android.content.Context
import android.net.wifi.WifiManager
import com.black.app.usagelog.collect.UsageLogListenerService
import com.black.app.usagelog.collect.WifiStateTracker
import com.black.core.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [UsageLogScreen]이 보여 주는 권한과 현재 Wi-Fi 이름 조회
 *
 * 사용 정보 접근·알림 접근·위치 권한·위치 설정·배터리 최적화 확인은 [PermissionHelper]에 위임함.
 * ViewModel에 주입하기 위한 얇은 래퍼.
 */
class UsageLogSystemStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isUsageAccessGranted(): Boolean = PermissionHelper.isUsageAccessGranted(context)

    fun isNotificationAccessGranted(): Boolean =
        PermissionHelper.isNotificationListenerEnabled(context, UsageLogListenerService::class.java)

    fun isFineLocationGranted(): Boolean = PermissionHelper.isFineLocationGranted(context)

    fun isApproximateLocationOnly(): Boolean = PermissionHelper.isApproximateLocationOnly(context)

    /** API 29 미만에는 백그라운드 위치 권한이 따로 없어 항상 true */
    fun isBackgroundLocationGranted(): Boolean = PermissionHelper.isBackgroundLocationGranted(context)

    fun isLocationEnabled(): Boolean = PermissionHelper.isLocationEnabled(context)

    fun isBatteryOptimizationIgnored(): Boolean = PermissionHelper.isIgnoringBatteryOptimizations(context)

    /**
     * 지금 연결된 Wi-Fi 이름
     *
     * 연결돼 있지 않거나 위치 권한·위치 설정이 없어 읽을 수 없으면 null.
     */
    @Suppress("DEPRECATION")
    fun currentSsid(): String? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val rawSsid = try {
            wifiManager.connectionInfo?.ssid
        } catch (failure: RuntimeException) {
            null
        }
        return WifiStateTracker.normalizeSsid(rawSsid)
    }
}
