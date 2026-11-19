package com.black.feature.todo.receiver

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.black.feature.todo.notification.TodoReminderRescheduler
import com.black.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/** [TodoExactAlarmPermissionReceiver]의 액션 확인과 다시 예약 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoExactAlarmPermissionReceiverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val rescheduled = CompletableDeferred<Unit>()
    private var reschedulerCreations = 0

    private val originalRescheduler = TodoExactAlarmPermissionReceiver.createRescheduler

    /** 실제 저장본을 읽지 않도록 다시 예약을 호출 기록으로 대체 */
    @Before
    fun replaceRescheduler() {
        TodoExactAlarmPermissionReceiver.createRescheduler = {
            reschedulerCreations++
            TodoReminderRescheduler { rescheduled.complete(Unit) }
        }
    }

    @After
    fun restoreRescheduler() {
        TodoExactAlarmPermissionReceiver.createRescheduler = originalRescheduler
    }

    /**
     * 정확한 알람 권한 변경 액션이면 다시 예약하는지 검증
     *
     * Given: 다시 예약 호출 기록
     * When: ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED로 수신기를 지정한 브로드캐스트 전송
     * Then: 다시 예약 호출
     */
    @Test
    fun test_01_permissionChangeReschedules() = runTest {
        /** Given **/
        val intent = Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            .setClass(context, TodoExactAlarmPermissionReceiver::class.java)

        /** When **/
        context.sendBroadcast(intent)
        idleMainLooper()

        /** Then **/
        // 다시 예약은 goAsync로 이어 가는 Dispatchers.Default 범위에서 불리므로 실제 시간으로 대기
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { rescheduled.await() } }
    }

    /**
     * 다른 액션이면 아무것도 하지 않는지 검증
     *
     * Given: 다른 액션 인텐트
     * When: onReceive 직접 호출
     * Then: 예약기 생성과 다시 예약 없음
     */
    @Test
    fun test_02_otherActionIsIgnored() = runTest {
        /** Given **/
        val intent = Intent("com.other.ACTION")

        /** When **/
        TodoExactAlarmPermissionReceiver().onReceive(context, intent)
        idleMainLooper()

        /** Then **/
        assertEquals(0, reschedulerCreations)
        assertFalse(rescheduled.isCompleted)
    }
}
