package com.black.core.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.black.core.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * [WifiConnectionMonitor]의 감시 세션 하나가 보고를 넘기는 대상
 *
 * [WifiConnectionMonitor.start]마다 새로 받아, 해제 뒤 늦게 도착한 이전 세션의 콜백 보고가 새 세션에 섞이지 않음.
 * [onConnected]·[onLost]는 시스템 네트워크 콜백 스레드에서 불리므로 무거운 처리는 다른 코루틴으로 넘겨야 함.
 */
interface WifiConnectionSession {

    /** 네트워크 콜백 등록 성공 직후 한 번 호출 */
    fun onRegistered()

    /**
     * 네트워크의 첫 보고와, SSID가 보이지 않았던 네트워크에서 SSID가 처음 보인 보고
     *
     * 콜백 스레드에서 호출.
     * [ssid]는 [WifiSsid.normalize]로 정리한 값이며 위치 권한·위치 설정이 없어 읽지 못하면 null.
     * [time]은 콜백이 도착한 시각.
     */
    fun onConnected(handle: Long, ssid: String?, time: Long)

    /**
     * 네트워크 끊김
     *
     * 콜백 스레드에서 호출하며 [time]은 콜백이 도착한 시각.
     */
    fun onLost(handle: Long, time: Long)

    /**
     * 세션 끝
     *
     * [WifiConnectionMonitor.stop] 또는 콜백 등록 실패 시 한 번 호출.
     */
    fun onClosed()
}

/**
 * Wi-Fi 네트워크의 연결·해제 감시
 *
 * 시스템 네트워크 콜백으로 받은 Wi-Fi 네트워크마다의 연결과 끊김을 [WifiConnectionSession]에 넘김.
 * 같은 네트워크의 반복 보고는 거르고, SSID가 보이지 않았던 네트워크만 SSID가 처음 보일 때 다시 넘김.
 * API 31 이상은 위치 정보를 포함한 콜백의 전송 정보에서, 그 미만은 [WifiSsid.current]로 SSID를 읽음.
 * SSID는 로그에 남기지 않음(예외도 클래스 이름만).
 */
class WifiConnectionMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var session: WifiConnectionSession? = null

    /**
     * 감시 시작
     *
     * 이미 시작했으면 무시.
     * 등록 전에 이미 연결된 Wi-Fi 네트워크 핸들로 [openSession]을 불러 세션을 연 뒤 콜백을 등록함.
     * 등록에 실패하면 [WifiConnectionSession.onClosed], 성공하면 [WifiConnectionSession.onRegistered]를 부름.
     */
    fun start(openSession: (connectedHandles: Set<Long>) -> WifiConnectionSession) {
        if (callback != null) {
            return
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        // 등록 전에 이미 연결된 Wi-Fi 핸들만 읽음(SSID는 판단에 쓰지 않음)
        val connectedHandles = readWifiHandles(request)
        val newSession = openSession(connectedHandles)

        val networkCallback = createCallback(CallbackSession(newSession))
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (failure: RuntimeException) {
            // 권한 없음 등으로 등록이 막혀도 호출한 쪽(알림 기록 등)은 계속 동작하도록 예외를 넘기지 않고 세션만 닫음
            Log.w("[WifiConnectionMonitor] 네트워크 콜백 등록 실패: ${failure::class.java.simpleName}")
            newSession.onClosed()
            return
        }
        callback = networkCallback
        session = newSession
        newSession.onRegistered()
    }

    /**
     * 감시 중지
     *
     * 시작하지 않았으면 무시.
     */
    fun stop() {
        callback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (failure: RuntimeException) {
                Log.w("[WifiConnectionMonitor] 네트워크 콜백 해제 실패: ${failure::class.java.simpleName}")
            }
        }
        callback = null
        session?.onClosed()
        session = null
    }

    /**
     * 콜백 요청을 만족하는 현재 네트워크 핸들
     *
     * Wi-Fi 위의 VPN처럼 Wi-Fi 전송을 함께 가졌어도 요청이 받지 않는 네트워크는 첫 보고가 오지 않으므로 뺌.
     */
    @Suppress("DEPRECATION")
    private fun readWifiHandles(request: NetworkRequest): Set<Long> = try {
        connectivityManager.allNetworks
            .filter { network -> matchesRequest(request, connectivityManager.getNetworkCapabilities(network)) }
            .map { it.networkHandle }
            .toSet()
    } catch (failure: RuntimeException) {
        Log.w("[WifiConnectionMonitor] 초기 Wi-Fi 네트워크 읽기 실패: ${failure::class.java.simpleName}")
        emptySet()
    }

    private fun createCallback(session: CallbackSession): ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    session.reportConnected(network) { WifiSsid.normalize(readSsidFromTransportInfo(capabilities)) }
                }

                override fun onLost(network: Network) {
                    session.reportLost(network)
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // API 26 미만은 onAvailable 뒤 onCapabilitiesChanged가 오지 않을 수 있음
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        session.reportConnected(network) { WifiSsid.current(appContext) }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    session.reportConnected(network) { WifiSsid.current(appContext) }
                }

                override fun onLost(network: Network) {
                    session.reportLost(network)
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readSsidFromTransportInfo(capabilities: NetworkCapabilities): String? =
        (capabilities.transportInfo as? WifiInfo)?.ssid

    /**
     * 감시 세션 하나의 콜백이 보고한 네트워크 핸들과 보고를 넘길 세션
     *
     * `start()`마다 새로 만들어, 해제 뒤 늦게 도착한 이전 세션의 콜백이 새 세션의 보고 여부를 바꾸지 못함.
     */
    private class CallbackSession(private val session: WifiConnectionSession) {

        /**
         * 연결로 이미 보고한 네트워크 핸들
         *
         * 콜백 스레드에서 접근.
         */
        private val reportedHandles: MutableSet<Long> = ConcurrentHashMap.newKeySet()

        /**
         * SSID까지 읽어 보고한 네트워크 핸들
         *
         * SSID가 보이지 않았던 네트워크만 다시 읽기 위함.
         */
        private val resolvedHandles: MutableSet<Long> = ConcurrentHashMap.newKeySet()

        /**
         * 네트워크의 첫 보고와, SSID가 보이지 않았던 네트워크의 이후 보고를 세션에 넘김
         *
         * 중복 확인을 SSID 읽기보다 앞에 둠(API 31 미만은 읽을 때마다 시스템 호출).
         * [readSsid]는 [WifiSsid.normalize]로 정리한 SSID를 돌려줌.
         */
        fun reportConnected(network: Network, readSsid: () -> String?) {
            val time = System.currentTimeMillis()
            val handle = network.networkHandle
            if (handle in resolvedHandles) {
                return
            }
            val ssid = readSsid()
            val firstReport = reportedHandles.add(handle)
            if (ssid != null) {
                resolvedHandles.add(handle)
            }
            // 이미 보고했고 SSID도 여전히 안 보이면 보낼 것이 없음
            if (!firstReport && ssid == null) {
                return
            }
            session.onConnected(handle, ssid, time)
        }

        fun reportLost(network: Network) {
            val time = System.currentTimeMillis()
            val handle = network.networkHandle
            reportedHandles.remove(handle)
            resolvedHandles.remove(handle)
            session.onLost(handle, time)
        }
    }

    companion object {

        /**
         * 네트워크가 콜백 요청을 만족하는지 판정
         *
         * API 30 미만은 [NetworkRequest.canBeSatisfiedBy]가 없어, 요청의 기본 조건인 제한 없음·신뢰·VPN 아님과 Wi-Fi 전송으로 판정함.
         */
        @VisibleForTesting
        internal fun matchesRequest(request: NetworkRequest, capabilities: NetworkCapabilities?): Boolean {
            if (capabilities == null) {
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return request.canBeSatisfiedBy(capabilities)
            }
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
    }
}
