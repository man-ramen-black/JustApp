package com.black.core.wifi

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * [WifiConnectionMonitor]의 감시 세션 수명 주기(세션 열기, 콜백 등록, 등록 실패와 중지 때 닫기)와 초기 네트워크 거르기 검증
 *
 * core 유닛 테스트는 매니페스트 targetSdk가 없어 Robolectric 기본 SDK가 19(NetworkRequest 없음)이므로 34로 지정.
 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WifiConnectionMonitorTest : BaseTest() {

    /** 세션 열기, 콜백 등록·해제, 세션 콜백이 일어난 순서 */
    private val steps = mutableListOf<String>()

    /** 콜백 등록·해제를 [steps]에 남기는 목업 연결 관리자, 연결된 네트워크는 없음 */
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true).also { manager ->
        every { manager.allNetworks } returns emptyArray()
        every { manager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) } answers {
            steps += "register"
        }
        every { manager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) } answers {
            steps += "unregister"
        }
    }

    /** [connectivityManager]를 연결 서비스로 돌려주는 애플리케이션 컨텍스트 */
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getApplicationContext(): Context = this

        override fun getSystemService(name: String): Any? =
            if (name == Context.CONNECTIVITY_SERVICE) connectivityManager else super.getSystemService(name)
    }

    /** 여는 순번과 받은 연결 핸들, 세션 콜백을 [steps]에 남기는 세션 열기 함수 */
    private fun recordingSessionOpener(): (Set<Long>) -> WifiConnectionSession {
        var sessionCount = 0
        return { connectedHandles ->
            sessionCount += 1
            val name = "session$sessionCount"
            steps += "open $name $connectedHandles"
            object : WifiConnectionSession {
                override fun onRegistered() {
                    steps += "registered $name"
                }

                override fun onConnected(handle: Long, ssid: String?, time: Long) {
                    steps += "connected $name $handle"
                }

                override fun onLost(handle: Long, time: Long) {
                    steps += "lost $name $handle"
                }

                override fun onClosed() {
                    steps += "closed $name"
                }
            }
        }
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            shadow.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
        }
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
        assertTrue(WifiConnectionMonitor.matchesRequest(request, capabilities(NetworkCapabilities.TRANSPORT_WIFI)))
        assertFalse(WifiConnectionMonitor.matchesRequest(request, vpnOverWifi()))
        assertFalse(WifiConnectionMonitor.matchesRequest(request, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR)))
        assertFalse(WifiConnectionMonitor.matchesRequest(request, null))
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
        assertTrue(WifiConnectionMonitor.matchesRequest(request, capabilities(NetworkCapabilities.TRANSPORT_WIFI)))
        assertFalse(WifiConnectionMonitor.matchesRequest(request, vpnOverWifi()))
        assertFalse(WifiConnectionMonitor.matchesRequest(request, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR)))
    }

    /**
     * 시작하면 세션을 연 뒤 콜백을 등록하고 onRegistered를 부르며, 이미 시작했으면 무시하는지 검증
     *
     * Given: 이미 연결된 Wi-Fi 네트워크 하나
     * When: start 두 번 호출
     * Then: 세션 하나가 연결 핸들을 받아 열림, 등록, onRegistered 순서이고 두 번째 start는 아무것도 하지 않음
     */
    @Test
    fun test_03_startOpensSessionThenRegisters() = runTest {
        /** Given **/
        val network: Network = ShadowNetwork.newInstance(7)
        every { connectivityManager.allNetworks } returns arrayOf(network)
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(NetworkCapabilities.TRANSPORT_WIFI)
        val monitor = WifiConnectionMonitor(context)
        val openSession = recordingSessionOpener()

        /** When **/
        monitor.start(openSession)
        monitor.start(openSession)

        /** Then **/
        assertEquals(listOf("open session1 [${network.networkHandle}]", "register", "registered session1"), steps)
    }

    /**
     * 콜백 등록이 막히면 예외를 넘기지 않고 세션을 닫으며, 다시 시작할 수 있는지 검증
     *
     * Given: 첫 콜백 등록만 SecurityException을 던지는 연결 관리자
     * When: start, stop, start 호출
     * Then: 첫 세션은 onRegistered 없이 닫히고 stop은 해제·닫기를 하지 않음, 두 번째 세션은 등록 뒤 onRegistered
     */
    @Test
    fun test_04_registerFailureClosesSessionAndAllowsRestart() = runTest {
        /** Given **/
        var registerCount = 0
        every { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) } answers {
            registerCount += 1
            steps += "register"
            if (registerCount == 1) {
                throw SecurityException()
            }
        }
        val monitor = WifiConnectionMonitor(context)
        val openSession = recordingSessionOpener()

        /** When **/
        monitor.start(openSession)
        monitor.stop()
        monitor.start(openSession)

        /** Then **/
        assertEquals(
            listOf(
                "open session1 []",
                "register",
                "closed session1",
                "open session2 []",
                "register",
                "registered session2",
            ),
            steps,
        )
    }

    /**
     * 중지하면 등록한 콜백을 해제한 뒤 세션을 닫고, 다시 중지해도 무시하는지 검증
     *
     * Given: 시작해 등록한 감시
     * When: stop 두 번 호출
     * Then: 등록한 콜백과 같은 콜백으로 한 번 해제, 이어서 세션 한 번 닫힘
     */
    @Test
    fun test_05_stopUnregistersThenClosesSession() = runTest {
        /** Given **/
        val monitor = WifiConnectionMonitor(context)
        monitor.start(recordingSessionOpener())
        steps.clear()

        /** When **/
        monitor.stop()
        monitor.stop()

        /** Then **/
        assertEquals(listOf("unregister", "closed session1"), steps)
        val registered = mutableListOf<ConnectivityManager.NetworkCallback>()
        val unregistered = mutableListOf<ConnectivityManager.NetworkCallback>()
        verify { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), capture(registered)) }
        verify { connectivityManager.unregisterNetworkCallback(capture(unregistered)) }
        assertSame(registered.single(), unregistered.single())
    }

    /**
     * 콜백 해제가 던져도 세션을 닫는지 검증
     *
     * Given: 시작해 등록한 감시, 해제가 IllegalArgumentException을 던지는 연결 관리자
     * When: stop 호출
     * Then: 해제 시도 뒤 세션 닫힘
     */
    @Test
    fun test_06_unregisterFailureStillClosesSession() = runTest {
        /** Given **/
        every { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) } answers {
            steps += "unregister"
            throw IllegalArgumentException()
        }
        val monitor = WifiConnectionMonitor(context)
        monitor.start(recordingSessionOpener())
        steps.clear()

        /** When **/
        monitor.stop()

        /** Then **/
        assertEquals(listOf("unregister", "closed session1"), steps)
    }

    private companion object {
        /** `NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED`가 시스템 API라 앱에서 쓸 수 없어 값을 복사 */
        const val NET_CAPABILITY_NOT_VCN_MANAGED = 28
    }
}
