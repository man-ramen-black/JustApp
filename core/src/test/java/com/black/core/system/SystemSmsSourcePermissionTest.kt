package com.black.core.system

import android.Manifest
import android.content.Context
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

/**
 * [SystemSmsSource]의 문자 읽기 권한에 따른 조회 결과 검증
 *
 * 실제 문자 저장소 조회는 기기가 필요해 앱 계측 테스트 `SystemSmsSourceTest`가 맡음.
 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SystemSmsSourcePermissionTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * 문자 읽기 권한이 거부되면 null인지 검증
     *
     * Given: READ_SMS 거부
     * When: query 호출
     * Then: null
     */
    @Test
    fun test_01_smsPermissionDeniedReturnsNull() = runTest {
        /** Given **/
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_SMS)

        /** When **/
        val messages = SystemSmsSource(context).query(0, 10_000)

        /** Then **/
        assertNull(messages)
    }

    /**
     * 문자 읽기 권한이 허용되고 문자 저장소가 비면 null이 아닌 빈 목록인지 검증
     *
     * Given: READ_SMS 허용, READ_CONTACTS 거부, 문자 저장소 제공자가 없어 조회 커서가 null
     * When: query 호출
     * Then: 빈 목록
     */
    @Test
    fun test_02_smsPermissionGrantedWithEmptyStoreReturnsEmptyList() = runTest {
        /** Given **/
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_SMS)
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS)

        /** When **/
        val messages = SystemSmsSource(context).query(0, 10_000)

        /** Then **/
        assertEquals(emptyList<SmsMessage>(), messages)
    }
}
