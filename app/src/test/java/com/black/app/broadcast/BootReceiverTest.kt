package com.black.app.broadcast

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [BootReceiver]의 action 확인 동작 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BootReceiverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** 알림 권한 허용 */
    @Before
    fun setupNotificationPermission() {
        PackageInstallTestFixture.grantNotificationPermission(context)
    }

    /**
     * 부팅 완료가 아닌 action은 처리하지 않는지 검증
     *
     * Given: 알림 없음
     * When: 다른 action으로 BootReceiver를 지정한 명시적 브로드캐스트 전송
     * Then: 부팅 알림이 표시되지 않음
     */
    @Test
    fun test_01_otherActionIsIgnored() = runTest {
        /** Given **/
        // 알림 권한만 허용(별도 준비 없음)

        /** When **/
        context.sendBroadcast(Intent("com.other.ACTION").setClass(context, BootReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * 부팅 완료 action은 부팅 알림을 표시하는지 검증
     *
     * Given: 알림 없음
     * When: BOOT_COMPLETED action으로 BootReceiver를 지정한 브로드캐스트 전송
     * Then: 부팅 알림 1개 표시
     */
    @Test
    fun test_02_bootCompletedShowsNotification() = runTest {
        /** Given **/
        // 알림 권한만 허용(별도 준비 없음)

        /** When **/
        context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(context, BootReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }
}
