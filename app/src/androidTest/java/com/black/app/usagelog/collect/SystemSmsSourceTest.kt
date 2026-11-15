package com.black.app.usagelog.collect

import android.Manifest
import android.os.Build
import com.black.app.testutil.BaseUiTest
import com.black.app.usagelog.record.SmsRecord
import com.black.core.util.PermissionHelper
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [SystemSmsSource]가 실제 기기의 문자 저장소를 예외 없이 조회하고 규칙에 맞는 기록을 돌려주는지 확인하는 스모크 테스트
 *
 * 문자 본문·번호·이름·키·시각은 단언 메시지와 로그에 남기지 않음.
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SystemSmsSourceTest : BaseUiTest() {

    /**
     * 문자·연락처 읽기 권한을 허용하고, 테스트 전에 거부였던 권한만 teardown 뒤 회수 예약
     *
     * 계측이 개발 앱과 같은 프로세스에서 돌아 권한을 바로 회수하면 시스템이 그 프로세스를 종료해 테스트 실행이 끊김.
     * 그래서 계측이 끝나 프로세스가 내려갈 때 회수되는 revokeSelfPermissionsOnKill을 쓰고, 이 API가 없는 API 33 미만은
     * 거부였던 권한을 되돌릴 수 없어 건너뜀.
     * 앞 테스트가 예약한 회수는 프로세스가 끝날 때까지 남아 있어, 뒤 테스트가 허용으로 읽고 회수를 예약하지 않아도 결과는 테스트 전 상태.
     */
    override fun setup() {
        assumeTrue(
            "API 33 미만은 테스트 뒤 권한을 거부로 되돌릴 수 없어 건너뜀(문자·연락처 권한을 미리 허용하면 실행)",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || PERMISSIONS.all(::isGranted),
        )
        PERMISSIONS.forEach { permission ->
            preserveValue(
                read = { isGranted(permission) },
                restore = { wasGranted ->
                    // 테스트 전에 허용이었던 권한은 그대로 둠
                    if (!wasGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.revokeSelfPermissionsOnKill(listOf(permission))
                    }
                },
            )
            // 이미 허용된 권한이면 상태가 바뀌지 않음
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }
    }

    private fun isGranted(permission: String) = PermissionHelper.isGranted(context, permission)

    /**
     * 지난 30일 조회가 null이 아니고 각 기록이 시각 범위와 키 규칙을 지키는지 검증
     *
     * Given: 문자·연락처 읽기 권한 허용
     * When: 지난 30일 구간 query
     * Then: 결과가 null이 아니고, 각 기록의 시각이 시작 앞 1초 미만부터 끝 전까지, 키 접두사가 mms 값과 일치, attachments 0 이상
     */
    @Test
    fun test_01_queryLastThirtyDaysReturnsConsistentRecords() {
        /** Given **/
        val end = System.currentTimeMillis()
        val begin = end - THIRTY_DAYS_MILLIS

        /** When **/
        val records = SystemSmsSource(context).query(begin, end)

        /** Then **/
        assertNotNull("문자 조회가 null", records)
        println("[SystemSmsSourceTest] 조회 건수: ${records!!.size}")
        records.forEach { record -> assertConsistent(record, begin, end) }
    }

    private fun assertConsistent(record: SmsRecord, begin: Long, end: Long) {
        // MMS는 시작을 초로 내림해 조회하므로 시작 앞 1초 미만까지 허용
        assertTrue("시각이 범위 밖인 기록 있음", record.time > begin - ONE_SECOND_MILLIS && record.time < end)
        val expectedPrefix = if (record.mms) "mms:" else "sms:"
        assertTrue("키 접두사와 mms 값이 다른 기록 있음", record.messageKey.startsWith(expectedPrefix))
        assertTrue("첨부 수가 음수인 기록 있음", record.attachments >= 0)
    }

    /**
     * 조회 결과가 시각 오름차순인지 검증
     *
     * Given: 문자·연락처 읽기 권한 허용
     * When: 지난 30일 구간 query
     * Then: 기록 시각이 오름차순
     */
    @Test
    fun test_02_queryResultIsSortedByTime() {
        /** Given **/
        val end = System.currentTimeMillis()

        /** When **/
        val records = SystemSmsSource(context).query(end - THIRTY_DAYS_MILLIS, end)

        /** Then **/
        assertNotNull("문자 조회가 null", records)
        // 실패해도 실제 문자 시각 목록이 메시지에 남지 않도록 비교 결과만 단언
        val sorted = records!!.zipWithNext().all { (previous, next) -> previous.time <= next.time }
        assertTrue("시각 오름차순이 아닌 기록 있음", sorted)
    }

    private companion object {
        val PERMISSIONS = listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
        const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val ONE_SECOND_MILLIS = 1000L
    }
}
