package com.black.app.broadcast

import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_PACKAGE
import com.black.app.ui.MainActivity
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [PackageInstallReceiver]의 수신 분기와 ForegroundService 수명에 맞춘 등록·해제 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PackageInstallReceiverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** 알림 권한 허용, 알림 대상 런처 앱 설치 */
    @Before
    fun setupInstalledApp() {
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)
    }

    /** 싱글턴 상태 격리를 위한 각 테스트 후 리시버 해제 */
    @After
    fun unregisterReceiver() {
        PackageInstallReceiver.unregister(context)
    }

    /**
     * 등록 후 새 설치 브로드캐스트 수신 시 설치 알림 표시 검증
     *
     * Given: 리시버 등록
     * When: EXTRA_REPLACING=false인 ACTION_PACKAGE_ADDED 전송
     * Then: 해당 패키지 알림 메시지=설치되었습니다.
     */
    @Test
    fun test_01_packageAddedShowsInstalledNotification() = runTest {
        /** Given **/
        PackageInstallReceiver.register(context)

        /** When **/
        context.sendBroadcast(packageAddedIntent(LAUNCHER_PACKAGE, isReplacing = false))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        val notification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification(LAUNCHER_PACKAGE.hashCode())
        assertEquals("설치되었습니다.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    /**
     * 업데이트 브로드캐스트 수신 시 업데이트 알림 표시 검증
     *
     * Given: 리시버 등록
     * When: EXTRA_REPLACING=true인 ACTION_PACKAGE_ADDED 전송
     * Then: 해당 패키지 알림 메시지=업데이트되었습니다.
     */
    @Test
    fun test_02_replacingPackageShowsUpdatedNotification() = runTest {
        /** Given **/
        PackageInstallReceiver.register(context)

        /** When **/
        context.sendBroadcast(packageAddedIntent(LAUNCHER_PACKAGE, isReplacing = true))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        val notification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification(LAUNCHER_PACKAGE.hashCode())
        assertEquals("업데이트되었습니다.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    /**
     * 자기 앱 설치·업데이트는 무시 검증
     *
     * Given: 리시버 등록, 자기 패키지에 런처 액티비티·아이콘 구성(가드가 없으면 알림이 뜨는 조건)
     * When: 자기 패키지명으로 ACTION_PACKAGE_ADDED 전송
     * Then: 알림 없음
     */
    @Test
    fun test_03_ownPackageIgnored() = runTest {
        /** Given **/
        PackageInstallTestFixture.addLauncherActivity(context, context.packageName, MainActivity::class.java.name)
        shadowOf(context.packageManager).setApplicationIcon(context.packageName, ColorDrawable(Color.RED))
        assertNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        PackageInstallReceiver.register(context)

        /** When **/
        context.sendBroadcast(packageAddedIntent(context.packageName, isReplacing = true))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * data URI 없는 인텐트는 예외 없이 무시 검증
     *
     * Given: 리시버 인스턴스
     * When: data 없는 ACTION_PACKAGE_ADDED로 onReceive 직접 호출(필터의 package scheme을 거치지 않는 경로)
     * Then: 알림 없음
     */
    @Test
    fun test_04_intentWithoutDataIgnored() = runTest {
        /** Given **/
        val receiver = PackageInstallReceiver()

        /** When **/
        receiver.onReceive(context, Intent(Intent.ACTION_PACKAGE_ADDED))

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * 해제 없이 재등록 시 리시버 1개 유지 검증
     *
     * Given / When: register 2회 호출
     * Then: 등록된 PackageInstallReceiver 1개
     */
    @Test
    fun test_05_registerTwiceKeepsSingleReceiver() = runTest {
        /** Given / When **/
        PackageInstallReceiver.register(context)
        PackageInstallReceiver.register(context)

        /** Then **/
        assertEquals(1, registeredPackageInstallReceiverCount())
    }

    /**
     * 해제 후 수신 중단 검증
     *
     * Given: 리시버 등록 후 해제
     * When: ACTION_PACKAGE_ADDED 전송
     * Then: 알림 없음, 등록된 리시버 없음
     */
    @Test
    fun test_06_destroyStopsReceiving() = runTest {
        /** Given **/
        PackageInstallReceiver.register(context)
        PackageInstallReceiver.unregister(context)

        /** When **/
        context.sendBroadcast(packageAddedIntent(LAUNCHER_PACKAGE, isReplacing = false))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
        assertEquals(0, registeredPackageInstallReceiverCount())
    }

    /**
     * 등록 없이 해제 호출 시 예외 없음 검증
     *
     * Given: 등록 없음
     * When: unregister 호출
     * Then: 예외 없음, 등록된 리시버 없음
     */
    @Test
    fun test_07_destroyWithoutCreateDoesNothing() = runTest {
        /** Given / When **/
        PackageInstallReceiver.unregister(context)

        /** Then **/
        assertEquals(0, registeredPackageInstallReceiverCount())
    }

    /**
     * 설치·업데이트 외 액션은 무시 검증
     *
     * Given: 리시버 인스턴스
     * When: ACTION_PACKAGE_REMOVED로 onReceive 직접 호출
     * Then: 알림 없음
     */
    @Test
    fun test_08_otherActionIgnored() = runTest {
        /** Given **/
        val receiver = PackageInstallReceiver()

        /** When **/
        receiver.onReceive(context, Intent(Intent.ACTION_PACKAGE_REMOVED, Uri.fromParts("package", LAUNCHER_PACKAGE, null)))

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /** data가 package:<패키지명>인, 시스템이 보내는 형식의 설치·업데이트 인텐트 */
    private fun packageAddedIntent(packageName: String, isReplacing: Boolean): Intent =
        Intent(Intent.ACTION_PACKAGE_ADDED, Uri.fromParts("package", packageName, null))
            .putExtra(Intent.EXTRA_REPLACING, isReplacing)

    /** 애플리케이션에 런타임 등록된 PackageInstallReceiver 개수 */
    private fun registeredPackageInstallReceiverCount(): Int =
        shadowOf(context.applicationContext as Application).registeredReceivers
            .count { it.broadcastReceiver is PackageInstallReceiver }
}
