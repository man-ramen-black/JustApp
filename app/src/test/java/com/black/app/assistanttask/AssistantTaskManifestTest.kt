package com.black.app.assistanttask

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.black.app.assistanttask.alarm.AssistantTaskAlarmReceiver
import com.black.app.assistanttask.alarm.AssistantTaskExactAlarmPermissionReceiver
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Todo 시각 알림 수신기 두 개가 매니페스트에 맞게 선언됐는지 검증
 *
 * Robolectric은 공개되지 않은 수신기에도 브로드캐스트를 전달하므로 전달 여부가 아닌 등록 정보로 판정.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskManifestTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * 알람 수신기가 앱이 보낸 알람 인텐트만 받도록 공개되지 않았는지 검증
     *
     * Given: 병합된 앱 매니페스트
     * When: AssistantTaskAlarmReceiver 등록 정보 조회
     * Then: exported false
     */
    @Test
    fun test_01_alarmReceiverIsNotExported() = runTest {
        /** Given **/
        val componentName = ComponentName(context, AssistantTaskAlarmReceiver::class.java)

        /** When **/
        val receiverInfo = context.packageManager.getReceiverInfo(componentName, 0)

        /** Then **/
        assertFalse(receiverInfo.exported)
    }

    /**
     * 정확한 알람 허용 수신기가 시스템 방송을 받도록 공개되고 그 액션의 인텐트 필터를 가졌는지 검증
     *
     * Given: 병합된 앱 매니페스트
     * When: AssistantTaskExactAlarmPermissionReceiver 등록 정보와 정확한 알람 허용 상태 변경 액션을 받는 이 앱 수신기 조회
     * Then: exported true, 그 액션을 받는 수신기는 AssistantTaskExactAlarmPermissionReceiver 하나
     */
    @Test
    fun test_02_exactAlarmPermissionReceiverIsExportedWithAction() = runTest {
        /** Given **/
        val componentName = ComponentName(context, AssistantTaskExactAlarmPermissionReceiver::class.java)
        val intent = Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED).setPackage(context.packageName)

        /** When **/
        val receiverInfo = context.packageManager.getReceiverInfo(componentName, 0)
        val matched = context.packageManager.queryBroadcastReceivers(intent, 0).map { it.activityInfo.name }

        /** Then **/
        assertTrue(receiverInfo.exported)
        assertEquals(listOf(AssistantTaskExactAlarmPermissionReceiver::class.java.name), matched)
    }
}
