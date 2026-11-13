package com.black.app.usagelog.collect

import com.black.app.usagelog.record.WifiConnectedRecord
import com.black.app.usagelog.record.WifiRecord
import com.black.app.usagelog.record.WifiState

/**
 * 수집이 켜져 있을 때 판정에 필요한 저장소 값
 *
 * @property persisted 저장된 마지막 Wi-Fi 상태
 * @property homes 등록된 집 SSID
 */
class WifiCollectionContext(val persisted: WifiState, val homes: Set<String>)

/**
 * 이벤트 하나의 판정 결과
 *
 * @property records 저장할 기록
 * @property homeTransitions 집 도착(true)·출발(false) 알림 순서(추정 기록은 제외)
 * @property newPersisted 저장할 마지막 상태(저장하지 않으면 null)
 */
class WifiDecision(
    val records: List<WifiRecord>,
    val homeTransitions: List<Boolean>,
    val newPersisted: WifiState?,
) {
    companion object {
        val NONE = WifiDecision(emptyList(), emptyList(), null)
    }
}

/**
 * Wi-Fi 이벤트와 추적 상태로 기록, 집 전환 알림, 다음 저장 상태를 정하는 순수 판정기
 *
 * [WifiMonitor]가 `start()`마다 새로 만들어 소비 코루틴 하나만 부름. 현재 상태는 수집 여부와 관계없이 갱신하고,
 * 판정 결과는 컨텍스트(수집 켜짐일 때만 넘김)가 있을 때만 냄.
 *
 * 등록 전에 이미 연결돼 있던 네트워크(initialHandles)의 첫 보고는 서비스가 멈춘 사이의 변화일 수 있어
 * 실제 기록이 아니라 [WifiStateTracker.reconcile]의 추정 기록으로 처리함.
 * 그동안 수집 켜짐 이벤트는 첫 보고가 대신하므로 건너뜀.
 */
class WifiTransitionMachine(initialHandles: Set<Long>) {

    /**
     * 살아 있는 네트워크 핸들과 SSID
     *
     * 연결 순서 유지.
     */
    private val alive = LinkedHashMap<Long, String?>()
    private val pendingInitial = initialHandles.toMutableSet()
    private var currentHandle: Long? = null

    /** 현재 Wi-Fi 상태 */
    var currentState = WifiState(connected = false)
        private set

    /**
     * 네트워크의 보고
     *
     * 첫 보고, SSID가 null에서 값으로 바뀐 보고.
     */
    fun onReported(handle: Long, ssid: String?, time: Long, context: WifiCollectionContext?): WifiDecision {
        val known = alive.containsKey(handle)
        if (known && (alive[handle] != null || ssid == null)) return WifiDecision.NONE
        alive[handle] = ssid
        val wasInitial = pendingInitial.remove(handle)

        if (known) {
            // 위치 권한을 나중에 허용해 같은 네트워크의 SSID가 뒤늦게 보임
            if (handle != currentHandle) return WifiDecision.NONE
            currentState = WifiState(true, ssid)
            if (context == null) return WifiDecision.NONE
            val record = WifiConnectedRecord(time, ssid, WifiStateTracker.isHome(ssid, context.homes), inferred = true)
            return decision(listOf(record))
        }

        currentHandle = handle
        currentState = WifiState(true, ssid)
        if (context == null) return WifiDecision.NONE
        val records = if (wasInitial) {
            WifiStateTracker.reconcile(context.persisted, currentState, context.homes, time)
        } else {
            WifiStateTracker.onConnected(context.persisted, ssid, context.homes, time)
        }
        return decision(records)
    }

    /**
     * 네트워크의 끊김
     *
     * 남은 네트워크가 있으면 그쪽으로 연결을 이어 처리.
     */
    fun onLost(handle: Long, time: Long, context: WifiCollectionContext?): WifiDecision {
        val wasInitialPending = pendingInitial.remove(handle)
        val wasAlive = alive.containsKey(handle)
        alive.remove(handle)

        if (wasAlive && handle == currentHandle) {
            val remaining = alive.entries.lastOrNull()
            val records = if (remaining != null) {
                currentHandle = remaining.key
                currentState = WifiState(true, remaining.value)
                context?.let { WifiStateTracker.onConnected(it.persisted, remaining.value, it.homes, time) }
            } else {
                currentHandle = null
                currentState = WifiState(connected = false)
                context?.let { WifiStateTracker.onDisconnected(it.persisted, it.homes, time) }
            }
            return if (context == null || records == null) WifiDecision.NONE else decision(records)
        }
        // 보고도 못 하고 사라진 초기 네트워크가 마지막 대기분이면 대기를 끝내고 맞춤
        return if (wasInitialPending && pendingInitial.isEmpty()) reconcileNow(time, context) else WifiDecision.NONE
    }

    /**
     * 수집 켜짐
     *
     * 초기 네트워크의 첫 보고를 기다리는 중이면 건너뜀.
     */
    fun onCollectionEnabled(time: Long, context: WifiCollectionContext?): WifiDecision =
        if (pendingInitial.isNotEmpty()) WifiDecision.NONE else reconcileNow(time, context)

    /** 초기 네트워크의 첫 보고를 기다리는 제한 시간 만료 */
    fun onInitialTimeout(time: Long, context: WifiCollectionContext?): WifiDecision {
        if (pendingInitial.isEmpty()) return WifiDecision.NONE
        pendingInitial.clear()
        return reconcileNow(time, context)
    }

    private fun reconcileNow(time: Long, context: WifiCollectionContext?): WifiDecision {
        if (context == null) return WifiDecision.NONE
        return decision(WifiStateTracker.reconcile(context.persisted, currentState, context.homes, time))
    }

    private fun decision(records: List<WifiRecord>): WifiDecision {
        val transitions = records.filter { it.home == true && !it.inferred }.map { it is WifiConnectedRecord }
        return WifiDecision(records, transitions, currentState)
    }
}
