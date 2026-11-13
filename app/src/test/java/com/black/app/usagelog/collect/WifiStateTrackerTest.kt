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

/** [WifiStateTracker]의 SSID 정규화, 집 판정, 연결·해제·보정 기록 계산 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WifiStateTrackerTest : BaseTest() {

    private val homes = setOf("MyHome_5G")
    private val disconnected = WifiState(false, null)
    private val atHome = WifiState(true, "MyHome_5G")

    /**
     * SSID 정규화 검증
     *
     * Given: 따옴표 SSID, 알 수 없음 표기, 빈 문자열, null, 일반 SSID
     * When: normalizeSsid 호출
     * Then: 따옴표만 제거되고 알 수 없는 값은 null
     */
    @Test
    fun test_01_normalizeSsid() = runTest {
        /** When / Then **/
        assertEquals("MyHome_5G", WifiStateTracker.normalizeSsid("\"MyHome_5G\""))
        assertNull(WifiStateTracker.normalizeSsid("<unknown ssid>"))
        assertNull(WifiStateTracker.normalizeSsid(""))
        assertNull(WifiStateTracker.normalizeSsid(null))
        assertEquals("Cafe", WifiStateTracker.normalizeSsid("Cafe"))
    }

    /**
     * 집 여부 판정 검증
     *
     * Given: 집 SSID 하나
     * When: 집, 다른 곳, null로 isHome 호출
     * Then: true, false, null
     */
    @Test
    fun test_02_isHome() = runTest {
        /** When / Then **/
        assertEquals(true, WifiStateTracker.isHome("MyHome_5G", homes))
        assertEquals(false, WifiStateTracker.isHome("Cafe", homes))
        assertNull(WifiStateTracker.isHome(null, homes))
    }

    /**
     * 해제 상태에서 연결하면 연결 기록 하나
     *
     * Given: 해제 상태
     * When: 집 SSID로 onConnected
     * Then: 집 연결 기록 하나
     */
    @Test
    fun test_03_connectFromDisconnectedRecordsOne() = runTest {
        /** When **/
        val records = WifiStateTracker.onConnected(disconnected, "MyHome_5G", homes, 100)

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(100, "MyHome_5G", true, false)), records)
    }

    /**
     * 같은 SSID로 다시 연결돼도 기록 없음
     *
     * Given: 집에 연결된 상태
     * When: 같은 SSID로 onConnected
     * Then: 빈 목록
     */
    @Test
    fun test_04_connectSameSsidAgainRecordsNothing() = runTest {
        /** When / Then **/
        assertTrue(WifiStateTracker.onConnected(atHome, "MyHome_5G", homes, 100).isEmpty())
    }

    /**
     * 다른 SSID로 옮기면 해제 뒤 연결
     *
     * Given: 집에 연결된 상태
     * When: Cafe로 onConnected
     * Then: 집 해제, Cafe 연결 순서
     */
    @Test
    fun test_05_connectDifferentSsidRecordsDisconnectThenConnect() = runTest {
        /** When **/
        val records = WifiStateTracker.onConnected(atHome, "Cafe", homes, 100)

        /** Then **/
        assertEquals(
            listOf(
                WifiDisconnectedRecord(100, "MyHome_5G", true, false),
                WifiConnectedRecord(100, "Cafe", false, false),
            ),
            records,
        )
    }

    /**
     * 해제는 이전 SSID와 집 여부를 기록
     *
     * Given: 집에 연결된 상태
     * When: onDisconnected
     * Then: 집 해제 기록 하나
     */
    @Test
    fun test_06_disconnectRecordsPreviousSsidAndHome() = runTest {
        /** When **/
        val records = WifiStateTracker.onDisconnected(atHome, homes, 100)

        /** Then **/
        assertEquals(listOf(WifiDisconnectedRecord(100, "MyHome_5G", true, false)), records)
    }

    /**
     * 이미 해제 상태면 기록 없음
     *
     * Given: 해제 상태
     * When: onDisconnected
     * Then: 빈 목록
     */
    @Test
    fun test_07_disconnectWhenAlreadyDisconnectedRecordsNothing() = runTest {
        /** When / Then **/
        assertTrue(WifiStateTracker.onDisconnected(disconnected, homes, 100).isEmpty())
    }

    /**
     * 보정 기록은 추정 표시
     *
     * Given: 저장된 상태는 집 연결, 현재는 해제
     * When: reconcile
     * Then: inferred 해제 기록 하나
     */
    @Test
    fun test_08_reconcileMarksInferred() = runTest {
        /** When **/
        val records = WifiStateTracker.reconcile(atHome, disconnected, homes, 100)

        /** Then **/
        assertEquals(listOf(WifiDisconnectedRecord(100, "MyHome_5G", true, true)), records)
    }

    /**
     * 같은 상태면 보정 기록 없음
     *
     * Given: 저장된 상태와 현재 상태가 같음
     * When: reconcile
     * Then: 빈 목록
     */
    @Test
    fun test_09_reconcileSameStateRecordsNothing() = runTest {
        /** When / Then **/
        assertTrue(WifiStateTracker.reconcile(atHome, atHome, homes, 100).isEmpty())
        assertTrue(WifiStateTracker.reconcile(disconnected, disconnected, homes, 100).isEmpty())
    }

    /**
     * SSID를 못 읽으면 집 여부는 null
     *
     * Given: 해제 상태
     * When: null SSID로 onConnected
     * Then: ssid, home이 null인 연결 기록
     */
    @Test
    fun test_10_unknownSsidRecordsNullHome() = runTest {
        /** When **/
        val records = WifiStateTracker.onConnected(disconnected, null, homes, 100)

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(100, null, null, false)), records)
    }

    /**
     * 저장은 해제, 현재는 연결이면 추정 연결 기록 하나
     *
     * Given: 저장된 상태는 해제, 현재는 집 연결
     * When: reconcile
     * Then: inferred 연결 기록 하나
     */
    @Test
    fun test_11_reconcileDisconnectedToConnected() = runTest {
        /** When **/
        val records = WifiStateTracker.reconcile(disconnected, atHome, homes, 100)

        /** Then **/
        assertEquals(listOf(WifiConnectedRecord(100, "MyHome_5G", true, true)), records)
    }

    /**
     * 저장은 A, 현재는 B면 추정 해제와 추정 연결 두 건
     *
     * Given: 저장된 상태는 집 연결, 현재는 Cafe 연결
     * When: reconcile
     * Then: inferred 집 해제, inferred Cafe 연결 순서
     */
    @Test
    fun test_12_reconcileDifferentSsidRecordsTwo() = runTest {
        /** When **/
        val records = WifiStateTracker.reconcile(atHome, WifiState(true, "Cafe"), homes, 100)

        /** Then **/
        assertEquals(
            listOf(
                WifiDisconnectedRecord(100, "MyHome_5G", true, true),
                WifiConnectedRecord(100, "Cafe", false, true),
            ),
            records,
        )
    }
}
