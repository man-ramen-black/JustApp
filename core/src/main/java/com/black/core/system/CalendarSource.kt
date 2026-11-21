package com.black.core.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

/** 기기 캘린더와 일정 발생 조회 */
interface CalendarSource {
    /** 캘린더 읽기 권한이 허용됐는지 */
    fun canRead(): Boolean

    /**
     * 일정을 동기화하는 캘린더 목록 조회. 권한이 없거나 조회에 실패하면 null
     *
     * 캘린더 앱의 표시 여부(`VISIBLE`)는 보지 않음. 구글 캘린더 앱만 쓰는 기기는 구글 계정 캘린더가 모두 `VISIBLE = 0`이라 이를 거르면 실제 일정이 빠짐.
     */
    fun queryCalendars(): List<DeviceCalendar>?

    /**
     * [begin] 이상 [end] 이하(epoch 밀리초)에 걸친 일정 발생 조회
     *
     * 취소된 일정과 [queryCalendars] 목록에 없는(일정을 동기화하지 않는) 캘린더의 일정은 제외. 권한이 없거나 조회에 실패하면 null.
     */
    fun queryInstances(begin: Long, end: Long): List<CalendarEventInstance>?
}

/**
 * ContentResolver 기반 [CalendarSource]
 *
 * 캘린더 읽기 권한이 없으면 null. 조회 예외는 클래스 이름만 남기고 null(일정 제목이 섞일 수 있어 메시지는 남기지 않음).
 * `READ_CALENDAR` 권한은 core가 아닌 이 클래스를 쓰는 `:feature:dday` 모듈의 매니페스트에서 선언.
 */
class SystemCalendarSource(private val context: Context) : CalendarSource {

    override fun canRead(): Boolean = PermissionHelper.isGranted(context, Manifest.permission.READ_CALENDAR)

    override fun queryCalendars(): List<DeviceCalendar>? {
        if (!canReadOrLog()) {
            return null
        }
        return try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
            )
            val selection = "${CalendarContract.Calendars.SYNC_EVENTS} = 1"
            val sortOrder = "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}"
            val calendars = ArrayList<DeviceCalendar>()
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, sortOrder)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        calendars.add(
                            DeviceCalendar(
                                id = cursor.getLong(0),
                                displayName = cursor.getString(1).orEmpty(),
                                accountName = cursor.getString(2).orEmpty(),
                                color = cursor.getInt(3),
                            ),
                        )
                    }
                }
            calendars
        } catch (failure: Exception) {
            Log.w("[SystemCalendarSource] 캘린더 조회 실패: ${failure::class.java.simpleName}")
            null
        }
    }

    override fun queryInstances(begin: Long, end: Long): List<CalendarEventInstance>? {
        if (!canReadOrLog()) {
            return null
        }
        return try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also {
                    ContentUris.appendId(it, begin)
                    ContentUris.appendId(it, end)
                }
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )
            // 설정 화면에서 끌 수 없는 캘린더의 일정이 섞이지 않도록 queryCalendars와 같은 캘린더 조건을 둠
            val selection = "${CalendarContract.Instances.SYNC_EVENTS} = 1 AND " +
                "(${CalendarContract.Instances.STATUS} IS NULL OR " +
                "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"
            val instances = ArrayList<CalendarEventInstance>()
            context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    instances.add(
                        CalendarEventInstance(
                            eventId = cursor.getLong(0),
                            calendarId = cursor.getLong(1),
                            title = cursor.getString(2),
                            begin = cursor.getLong(3),
                            end = cursor.getLong(4),
                            allDay = cursor.getInt(5) == 1,
                        ),
                    )
                }
            }
            instances
        } catch (failure: Exception) {
            Log.w("[SystemCalendarSource] 일정 조회 실패: ${failure::class.java.simpleName}")
            null
        }
    }

    private fun canReadOrLog(): Boolean {
        if (canRead()) {
            return true
        }
        // 권한 거부는 사용자가 고른 정상 상태라 조회 때마다 경고하지 않음
        Log.d("[SystemCalendarSource] 캘린더 읽기 권한 없어 조회하지 않음")
        return false
    }
}
