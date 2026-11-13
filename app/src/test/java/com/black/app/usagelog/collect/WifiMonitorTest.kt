package com.black.app.usagelog.collect

import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/** [WifiMonitor.matchesRequest]의 초기 네트워크 거르기 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WifiMonitorTest : BaseTest() {

    /** 감시와 같은 Wi-Fi 콜백 요청 */
    private fun wifiRequest(): NetworkRequest =
        NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()

    /**
     * 기본 조건을 가진 네트워크 능력
     *
     * API 31 이상의 요청은 VCN 관리 아님도 요구하므로 일반 Wi-Fi처럼 함께 둠.
     */
    private fun capabilities(vararg transports: Int): NetworkCapabilities {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        val shadow = shadowOf(capabilities)
        transports.forEach { shadow.addTransportType(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) shadow.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
        return capabilities
    }

    /** Wi-Fi 위의 VPN 네트워크 능력 */
    private fun vpnOverWifi(): NetworkCapabilities =
        capabilities(NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_WIFI).also {
            shadowOf(it).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }

    /**
     * 콜백 요청이 받는 Wi-Fi만 초기 네트워크로 보는지 검증
     *
     * Given: 일반 Wi-Fi, Wi-Fi 위의 VPN, 모바일 데이터 네트워크 능력과 능력 없음
     * When: matchesRequest 호출
     * Then: 일반 Wi-Fi만 true
     */
    @Test
    fun test_01_matchesOnlyRequestedWifi() = runTest {
        /** Given **/
        val request = wifiRequest()

        /** When / Then **/
        assertTrue(WifiMonitor.matchesRequest(request, capabilities(NetworkCapabilities.TRANSPORT_WIFI)))
        assertFalse(WifiMonitor.matchesRequest(request, vpnOverWifi()))
        assertFalse(WifiMonitor.matchesRequest(request, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR)))
        assertFalse(WifiMonitor.matchesRequest(request, null))
    }

    /**
     * API 30 미만에서도 요청의 기본 조건으로 같은 결과를 내는지 검증
     *
     * Given: API 29, 일반 Wi-Fi, Wi-Fi 위의 VPN, 모바일 데이터 네트워크 능력
     * When: matchesRequest 호출
     * Then: 일반 Wi-Fi만 true
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun test_02_matchesOnlyRequestedWifiBeforeR() = runTest {
        /** Given **/
        val request = wifiRequest()

        /** When / Then **/
        assertTrue(WifiMonitor.matchesRequest(request, capabilities(NetworkCapabilities.TRANSPORT_WIFI)))
        assertFalse(WifiMonitor.matchesRequest(request, vpnOverWifi()))
        assertFalse(WifiMonitor.matchesRequest(request, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR)))
    }

    private companion object {
        /** `NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED`가 시스템 API라 앱에서 쓸 수 없어 값을 복사 */
        const val NET_CAPABILITY_NOT_VCN_MANAGED = 28
    }
}
