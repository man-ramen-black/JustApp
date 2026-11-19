package com.black.core.wifi

import android.content.Context
import android.net.wifi.WifiManager
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWifiInfo

/** [WifiSsid]의 SSID 정리와 현재 연결 Wi-Fi 이름 조회 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WifiSsidTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Suppress("DEPRECATION")
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * SSID 정리 검증
     *
     * Given: 따옴표 SSID, 알 수 없음 표기, 빈 문자열, null, 일반 SSID
     * When: normalize 호출
     * Then: 따옴표만 제거되고 알 수 없는 값은 null
     */
    @Test
    fun test_01_normalize() = runTest {
        /** When / Then **/
        assertEquals("MyHome_5G", WifiSsid.normalize("\"MyHome_5G\""))
        assertNull(WifiSsid.normalize("<unknown ssid>"))
        assertNull(WifiSsid.normalize(""))
        assertNull(WifiSsid.normalize(null))
        assertEquals("Cafe", WifiSsid.normalize("Cafe"))
    }

    /**
     * 연결 정보의 SSID를 큰따옴표 없이 돌려주는지 검증
     *
     * Given: 연결 정보 SSID가 MyHome_5G(시스템은 큰따옴표로 감싸 줌)
     * When: current 호출
     * Then: "MyHome_5G"
     */
    @Test
    fun test_02_currentStripsQuotes() = runTest {
        /** Given **/
        val wifiInfo = ShadowWifiInfo.newInstance()
        shadowOf(wifiInfo).setSSID("MyHome_5G")
        shadowOf(wifiManager).setConnectionInfo(wifiInfo)

        /** When **/
        val ssid = WifiSsid.current(context)

        /** Then **/
        assertEquals("\"MyHome_5G\"", wifiInfo.ssid)
        assertEquals("MyHome_5G", ssid)
    }

    /**
     * 시스템이 알 수 없음 표기를 주면 null인지 검증
     *
     * Given: SSID를 정하지 않은 연결 정보(위치 권한·위치 설정이 없을 때처럼 알 수 없음 표기)
     * When: current 호출
     * Then: null
     */
    @Test
    fun test_03_currentUnknownSsidIsNull() = runTest {
        /** Given **/
        val wifiInfo = ShadowWifiInfo.newInstance()
        shadowOf(wifiManager).setConnectionInfo(wifiInfo)

        /** When **/
        val ssid = WifiSsid.current(context)

        /** Then **/
        assertEquals(WifiManager.UNKNOWN_SSID, wifiInfo.ssid)
        assertNull(ssid)
    }

    /**
     * 연결 정보 읽기가 예외를 던지면 예외 없이 null인지 검증
     *
     * Given: Wi-Fi 상태 읽기 권한이 없어 연결 정보 조회가 SecurityException을 던짐
     * When: current 호출
     * Then: null
     */
    @Test
    fun test_04_currentReadFailureIsNull() = runTest {
        /** Given **/
        shadowOf(wifiManager).setAccessWifiStatePermission(false)

        /** When **/
        val ssid = WifiSsid.current(context)

        /** Then **/
        assertNull(ssid)
    }
}
