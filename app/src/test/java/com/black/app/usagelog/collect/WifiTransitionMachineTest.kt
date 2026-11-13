package com.black.app.usagelog.collect

import com.black.app.usagelog.record.WifiConnectedRecord
import com.black.app.usagelog.record.WifiDisconnectedRecord
import com.black.app.usagelog.record.WifiState
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [WifiTransitionMachine]의 처리 순서별 기록, 집 전환 알림, 저장 상태 판정 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WifiTransitionMachineTest : BaseTest() {

    private val homes = setOf("MyHome_5G")
    private val disconnected = WifiState(false, null)
    private val atHome = WifiState(true, "MyHome_5G")

    private fun context(persisted: WifiState) = WifiCollectionContext(persisted, homes)

    /**
     * 초기 네트워크의 첫 보고가 수집 켜짐보다 먼저 오는 순서
     *
     * Given: 서비스가 멈춘 사이 해제됐다가 다른 집 Wi-Fi에 연결돼 저장은 해제, 초기 핸들 1개
     * When: 첫 보고 처리, 이어서 수집 켜짐 처리
     * Then: 추정 연결 기록 하나, 집 전환 알림 없음, 켜짐은 기록 없음
     */
    @Test
    fun test_01_initialReportBeforeCollectionEnabled() {
        /** Given **/
        val machine = WifiTransitionMachine(setOf(1L))

        /** When **/
        val reported = machine.onReported(1L, "MyHome_5G", 100, context(disconnected))
        val enabled = machine.onCollectionEnabled(101, context(atHome))

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(100, "MyHome_5G", true, true)), reported.records)
        assertTrue(reported.homeTransitions.isEmpty())
        assertEquals(atHome, reported.newPersisted)
        assertTrue(enabled.records.isEmpty())
        assertTrue(enabled.homeTransitions.isEmpty())
    }

    /**
     * 수집 켜짐이 초기 네트워크의 첫 보고보다 먼저 오는 순서
     *
     * Given: 저장은 해제, 초기 핸들 1개
     * When: 수집 켜짐 처리, 이어서 첫 보고 처리
     * Then: 켜짐은 건너뛰고, 첫 보고가 추정 연결 기록을 남기며 집 전환 알림 없음
     */
    @Test
    fun test_02_collectionEnabledBeforeInitialReport() {
        /** Given **/
        val machine = WifiTransitionMachine(setOf(1L))

        /** When **/
        val enabled = machine.onCollectionEnabled(99, context(disconnected))
        val reported = machine.onReported(1L, "MyHome_5G", 100, context(disconnected))

        /** Then **/
        assertTrue(enabled.records.isEmpty())
        assertNull(enabled.newPersisted)
        assertEquals(listOf(WifiConnectedRecord(100, "MyHome_5G", true, true)), reported.records)
        assertTrue(reported.homeTransitions.isEmpty())
    }

    /**
     * 초기 Wi-Fi가 없으면 수집 켜짐이 바로 맞춤
     *
     * Given: 저장은 집 연결, 초기 핸들 없음
     * When: 수집 켜짐 처리
     * Then: 추정 해제 기록, 집 전환 알림 없음, 해제 상태 저장
     */
    @Test
    fun test_03_noInitialWifiReconcilesOnEnabled() {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())

        /** When **/
        val enabled = machine.onCollectionEnabled(100, context(atHome))

        /** Then **/
        assertEquals(listOf(WifiDisconnectedRecord(100, "MyHome_5G", true, true)), enabled.records)
        assertTrue(enabled.homeTransitions.isEmpty())
        assertEquals(disconnected, enabled.newPersisted)
    }

    /**
     * 초기 첫 보고가 끝내 오지 않으면 제한 시간 만료가 맞춤
     *
     * Given: 저장은 집 연결, 초기 핸들 1개(보고 없음)
     * When: 수집 켜짐, 제한 시간 만료 처리
     * Then: 켜짐은 건너뛰고 만료가 추정 해제 기록을 남김
     */
    @Test
    fun test_04_initialTimeoutReconciles() {
        /** Given **/
        val machine = WifiTransitionMachine(setOf(1L))

        /** When **/
        val enabled = machine.onCollectionEnabled(100, context(atHome))
        val timeout = machine.onInitialTimeout(105, context(atHome))

        /** Then **/
        assertTrue(enabled.records.isEmpty())
        assertEquals(listOf(WifiDisconnectedRecord(105, "MyHome_5G", true, true)), timeout.records)
    }

    /**
     * 초기가 아닌 새 네트워크의 연결은 실제 기록과 집 도착 알림
     *
     * Given: 초기 핸들 없음, 저장은 해제
     * When: 집 Wi-Fi 첫 보고 처리
     * Then: 실제 연결 기록과 도착 알림
     */
    @Test
    fun test_05_newNetworkRecordsRealAndNotifies() {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())

        /** When **/
        val decision = machine.onReported(2L, "MyHome_5G", 100, context(disconnected))

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(100, "MyHome_5G", true, false)), decision.records)
        assertEquals(listOf(true), decision.homeTransitions)
    }

    /**
     * 수집이 꺼져 있으면 상태만 추적하고 판정 결과는 없음
     *
     * Given: 초기 핸들 없음
     * When: 컨텍스트 없이 첫 보고 처리
     * Then: 기록·알림·저장 없음, 현재 상태는 연결
     */
    @Test
    fun test_06_collectionOffTracksStateOnly() {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())

        /** When **/
        val decision = machine.onReported(2L, "MyHome_5G", 100, null)

        /** Then **/
        assertTrue(decision.records.isEmpty())
        assertNull(decision.newPersisted)
        assertEquals(atHome, machine.currentState)
    }

    /**
     * 현재 네트워크가 끊길 때 남은 네트워크가 있으면 그쪽으로 이어 처리
     *
     * Given: 집 Wi-Fi(1) 뒤에 Cafe(2)가 연결됨
     * When: 현재인 Cafe(2)가 끊김
     * Then: Cafe 해제와 집 연결 기록, 현재 상태는 집 연결
     */
    @Test
    fun test_07_lostCurrentFallsBackToRemainingNetwork() {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "MyHome_5G", 100, null)
        machine.onReported(2L, "Cafe", 110, null)

        /** When **/
        val decision = machine.onLost(2L, 120, context(WifiState(true, "Cafe")))

        /** Then **/
        assertEquals(
            listOf(
                WifiDisconnectedRecord(120, "Cafe", false, false),
                WifiConnectedRecord(120, "MyHome_5G", true, false),
            ),
            decision.records,
        )
        assertEquals(atHome, machine.currentState)
        assertEquals(atHome, decision.newPersisted)
    }

    /**
     * 현재가 아닌 네트워크가 끊겨도 상태는 그대로
     *
     * Given: 집 Wi-Fi(1) 뒤에 Cafe(2)가 연결됨
     * When: 집 Wi-Fi(1)가 끊김
     * Then: 기록 없음, 현재 상태는 Cafe 연결
     */
    @Test
    fun test_08_lostNonCurrentKeepsState() {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "MyHome_5G", 100, null)
        machine.onReported(2L, "Cafe", 110, null)

        /** When **/
        val decision = machine.onLost(1L, 120, context(WifiState(true, "Cafe")))

        /** Then **/
        assertTrue(decision.records.isEmpty())
        assertEquals(WifiState(true, "Cafe"), machine.currentState)
    }

    /**
     * 마지막 네트워크가 끊기면 해제 기록과 출발 알림
     *
     * Given: 집 Wi-Fi(1) 하나
     * When: 끊김
     * Then: 실제 해제 기록, 출발 알림, 해제 상태 저장
     */
    @Test
    fun test_09_lostLastNetworkRecordsDisconnect() {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "MyHome_5G", 100, null)

        /** When **/
        val decision = machine.onLost(1L, 120, context(atHome))

        /** Then **/
        assertEquals(listOf(WifiDisconnectedRecord(120, "MyHome_5G", true, false)), decision.records)
        assertEquals(listOf(false), decision.homeTransitions)
        assertEquals(disconnected, decision.newPersisted)
    }

    /**
     * SSID가 null이었다가 값이 보이면 추정 연결 기록, 알림 없음
     *
     * Given: SSID null로 연결된 네트워크, 저장 상태는 (true, null)
     * When: 같은 네트워크가 집 SSID로 다시 보고됨
     * Then: 해제 기록 없이 inferred 연결 기록 하나, 알림 없음, 상태 갱신
     */
    @Test
    fun test_10_lateSsidRecordsInferredConnect() {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, null, 100, null)

        /** When **/
        val decision = machine.onReported(1L, "MyHome_5G", 130, context(WifiState(true, null)))

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(130, "MyHome_5G", true, true)), decision.records)
        assertTrue(decision.homeTransitions.isEmpty())
        assertEquals(atHome, decision.newPersisted)
    }

    /**
     * 같은 네트워크의 SSID가 계속 null이면 다시 처리하지 않음
     *
     * Given: SSID null로 연결된 네트워크
     * When: 같은 네트워크가 null로 다시 보고됨
     * Then: 기록 없음
     */
    @Test
    fun test_11_repeatedNullSsidIsIgnored() {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, null, 100, null)

        /** When **/
        val decision = machine.onReported(1L, null, 130, context(WifiState(true, null)))

        /** Then **/
        assertTrue(decision.records.isEmpty())
        assertNull(decision.newPersisted)
    }
}
