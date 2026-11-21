package com.black.core.system

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/**
 * [SystemCalendarSource]의 권한 확인, 조회 조건, 행 변환 검증
 *
 * 가짜 캘린더 제공자가 받은 URI·selection을 기록하고 열 이름대로 행을 돌려줌.
 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SystemCalendarSourceTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val provider = FakeCalendarProvider()

    @Before
    fun setupProvider() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALENDAR)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
    }

    /**
     * 캘린더 읽기 권한이 거부되면 null인지 검증
     *
     * Given: READ_CALENDAR 거부
     * When: canRead, queryCalendars, queryInstances 호출
     * Then: canRead false, 두 조회 모두 null
     */
    @Test
    fun test_01_queryReturnsNullWithoutPermission() = runTest {
        /** Given **/
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CALENDAR)
        val source = SystemCalendarSource(context)

        /** When / Then **/
        assertEquals(false, source.canRead())
        assertNull(source.queryCalendars())
        assertNull(source.queryInstances(0, 1))
    }

    /**
     * 일정을 동기화하는 캘린더만 조회하고 행을 DeviceCalendar로 바꾸는지 검증
     *
     * Given: 제공자가 캘린더 행 하나를 반환
     * When: queryCalendars 호출
     * Then: selection에 SYNC_EVENTS = 1이 있고 VISIBLE 조건은 없으며 DeviceCalendar로 변환
     */
    @Test
    fun test_02_queryCalendarsReturnsSyncedOnly() = runTest {
        /** Given **/
        provider.rows = listOf(
            mapOf(
                CalendarContract.Calendars._ID to 7L,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to "내 일정",
                CalendarContract.Calendars.ACCOUNT_NAME to "me@example.com",
                CalendarContract.Calendars.CALENDAR_COLOR to 0xFF123456.toInt(),
            ),
        )

        /** When **/
        val calendars = SystemCalendarSource(context).queryCalendars()

        /** Then **/
        assertTrue(provider.lastSelection!!.contains("${CalendarContract.Calendars.SYNC_EVENTS} = 1"))
        assertFalse(provider.lastSelection!!.contains(CalendarContract.Calendars.VISIBLE))
        assertEquals(listOf(DeviceCalendar(7L, "내 일정", "me@example.com", 0xFF123456.toInt())), calendars)
    }

    /**
     * 범위를 URI 경로에 싣고 행을 CalendarEventInstance로 옮기는지 검증
     *
     * Given: 제목이 있는 행과 제목이 null인 종일 행
     * When: queryInstances(1000, 2000) 호출
     * Then: URI 경로 끝 두 조각이 1000, 2000이고 행이 그대로 변환
     */
    @Test
    fun test_03_queryInstancesMapsRowsAndPassesRange() = runTest {
        /** Given **/
        provider.rows = listOf(
            instanceRow(eventId = 1L, calendarId = 2L, title = "회의", begin = 1100L, end = 1200L, allDay = 0),
            instanceRow(eventId = 3L, calendarId = 2L, title = null, begin = 1300L, end = 1400L, allDay = 1),
        )

        /** When **/
        val instances = SystemCalendarSource(context).queryInstances(1000L, 2000L)

        /** Then **/
        assertEquals(listOf("1000", "2000"), provider.lastUri!!.pathSegments.takeLast(2))
        assertEquals(
            listOf(
                CalendarEventInstance(1L, 2L, "회의", 1100L, 1200L, false),
                CalendarEventInstance(3L, 2L, null, 1300L, 1400L, true),
            ),
            instances,
        )
    }

    /**
     * 취소된 일정과 일정을 동기화하지 않는 캘린더의 일정을 조회 조건에서 제외하는지 검증
     *
     * Given: 제공자가 빈 결과 반환
     * When: queryInstances 호출
     * Then: selection에 SYNC_EVENTS 조건과 STATUS가 취소가 아닌 조건이 함께 들어가고 VISIBLE 조건은 없음
     */
    @Test
    fun test_04_queryInstancesExcludesCanceledAndUnsynced() = runTest {
        /** Given / When **/
        SystemCalendarSource(context).queryInstances(0L, 1L)

        /** Then **/
        val selection = provider.lastSelection.orEmpty()
        val canceled = CalendarContract.Events.STATUS_CANCELED
        assertTrue(selection.contains("${CalendarContract.Instances.SYNC_EVENTS} = 1 AND "))
        assertFalse(selection.contains(CalendarContract.Instances.VISIBLE))
        assertTrue(selection.contains("${CalendarContract.Instances.STATUS} != $canceled"))
    }

    /**
     * 제공자가 예외를 던지면 null인지 검증
     *
     * Given: 제공자가 예외를 던짐
     * When: queryCalendars, queryInstances 호출
     * Then: 둘 다 null
     */
    @Test
    fun test_05_queryReturnsNullWhenProviderThrows() = runTest {
        /** Given **/
        provider.failure = IllegalStateException("boom")
        val source = SystemCalendarSource(context)

        /** When / Then **/
        assertNull(source.queryCalendars())
        assertNull(source.queryInstances(0L, 1L))
    }

    private fun instanceRow(eventId: Long, calendarId: Long, title: String?, begin: Long, end: Long, allDay: Int) = mapOf(
        CalendarContract.Instances.EVENT_ID to eventId,
        CalendarContract.Instances.CALENDAR_ID to calendarId,
        CalendarContract.Instances.TITLE to title,
        CalendarContract.Instances.BEGIN to begin,
        CalendarContract.Instances.END to end,
        CalendarContract.Instances.ALL_DAY to allDay,
    )

    /** 받은 URI·selection을 기록하고 요청한 열 이름대로 [rows]를 돌려주는 가짜 캘린더 제공자 */
    private class FakeCalendarProvider : ContentProvider() {
        var rows: List<Map<String, Any?>> = emptyList()
        var failure: RuntimeException? = null
        var lastUri: Uri? = null
        var lastSelection: String? = null

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
            failure?.let { throw it }
            lastUri = uri
            lastSelection = selection
            val columns = projection ?: emptyArray()
            val cursor = MatrixCursor(columns)
            rows.forEach { row -> cursor.addRow(columns.map { row[it] }) }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
    }
}
