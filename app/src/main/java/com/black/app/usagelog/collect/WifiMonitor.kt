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
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.core.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch

/**
 * Wi-Fi 연결·해제를 감시해 기록하고, 집 Wi-Fi이면 [HomeTransitionListener]에 알리는 감시자
 *
 * 콜백 안에서는 시각만 찍어 대기열에 넣고, 저장소 상태(`lastWifiState`) 읽기·계산·저장은 단일 소비 코루틴이 도착 순서대로 처리함.
 * 기록과 알림 판정은 [WifiTransitionMachine]이 하며 `start()`마다 새로 만들어, 이전 세션의 코루틴이 새 상태를 덮어쓰지 못함.
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
     * 연결로 이미 보고한 네트워크 핸들
     *
     * 콜백 스레드와 `start()`가 접근.
     */
    private val reportedHandles: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /**
     * SSID까지 읽어 보고한 네트워크 핸들
     *
     * SSID가 보이지 않았던 네트워크만 다시 읽기 위함.
     */
    private val resolvedHandles: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /**
     * 감시 시작
     *
     * 이미 시작했으면 무시.
     */
    fun start() {
        if (callback != null) return
        val queue = Channel<WifiEvent>(Channel.UNLIMITED)
        events = queue
        reportedHandles.clear()
        resolvedHandles.clear()

        // 등록 전에 이미 연결된 Wi-Fi 핸들만 읽음(SSID는 판단에 쓰지 않음)
        val initialHandles = readWifiHandles()
        val machine = WifiTransitionMachine(initialHandles)

        val networkCallback = createCallback(queue)
        try {
            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (failure: RuntimeException) {
            // 권한 없음 등으로 등록이 막혀도 서비스의 알림 기록은 계속 동작해야 함
            Log.w("[WifiMonitor] 네트워크 콜백 등록 실패: ${failure::class.java.simpleName}")
            queue.close()
            events = null
            return
        }
        callback = networkCallback

        val startedJobs = mutableListOf<Job>()
        startedJobs += scope.launch {
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
        startedJobs += scope.launch {
            settings.collectionEnabled
                .retry { cause ->
                    Log.w("[WifiMonitor] 수집 설정 읽기 실패: ${cause::class.java.simpleName}")
                    delay(COLLECTION_RETRY_DELAY_MILLIS)
                    true
                }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) queue.trySend(WifiEvent.CollectionEnabled(System.currentTimeMillis()))
                }
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

    /** 요청과 같은 조건(Wi-Fi 전송)의 현재 네트워크 핸들 */
    @Suppress("DEPRECATION")
    private fun readWifiHandles(): Set<Long> = try {
        connectivityManager.allNetworks
            .filter { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            .map { it.networkHandle }
            .toSet()
    } catch (failure: RuntimeException) {
        Log.w("[WifiMonitor] 초기 Wi-Fi 네트워크 읽기 실패: ${failure::class.java.simpleName}")
        emptySet()
    }

    private fun createCallback(queue: Channel<WifiEvent>): ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    reportConnected(queue, network) { readSsidFromTransportInfo(capabilities) }
                }

                override fun onLost(network: Network) {
                    reportLost(queue, network)
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // API 26 미만은 onAvailable 뒤 onCapabilitiesChanged가 오지 않을 수 있음
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        reportConnected(queue, network) { readSsidFromWifiManager() }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    reportConnected(queue, network) { readSsidFromWifiManager() }
                }

                override fun onLost(network: Network) {
                    reportLost(queue, network)
                }
            }
        }

    /**
     * 네트워크의 첫 보고와, SSID가 보이지 않았던 네트워크의 이후 보고를 대기열에 넣음
     *
     * 중복 확인을 SSID 읽기보다 앞에 둠(API 31 미만은 읽을 때마다 시스템 호출).
     */
    private fun reportConnected(queue: Channel<WifiEvent>, network: Network, readRawSsid: () -> String?) {
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

    private fun reportLost(queue: Channel<WifiEvent>, network: Network) {
        val time = System.currentTimeMillis()
        val handle = network.networkHandle
        reportedHandles.remove(handle)
        resolvedHandles.remove(handle)
        queue.trySend(WifiEvent.Lost(handle, time))
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
                Log.w("[WifiMonitor] 집 전환 알림 실패: ${failure::class.java.simpleName}")
            }
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
    }

    private companion object {
        const val COLLECTION_RETRY_DELAY_MILLIS = 5_000L
        const val INITIAL_REPORT_TIMEOUT_MILLIS = 5_000L
    }
}
