package com.black.app.usagelog.collect

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Wi-Fi 연결·해제를 감시해 기록하고, 집 Wi-Fi이면 [HomeTransitionListener]에 알리는 감시자
 *
 * 콜백 안에서는 시각만 찍어 대기열에 넣고, 저장소 상태(`lastWifiState`) 읽기·계산·저장은 단일 소비 코루틴이 도착 순서대로 처리함.
 * 기록과 알림 판정은 [WifiTransitionMachine]이 하며 `start()`마다 새로 만들어, 이전 세션의 코루틴이 새 상태를 덮어쓰지 못함.
 * 새 세션의 소비 코루틴은 이전 세션의 소비 코루틴이 끝난 뒤 처리를 시작해, 취소 중인 이전 세션의 저장과 겹치지 않음.
 * SSID는 로그에 남기지 않음(예외도 클래스 이름만).
 *
 * @see UsageLogListenerService 시작과 중지 시점
 */
class WifiMonitor(
    context: Context,
    private val settings: UsageLogSettingsRepository,
    private val repository: UsageLogRepository,
    private val scope: CoroutineScope,
    private val listener: HomeTransitionListener,
) {

    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)

    @Suppress("DEPRECATION")
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var events: Channel<WifiEvent>? = null
    private var jobs: List<Job> = emptyList()

    /**
     * 마지막으로 시작한 세션의 소비 코루틴
     *
     * 다음 세션의 소비 코루틴이 이 코루틴이 끝나기를 기다림.
     */
    private var consumerJob: Job? = null

    /**
     * 감시 시작
     *
     * 이미 시작했으면 무시.
     */
    fun start() {
        if (callback != null) return
        val queue = Channel<WifiEvent>(Channel.UNLIMITED)
        // 콜백 보고보다 먼저 처리해, 서비스가 멈춘 사이의 변화를 저장된 옛 상태 기준의 추정 기록으로 먼저 맞춤
        queue.trySend(WifiEvent.CollectionEnabled(System.currentTimeMillis()))
        events = queue

        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        // 등록 전에 이미 연결된 Wi-Fi 핸들만 읽음(SSID는 판단에 쓰지 않음)
        val initialHandles = readWifiHandles(request)
        val machine = WifiTransitionMachine(initialHandles)

        val networkCallback = createCallback(CallbackSession(queue))
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (failure: RuntimeException) {
            // 권한 없음 등으로 등록이 막혀도 서비스의 알림 기록은 계속 동작해야 함
            Log.w("[WifiMonitor] 네트워크 콜백 등록 실패: ${failure::class.java.simpleName}")
            queue.close()
            events = null
            return
        }
        callback = networkCallback

        val previousConsumer = consumerJob
        val consumer = scope.launch {
            // 이전 세션이 저장을 마친 뒤 저장된 상태를 읽도록 기다림
            previousConsumer?.join()
            // 단일 소비자라 연결·해제와 수집 켜짐이 도착 순서대로 처리됨
            for (event in queue) {
                // 중지된 세션의 남은 이벤트가 저장소를 건드리지 않도록 확인
                ensureActive()
                try {
                    handle(machine, event)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // 한 건의 실패로 감시 전체가 멈추지 않도록 다음 이벤트로 넘어감
                    Log.w("[WifiMonitor] Wi-Fi 이벤트 처리 실패: ${failure::class.java.simpleName}")
                }
            }
        }
        consumerJob = consumer

        val startedJobs = mutableListOf(consumer)
        startedJobs += scope.launch {
            settings.collectionEnabled
                .retryAfterDelay("수집 설정")
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) queue.trySend(WifiEvent.CollectionEnabled(System.currentTimeMillis()))
                }
        }
        startedJobs += scope.launch {
            // 첫 값도 넣지만, 판정기가 마지막 기록에 쓴 집 SSID와 비교하므로 바뀌지 않았으면 기록하지 않음
            settings.homeSsids
                .retryAfterDelay("집 SSID")
                .distinctUntilChanged()
                .collect { queue.trySend(WifiEvent.HomesChanged(System.currentTimeMillis())) }
        }
        if (initialHandles.isNotEmpty()) {
            startedJobs += scope.launch {
                // 초기 네트워크의 첫 보고가 끝내 오지 않아도 수집 켜짐 처리가 막히지 않도록 함
                delay(INITIAL_REPORT_TIMEOUT_MILLIS)
                queue.trySend(WifiEvent.InitialTimeout(System.currentTimeMillis()))
            }
        }
        jobs = startedJobs
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
                Log.w("[WifiMonitor] 네트워크 콜백 해제 실패: ${failure::class.java.simpleName}")
            }
        }
        callback = null
        events?.close()
        events = null
        jobs.forEach { it.cancel() }
        jobs = emptyList()
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
        Log.w("[WifiMonitor] 초기 Wi-Fi 네트워크 읽기 실패: ${failure::class.java.simpleName}")
        emptySet()
    }

    private fun createCallback(session: CallbackSession): ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    session.reportConnected(network) { readSsidFromTransportInfo(capabilities) }
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
                        session.reportConnected(network) { readSsidFromWifiManager() }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    session.reportConnected(network) { readSsidFromWifiManager() }
                }

                override fun onLost(network: Network) {
                    session.reportLost(network)
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readSsidFromTransportInfo(capabilities: NetworkCapabilities): String? =
        (capabilities.transportInfo as? WifiInfo)?.ssid

    @Suppress("DEPRECATION")
    private fun readSsidFromWifiManager(): String? = try {
        wifiManager?.connectionInfo?.ssid
    } catch (failure: RuntimeException) {
        null
    }

    private suspend fun handle(machine: WifiTransitionMachine, event: WifiEvent) {
        val enabled = settings.isCollectionEnabled()
        val collectionContext = if (enabled) {
            WifiCollectionContext(settings.getLastWifiState(), settings.getHomeSsids())
        } else {
            null
        }
        val decision = when (event) {
            is WifiEvent.Reported -> machine.onReported(event.handle, event.ssid, event.time, collectionContext)
            is WifiEvent.Lost -> machine.onLost(event.handle, event.time, collectionContext)
            is WifiEvent.CollectionEnabled -> machine.onCollectionEnabled(event.time, collectionContext)
            is WifiEvent.InitialTimeout -> machine.onInitialTimeout(event.time, collectionContext)
            is WifiEvent.HomesChanged -> machine.onHomesChanged(event.time, collectionContext)
        }
        apply(decision)
    }

    /** 기록을 저장한 뒤 마지막 상태를 갱신하고, 집 도착·출발은 리스너에 알림 */
    private suspend fun apply(decision: WifiDecision) {
        if (decision.records.isNotEmpty()) repository.add(decision.records)
        decision.newPersisted?.let { state ->
            settings.setLastWifiState(state).exceptionOrNull()?.let {
                Log.w("[WifiMonitor] Wi-Fi 상태 저장 실패: ${it::class.java.simpleName}")
            }
        }
        decision.homeTransitions.forEach { arrived ->
            try {
                listener.onHomeTransition(arrived)
            } catch (failure: Exception) {
                // 전송 예약 실패가 다음 알림과 감시를 막지 않도록 클래스 이름만 기록
                Log.w("[WifiMonitor] 집 전환 알림 실패: ${failure::class.java.simpleName}")
            }
        }
    }

    /** 읽기 예외로 흐름이 끝나 이후 변화를 놓치지 않도록 잠시 뒤 다시 구독 */
    private fun <T> Flow<T>.retryAfterDelay(name: String): Flow<T> = retry { cause ->
        Log.w("[WifiMonitor] $name 읽기 실패: ${cause::class.java.simpleName}")
        delay(SETTINGS_RETRY_DELAY_MILLIS)
        true
    }

    /**
     * 감시 세션 하나의 콜백이 쓰는 대기열과 보고한 네트워크 핸들
     *
     * `start()`마다 새로 만들어, 해제 뒤 늦게 도착한 이전 세션의 콜백이 새 세션의 보고 여부를 바꾸지 못함.
     */
    private class CallbackSession(private val queue: Channel<WifiEvent>) {

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
         * 네트워크의 첫 보고와, SSID가 보이지 않았던 네트워크의 이후 보고를 대기열에 넣음
         *
         * 중복 확인을 SSID 읽기보다 앞에 둠(API 31 미만은 읽을 때마다 시스템 호출).
         */
        fun reportConnected(network: Network, readRawSsid: () -> String?) {
            val time = System.currentTimeMillis()
            val handle = network.networkHandle
            if (handle in resolvedHandles) return
            val ssid = WifiStateTracker.normalizeSsid(readRawSsid())
            val firstReport = reportedHandles.add(handle)
            if (ssid != null) resolvedHandles.add(handle)
            // 이미 보고했고 SSID도 여전히 안 보이면 보낼 것이 없음
            if (!firstReport && ssid == null) return
            queue.trySend(WifiEvent.Reported(handle, ssid, time))
        }

        fun reportLost(network: Network) {
            val time = System.currentTimeMillis()
            val handle = network.networkHandle
            reportedHandles.remove(handle)
            resolvedHandles.remove(handle)
            queue.trySend(WifiEvent.Lost(handle, time))
        }
    }

    /**
     * 소비 코루틴이 처리하는 Wi-Fi 이벤트
     *
     * 시각은 발생 지점에서 찍음.
     */
    private sealed interface WifiEvent {
        class Reported(val handle: Long, val ssid: String?, val time: Long) : WifiEvent
        class Lost(val handle: Long, val time: Long) : WifiEvent
        class CollectionEnabled(val time: Long) : WifiEvent
        class InitialTimeout(val time: Long) : WifiEvent
        class HomesChanged(val time: Long) : WifiEvent
    }

    companion object {
        private const val SETTINGS_RETRY_DELAY_MILLIS = 5_000L
        private const val INITIAL_REPORT_TIMEOUT_MILLIS = 5_000L

        /**
         * 네트워크가 콜백 요청을 만족하는지 판정
         *
         * API 30 미만은 [NetworkRequest.canBeSatisfiedBy]가 없어, 요청의 기본 조건인 제한 없음·신뢰·VPN 아님과 Wi-Fi 전송으로 판정함.
         */
        @VisibleForTesting
        internal fun matchesRequest(request: NetworkRequest, capabilities: NetworkCapabilities?): Boolean {
            if (capabilities == null) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return request.canBeSatisfiedBy(capabilities)
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
    }
}
