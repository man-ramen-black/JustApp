package com.black.core.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
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
import org.robolectric.shadows.ShadowUsageStatsManager.EventBuilder

/** [SystemUsageEventSource]의 사용 정보 접근 확인과 시스템 이벤트 종류 변환 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SystemUsageEventSourceTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    /** 이 앱의 사용 정보 접근 AppOps를 [mode]로 설정 */
    private fun setUsageAccessMode(mode: Int) {
        shadowOf(appOpsManager).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, mode)
    }

    /** 시스템 사용 통계에 [packageName]의 [className] 액티비티가 [time]에 남긴 [eventType] 이벤트 추가 */
    private fun addEvent(packageName: String, time: Long, eventType: Int, className: String? = null) {
        val event = EventBuilder.buildEvent()
            .setPackage(packageName)
            .setClass(className)
            .setTimeStamp(time)
            .setEventType(eventType)
            .build()
        shadowOf(usageStatsManager).addEvent(event)
    }

    /**
     * 사용 정보 접근이 거부되면 이벤트가 있어도 null인지 검증
     *
     * Given: 사용 정보 접근 AppOps가 MODE_IGNORED, 앱 재개 이벤트 1건
     * When: query 호출
     * Then: null
     */
    @Test
    fun test_01_usageAccessDeniedReturnsNull() = runTest {
        /** Given **/
        setUsageAccessMode(AppOpsManager.MODE_IGNORED)
        addEvent(APP_PACKAGE, 1000, UsageEvents.Event.ACTIVITY_RESUMED)

        /** When **/
        val events = SystemUsageEventSource(context).query(0, 10_000)

        /** Then **/
        assertNull(events)
    }

    /**
     * 사용 정보 접근이 허용되면 지원하는 여섯 종류만 시각·패키지를 유지한 채 변환되고 다른 이벤트는 빠지는지 검증
     *
     * Given: 사용 정보 접근 허용, 앱 재개·일시 정지·정지, 화면 켜짐·꺼짐, 잠금 해제 이벤트 사이에 잠금 표시·포그라운드 서비스 시작·종료·사용자 조작 이벤트
     * When: query 호출
     * Then: 지원 이벤트만 시각 오름차순으로 RESUMED, PAUSED, STOPPED, SCREEN_ON, SCREEN_OFF, UNLOCK이 되고 패키지와 시각은 그대로, 앱 이벤트에만 클래스 이름이 실림
     */
    @Test
    fun test_02_usageAccessGrantedConvertsOnlySupportedKinds() = runTest {
        /** Given **/
        setUsageAccessMode(AppOpsManager.MODE_ALLOWED)
        addEvent(APP_PACKAGE, 1000, UsageEvents.Event.ACTIVITY_RESUMED, APP_CLASS)
        addEvent(APP_PACKAGE, 1500, UsageEvents.Event.USER_INTERACTION)
        addEvent(APP_PACKAGE, 2000, UsageEvents.Event.ACTIVITY_PAUSED, APP_CLASS)
        addEvent(APP_PACKAGE, 2500, UsageEvents.Event.ACTIVITY_STOPPED, APP_CLASS)
        addEvent(SYSTEM_PACKAGE, 3000, UsageEvents.Event.SCREEN_INTERACTIVE, SYSTEM_CLASS)
        addEvent(SYSTEM_PACKAGE, 3500, UsageEvents.Event.KEYGUARD_SHOWN)
        addEvent(SYSTEM_PACKAGE, 4000, UsageEvents.Event.SCREEN_NON_INTERACTIVE, SYSTEM_CLASS)
        addEvent(OTHER_PACKAGE, 4500, UsageEvents.Event.FOREGROUND_SERVICE_START)
        addEvent(SYSTEM_PACKAGE, 5000, UsageEvents.Event.KEYGUARD_HIDDEN, SYSTEM_CLASS)
        addEvent(OTHER_PACKAGE, 5500, UsageEvents.Event.FOREGROUND_SERVICE_STOP)

        /** When **/
        val events = SystemUsageEventSource(context).query(0, 10_000)

        /** Then **/
        assertEquals(
            listOf(
                RawUsageEvent(RawUsageEventKind.RESUMED, APP_PACKAGE, 1000, APP_CLASS),
                RawUsageEvent(RawUsageEventKind.PAUSED, APP_PACKAGE, 2000, APP_CLASS),
                RawUsageEvent(RawUsageEventKind.STOPPED, APP_PACKAGE, 2500, APP_CLASS),
                RawUsageEvent(RawUsageEventKind.SCREEN_ON, SYSTEM_PACKAGE, 3000),
                RawUsageEvent(RawUsageEventKind.SCREEN_OFF, SYSTEM_PACKAGE, 4000),
                RawUsageEvent(RawUsageEventKind.UNLOCK, SYSTEM_PACKAGE, 5000),
            ),
            events,
        )
    }

    /**
     * 사용 정보 접근이 허용되고 구간에 이벤트가 없으면 null이 아닌 빈 목록인지 검증
     *
     * Given: 사용 정보 접근 AppOps가 MODE_ALLOWED, 조회 구간 밖의 앱 재개 이벤트 1건
     * When: 이벤트가 없는 구간으로 query 호출
     * Then: 빈 목록
     */
    @Test
    fun test_03_usageAccessGrantedWithoutEventsReturnsEmptyList() = runTest {
        /** Given **/
        setUsageAccessMode(AppOpsManager.MODE_ALLOWED)
        addEvent(APP_PACKAGE, 20_000, UsageEvents.Event.ACTIVITY_RESUMED)

        /** When **/
        val events = SystemUsageEventSource(context).query(0, 10_000)

        /** Then **/
        assertEquals(emptyList<RawUsageEvent>(), events)
    }

    /**
     * 액티비티 종료가 정지로 변환되고 클래스 이름이 유지되는지 검증
     *
     * Given: 사용 정보 접근 허용, 앱 재개 뒤 액티비티 종료 이벤트
     * When: query 호출
     * Then: RESUMED, STOPPED 순서이고 두 이벤트 모두 클래스 이름 유지
     */
    @Test
    fun test_04_convertsActivityDestroyedToStopped() = runTest {
        /** Given **/
        setUsageAccessMode(AppOpsManager.MODE_ALLOWED)
        addEvent(APP_PACKAGE, 1000, UsageEvents.Event.ACTIVITY_RESUMED, APP_CLASS)
        addEvent(APP_PACKAGE, 2000, ACTIVITY_DESTROYED, APP_CLASS)

        /** When **/
        val events = SystemUsageEventSource(context).query(0, 10_000)

        /** Then **/
        assertEquals(
            listOf(
                RawUsageEvent(RawUsageEventKind.RESUMED, APP_PACKAGE, 1000, APP_CLASS),
                RawUsageEvent(RawUsageEventKind.STOPPED, APP_PACKAGE, 2000, APP_CLASS),
            ),
            events,
        )
    }

    private companion object {
        const val APP_PACKAGE = "com.example.app"
        const val APP_CLASS = "com.example.app.MainActivity"
        const val OTHER_PACKAGE = "com.example.other"

        /** 화면·잠금 이벤트를 남기는 시스템 패키지 */
        const val SYSTEM_PACKAGE = "android"

        /** 화면·잠금 이벤트에 클래스 이름이 실려 와도 변환 결과에서 빠지는지 확인하는 값 */
        const val SYSTEM_CLASS = "android.SystemClass"

        /** `UsageEvents.Event.ACTIVITY_DESTROYED`는 공개 SDK에 없는 시스템 API라 값으로 사용 */
        const val ACTIVITY_DESTROYED = 24
    }
}
