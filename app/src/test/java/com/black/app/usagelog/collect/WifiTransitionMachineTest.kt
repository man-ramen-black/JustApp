package com.black.app.usagelog.collect

import com.black.app.usagelog.record.WifiConnectedRecord
import com.black.app.usagelog.record.WifiDisconnectedRecord
import com.black.app.usagelog.record.WifiState
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [WifiTransitionMachine]의 처리 순서별 기록, 집 전환 알림, 저장 상태 판정 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WifiTransitionMachineTest : BaseTest() {

    private val homes = setOf("MyHome_5G", "MyHome_2G")
    private val disconnected = WifiState(false, null)
    private val atHome = WifiState(true, "MyHome_5G")
    private val atCafe = WifiState(true, "Cafe")

    private fun context(persisted: WifiState, homeSsids: Set<String> = homes) = WifiCollectionContext(persisted, homeSsids)

    /** 판정 결과에 기록, 알림, 저장 상태가 모두 없는지 확인 */
    private fun assertNothing(decision: WifiDecision) {
        assertTrue(decision.records.isEmpty())
        assertTrue(decision.homeTransitions.isEmpty())
        assertNull(decision.newPersisted)
    }

    /**
     * 초기 네트워크의 첫 보고가 수집 켜짐보다 먼저 오는 순서
     *
     * Given: 서비스가 멈춘 사이 해제됐다가 다른 집 Wi-Fi에 연결돼 저장은 해제, 초기 핸들 1개
     * When: 첫 보고 처리, 이어서 수집 켜짐 처리
     * Then: 추정 연결 기록 하나와 도착 알림, 켜짐은 기록 없음
     */
    @Test
    fun test_01_initialReportBeforeCollectionEnabled() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(setOf(1L))

        /** When **/
        val reported = machine.onReported(1L, "MyHome_5G", 100, context(disconnected))
        val enabled = machine.onCollectionEnabled(101, context(atHome))

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(100, "MyHome_5G", true, true)), reported.records)
        assertEquals(listOf(true), reported.homeTransitions)
        assertEquals(atHome, reported.newPersisted)
        assertTrue(enabled.records.isEmpty())
        assertTrue(enabled.homeTransitions.isEmpty())
    }

    /**
     * 수집 켜짐이 초기 네트워크의 첫 보고보다 먼저 오는 순서
     *
     * Given: 저장은 해제, 초기 핸들 1개
     * When: 수집 켜짐 처리, 이어서 첫 보고 처리
     * Then: 켜짐은 건너뛰고, 첫 보고가 추정 연결 기록과 도착 알림을 냄
     */
    @Test
    fun test_02_collectionEnabledBeforeInitialReport() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(setOf(1L))

        /** When **/
        val enabled = machine.onCollectionEnabled(99, context(disconnected))
        val reported = machine.onReported(1L, "MyHome_5G", 100, context(disconnected))

        /** Then **/
        assertNothing(enabled)
        assertEquals(listOf(WifiConnectedRecord(100, "MyHome_5G", true, true)), reported.records)
        assertEquals(listOf(true), reported.homeTransitions)
    }

    /**
     * 초기 Wi-Fi가 없으면 수집 켜짐이 바로 맞춤
     *
     * Given: 저장은 집 연결, 초기 핸들 없음
     * When: 수집 켜짐 처리
     * Then: 추정 해제 기록과 출발 알림, 해제 상태 저장
     */
    @Test
    fun test_03_noInitialWifiReconcilesOnEnabled() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())

        /** When **/
        val enabled = machine.onCollectionEnabled(100, context(atHome))

        /** Then **/
        assertEquals(listOf(WifiDisconnectedRecord(100, "MyHome_5G", true, true)), enabled.records)
        assertEquals(listOf(false), enabled.homeTransitions)
        assertEquals(disconnected, enabled.newPersisted)
    }

    /**
     * 초기 첫 보고가 끝내 오지 않으면 제한 시간 만료가 맞춤
     *
     * Given: 저장은 집 연결, 초기 핸들 1개(보고 없음)
     * When: 수집 켜짐, 제한 시간 만료 처리
     * Then: 켜짐은 건너뛰고 만료가 추정 해제 기록과 출발 알림을 냄
     */
    @Test
    fun test_04_initialTimeoutReconciles() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(setOf(1L))

        /** When **/
        val enabled = machine.onCollectionEnabled(100, context(atHome))
        val timeout = machine.onInitialTimeout(105, context(atHome))

        /** Then **/
        assertNothing(enabled)
        assertEquals(listOf(WifiDisconnectedRecord(105, "MyHome_5G", true, true)), timeout.records)
        assertEquals(listOf(false), timeout.homeTransitions)
    }

    /**
     * 초기가 아닌 새 네트워크의 연결은 실제 기록과 집 도착 알림
     *
     * Given: 초기 핸들 없음, 저장은 해제
     * When: 집 Wi-Fi 첫 보고 처리
     * Then: 실제 연결 기록과 도착 알림
     */
    @Test
    fun test_05_newNetworkRecordsRealAndNotifies() = runTest {
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
    fun test_06_collectionOffTracksStateOnly() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())

        /** When **/
        val decision = machine.onReported(2L, "MyHome_5G", 100, null)

        /** Then **/
        assertNothing(decision)
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
    fun test_07_lostCurrentFallsBackToRemainingNetwork() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "MyHome_5G", 100, null)
        machine.onReported(2L, "Cafe", 110, null)

        /** When **/
        val decision = machine.onLost(2L, 120, context(atCafe))

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
    fun test_08_lostNonCurrentKeepsState() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "MyHome_5G", 100, null)
        machine.onReported(2L, "Cafe", 110, null)

        /** When **/
        val decision = machine.onLost(1L, 120, context(atCafe))

        /** Then **/
        assertTrue(decision.records.isEmpty())
        assertEquals(atCafe, machine.currentState)
    }

    /**
     * 마지막 네트워크가 끊기면 해제 기록과 출발 알림
     *
     * Given: 집 Wi-Fi(1) 하나
     * When: 끊김
     * Then: 실제 해제 기록, 출발 알림, 해제 상태 저장
     */
    @Test
    fun test_09_lostLastNetworkRecordsDisconnect() = runTest {
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
     * SSID가 null이었다가 집 SSID가 보이면 추정 연결 기록과 도착 알림
     *
     * Given: SSID null로 연결된 네트워크, 저장 상태는 (true, null)
     * When: 같은 네트워크가 집 SSID로 다시 보고됨
     * Then: 해제 기록 없이 inferred 연결 기록 하나, 도착 알림, 상태 갱신
     */
    @Test
    fun test_10_lateSsidRecordsInferredConnect() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, null, 100, null)

        /** When **/
        val decision = machine.onReported(1L, "MyHome_5G", 130, context(WifiState(true, null)))

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(130, "MyHome_5G", true, true)), decision.records)
        assertEquals(listOf(true), decision.homeTransitions)
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
    fun test_11_repeatedNullSsidIsIgnored() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, null, 100, null)

        /** When **/
        val decision = machine.onReported(1L, null, 130, context(WifiState(true, null)))

        /** Then **/
        assertNothing(decision)
    }

    /**
     * 시작 때 수집 켜짐이 콜백 보고보다 먼저 옛 저장 상태를 맞춤
     *
     * Given: 초기 핸들 없음, 저장 상태는 집 연결
     * When: 수집 켜짐 처리 뒤 Cafe 첫 보고 처리
     * Then: 켜짐은 추정 집 해제 1건과 출발 알림, 보고는 실제 Cafe 연결 1건이고 알림 없음
     */
    @Test
    fun test_12_startupReconcileBeforeFirstReport() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())

        /** When **/
        val enabled = machine.onCollectionEnabled(100, context(atHome))
        val reported = machine.onReported(2L, "Cafe", 110, context(disconnected))

        /** Then **/
        assertEquals(listOf(WifiDisconnectedRecord(100, "MyHome_5G", true, true)), enabled.records)
        assertEquals(listOf(false), enabled.homeTransitions)
        assertEquals(disconnected, enabled.newPersisted)
        assertEquals(listOf(WifiConnectedRecord(110, "Cafe", false, false)), reported.records)
        assertTrue(reported.homeTransitions.isEmpty())
        assertEquals(atCafe, reported.newPersisted)
    }

    /**
     * 재부팅·재연결 때 저장 상태와 같으면 기록과 알림이 없음
     *
     * Given: 초기 핸들이 저장 상태와 같은 집 Wi-Fi인 감시, 초기 핸들 없이 저장 상태도 해제인 감시
     * When: 각각 수집 켜짐과 첫 보고 처리
     * Then: 모두 기록과 집 전환 알림 없음
     */
    @Test
    fun test_13_sameStateOnRestartRecordsNothing() = runTest {
        /** Given **/
        val reconnected = WifiTransitionMachine(setOf(1L))
        val offline = WifiTransitionMachine(emptySet())

        /** When **/
        val decisions = listOf(
            reconnected.onCollectionEnabled(100, context(atHome)),
            reconnected.onReported(1L, "MyHome_5G", 101, context(atHome)),
            offline.onCollectionEnabled(100, context(disconnected)),
        )

        /** Then **/
        decisions.forEach { decision ->
            assertTrue(decision.records.isEmpty())
            assertTrue(decision.homeTransitions.isEmpty())
        }
    }

    /**
     * 집 Wi-Fi끼리 옮기면 기록은 남기고 집 전환 알림만 생략
     *
     * Given: 집 5G(1)에 연결돼 저장됨, 같은 집의 2G도 집 SSID로 등록됨
     * When: 집 2G(2)가 새로 연결됨
     * Then: 5G 해제와 2G 연결 기록, 집 전환 알림 없음
     */
    @Test
    fun test_14_homeToHomeSwitchSkipsTransitions() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "MyHome_5G", 100, null)

        /** When **/
        val decision = machine.onReported(2L, "MyHome_2G", 110, context(atHome))

        /** Then **/
        assertEquals(
            listOf(
                WifiDisconnectedRecord(110, "MyHome_5G", true, false),
                WifiConnectedRecord(110, "MyHome_2G", true, false),
            ),
            decision.records,
        )
        assertTrue(decision.homeTransitions.isEmpty())
        assertEquals(WifiState(true, "MyHome_2G"), decision.newPersisted)
    }

    /**
     * 지금 연결된 Wi-Fi를 집으로 등록하면 추정 연결 기록과 도착 알림
     *
     * Given: Cafe에 연결된 것을 기록하고 저장함
     * When: Cafe를 집 SSID에 더한 집 SSID 변경 처리
     * Then: Cafe 추정 집 연결 1건, 도착 알림, 저장 상태는 Cafe 연결
     */
    @Test
    fun test_15_homeAddedRecordsInferredArrival() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "Cafe", 100, context(disconnected))

        /** When **/
        val decision = machine.onHomesChanged(200, context(atCafe, homes + "Cafe"))

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(200, "Cafe", true, true)), decision.records)
        assertEquals(listOf(true), decision.homeTransitions)
        assertEquals(atCafe, decision.newPersisted)
    }

    /**
     * 지금 연결된 집 Wi-Fi를 집에서 빼면 추정 해제 뒤 집이 아닌 추정 연결과 출발 알림
     *
     * Given: 집 5G에 연결된 것을 기록하고 저장함
     * When: 집 5G를 뺀 집 SSID 변경 처리
     * Then: 5G 추정 집 해제, 5G 추정 집 아닌 연결 순서, 출발 알림
     */
    @Test
    fun test_16_homeRemovedRecordsInferredLeave() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "MyHome_5G", 100, context(disconnected))

        /** When **/
        val decision = machine.onHomesChanged(200, context(atHome, homes - "MyHome_5G"))

        /** Then **/
        assertEquals(
            listOf(
                WifiDisconnectedRecord(200, "MyHome_5G", true, true),
                WifiConnectedRecord(200, "MyHome_5G", false, true),
            ),
            decision.records,
        )
        assertEquals(listOf(false), decision.homeTransitions)
        assertEquals(atHome, decision.newPersisted)
    }

    /**
     * 감시 시작 직후의 첫 집 SSID 값과 지금 연결과 무관한 변경은 기록하지 않음
     *
     * Given: 초기 Cafe 연결을 저장 상태와 같다고 맞춘 감시
     * When: 같은 집 SSID로 변경 처리, 다른 SSID를 더한 변경 처리
     * Then: 둘 다 기록·알림·저장 없음
     */
    @Test
    fun test_17_unchangedHomeStatusRecordsNothing() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(setOf(1L))
        machine.onCollectionEnabled(100, context(atCafe))
        machine.onReported(1L, "Cafe", 101, context(atCafe))

        /** When **/
        val firstValue = machine.onHomesChanged(102, context(atCafe))
        val unrelated = machine.onHomesChanged(103, context(atCafe, homes + "Office"))

        /** Then **/
        assertNothing(firstValue)
        assertNothing(unrelated)
    }

    /**
     * 수집이 꺼져 있거나 저장 상태가 지금 상태와 다르면 집 SSID 변경을 건너뜀
     *
     * Given: Cafe에 연결된 것을 기록하고 저장함
     * When: 컨텍스트 없이 Cafe 등록 처리, 저장 상태가 해제인 컨텍스트로 Cafe 등록 처리
     * Then: 둘 다 기록·알림·저장 없음
     */
    @Test
    fun test_18_homesChangeSkippedWhenOffOrOutOfSync() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "Cafe", 100, context(disconnected))

        /** When **/
        val collectionOff = machine.onHomesChanged(200, null)
        val outOfSync = machine.onHomesChanged(201, context(disconnected, homes + "Cafe"))

        /** Then **/
        assertNothing(collectionOff)
        assertNothing(outOfSync)
    }

    /**
     * 보고되지 않은 초기 연결이 끊기면 대기를 끝내고 추정 해제
     *
     * Given: 저장은 집 연결, 초기 핸들 1개, 수집 켜짐은 대기로 건너뜀
     * When: 첫 보고 없이 초기 핸들이 끊김
     * Then: 추정 집 해제 1건, 출발 알림, 해제 상태 저장
     */
    @Test
    fun test_19_unreportedInitialLostReconciles() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(setOf(1L))
        machine.onCollectionEnabled(100, context(atHome))

        /** When **/
        val decision = machine.onLost(1L, 110, context(atHome))

        /** Then **/
        assertEquals(listOf(WifiDisconnectedRecord(110, "MyHome_5G", true, true)), decision.records)
        assertEquals(listOf(false), decision.homeTransitions)
        assertEquals(disconnected, decision.newPersisted)
    }

    /**
     * 집 전환 알림은 기록 순서를 따름
     *
     * Given: 집 5G(1)에 연결된 것을 기록하고 저장함
     * When: Cafe(2)가 새로 연결된 뒤 Cafe(2)가 끊겨 집 5G로 돌아옴
     * Then: 첫 판정은 집 해제 뒤 Cafe 연결과 출발 알림, 둘째 판정은 Cafe 해제 뒤 집 연결과 도착 알림
     */
    @Test
    fun test_20_homeTransitionsFollowRecordOrder() = runTest {
        /** Given **/
        val machine = WifiTransitionMachine(emptySet())
        machine.onReported(1L, "MyHome_5G", 100, context(disconnected))

        /** When **/
        val toCafe = machine.onReported(2L, "Cafe", 110, context(atHome))
        val backHome = machine.onLost(2L, 120, context(atCafe))

        /** Then **/
        assertEquals(
            listOf(
                WifiDisconnectedRecord(110, "MyHome_5G", true, false),
                WifiConnectedRecord(110, "Cafe", false, false),
            ),
            toCafe.records,
        )
        assertEquals(listOf(false), toCafe.homeTransitions)
        assertEquals(
            listOf(
                WifiDisconnectedRecord(120, "Cafe", false, false),
                WifiConnectedRecord(120, "MyHome_5G", true, false),
            ),
            backHome.records,
        )
        assertEquals(listOf(true), backHome.homeTransitions)
    }

    /**
     * 기다리는 초기 네트워크가 없으면 제한 시간 만료는 아무것도 하지 않음
     *
     * Given: 초기 핸들 없는 감시, 초기 핸들의 첫 보고를 이미 처리한 감시
     * When: 각각 제한 시간 만료 처리
     * Then: 둘 다 기록·알림·저장 없음
     */
    @Test
    fun test_21_initialTimeoutWithoutPendingDoesNothing() = runTest {
        /** Given **/
        val noInitial = WifiTransitionMachine(emptySet())
        val reported = WifiTransitionMachine(setOf(1L))
        reported.onReported(1L, "MyHome_5G", 100, context(disconnected))

        /** When **/
        val noInitialTimeout = noInitial.onInitialTimeout(105, context(disconnected))
        val reportedTimeout = reported.onInitialTimeout(105, context(atHome))

        /** Then **/
        assertNothing(noInitialTimeout)
        assertNothing(reportedTimeout)
    }
}
