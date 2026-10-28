package com.black.app.broadcast

import android.app.Person as PlatformPerson
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_ACTIVITY
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_LABEL
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_PACKAGE
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** [PackageInstallShortcut]의 대화 알림용 바로가기 게시·정리와 실패 처리 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PackageInstallShortcutTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * 바로가기 게시 내용 검증
     *
     * Given: SDK 34, 런처 앱 설치
     * When: publish 호출
     * Then: true 반환, 동적 바로가기 1개, ID=PackageInstall:<패키지명>, 짧은 이름=앱 이름, 인텐트 대상=런처 액티비티, long-lived, 아이콘 있음, Person 이름=앱 이름, 런처 노출 제외 플래그 없음
     */
    @Test
    fun test_01_publishAddsLongLivedShortcutWithoutLauncherExclusion() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        val published = publishLauncherShortcut()

        /** Then **/
        assertTrue(published)
        val shortcut = PackageInstallTestFixture.dynamicShortcuts(context).single()
        assertEquals("PackageInstall:$LAUNCHER_PACKAGE", shortcut.id)
        assertEquals("PackageInstall:$LAUNCHER_PACKAGE", PackageInstallShortcut.shortcutId(LAUNCHER_PACKAGE))
        assertEquals(LAUNCHER_LABEL, shortcut.shortLabel.toString())
        assertEquals(ComponentName(LAUNCHER_PACKAGE, LAUNCHER_ACTIVITY), shortcut.intent?.component)
        assertTrue(isLongLived(shortcut))
        // 알림창 왼쪽 대화 아이콘·대화 상대의 원천, 숨김 API라 리플렉션으로 조회
        assertNotNull(ReflectionHelpers.callInstanceMethod<Icon>(shortcut, "getIcon"))
        val persons = ReflectionHelpers.callInstanceMethod<Array<PlatformPerson>>(shortcut, "getPersons")
        assertEquals(LAUNCHER_LABEL, persons.single().name.toString())
        // 런처 노출 제외 바로가기는 Android 13 이상 실기기 ShortcutService가 보관하지 않아 플래그 미지정
        assertFalse(shortcut.isExcludedFromSurfaces(ShortcutInfo.SURFACE_LAUNCHER))
    }

    /**
     * 알림 뒤 정리에서 자기 바로가기만 삭제 검증
     *
     * Given: SDK 34, 런처 앱 설치, 다른 기능의 동적 바로가기 other 게시, 바로가기 게시
     * When: removeAfterNotify 호출
     * Then: 동적 바로가기가 other 하나만 남음
     */
    @Test
    fun test_02_removeAfterNotifyRemovesOnlyOwnShortcut() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)
        val otherShortcut = ShortcutInfoCompat.Builder(context, "other")
            .setShortLabel("Other")
            .setIntent(Intent(Intent.ACTION_VIEW))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, otherShortcut)
        publishLauncherShortcut()

        /** When **/
        PackageInstallShortcut.removeAfterNotify(context, LAUNCHER_PACKAGE)

        /** Then **/
        assertEquals(listOf("other"), PackageInstallTestFixture.dynamicShortcuts(context).map { it.id })
    }

    /**
     * Android 12L 바로가기 게시 검증
     *
     * Given: SDK 32, 런처 앱 설치
     * When: publish 호출
     * Then: true 반환, 동적 바로가기 1개, ID=PackageInstall:<패키지명>, long-lived
     */
    @Test
    @Config(sdk = [32])
    fun test_03_publishOnAndroid12L() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        val published = publishLauncherShortcut()

        /** Then **/
        assertTrue(published)
        // 런처 노출 제외 플래그가 붙으면 androidx가 SDK 32 이하에서 게시를 건너뛰므로 게시 자체가 플래그 미지정 근거
        val shortcut = PackageInstallTestFixture.dynamicShortcuts(context).single()
        assertEquals("PackageInstall:$LAUNCHER_PACKAGE", shortcut.id)
        assertTrue(isLongLived(shortcut))
    }

    /**
     * 바로가기 개수 상한이 0이면 게시 실패 반환 검증
     *
     * Given: SDK 34, 런처 앱 설치, 활동별 최대 바로가기 수 0
     * When: publish 호출
     * Then: false 반환, 동적 바로가기 없음
     */
    @Test
    fun test_04_publishReturnsFalseWhenShortcutLimitIsZero() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)
        PackageInstallTestFixture.shadowShortcutManager(context).setMaxShortcutCountPerActivity(0)

        /** When **/
        val published = publishLauncherShortcut()

        /** Then **/
        assertFalse(published)
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * 바로가기 서비스 예외 시 예외 전파 없이 실패 반환 검증
     *
     * Given: SDK 34, 런처 앱 설치, pushDynamicShortcut이 IllegalStateException 발생
     * When: publish 호출
     * Then: false 반환, pushDynamicShortcut 1회 호출
     */
    @Test
    fun test_05_publishReturnsFalseWhenShortcutServiceThrows() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)
        mockkStatic(ShortcutManagerCompat::class)
        every { ShortcutManagerCompat.pushDynamicShortcut(any(), any()) } throws IllegalStateException("User is locked")

        /** When **/
        val published = publishLauncherShortcut()

        /** Then **/
        assertFalse(published)
        verify(exactly = 1) { ShortcutManagerCompat.pushDynamicShortcut(any(), any()) }
    }

    /**
     * 알림 뒤 정리에서 바로가기 서비스 예외를 전파하지 않음 검증
     *
     * Given: SDK 34, removeDynamicShortcuts가 IllegalStateException 발생
     * When: removeAfterNotify 호출
     * Then: 예외 없이 종료, removeDynamicShortcuts가 자기 바로가기 ID로 1회 호출
     */
    @Test
    fun test_06_removeAfterNotifyIgnoresShortcutServiceException() = runTest {
        /** Given **/
        mockkStatic(ShortcutManagerCompat::class)
        every { ShortcutManagerCompat.removeDynamicShortcuts(any(), any()) } throws IllegalStateException("User is locked")

        /** When **/
        PackageInstallShortcut.removeAfterNotify(context, LAUNCHER_PACKAGE)

        /** Then **/
        verify(exactly = 1) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf("PackageInstall:$LAUNCHER_PACKAGE"))
        }
    }

    /** 런처 앱의 실행 인텐트·앱 이름으로 바로가기 게시 */
    private fun publishLauncherShortcut(): Boolean {
        val launchIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(LAUNCHER_PACKAGE))
        val sender = Person.Builder().setName(LAUNCHER_LABEL).setKey(LAUNCHER_PACKAGE).build()
        return PackageInstallShortcut.publish(
            context,
            LAUNCHER_PACKAGE,
            LAUNCHER_LABEL,
            Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888),
            launchIntent,
            sender,
        )
    }

    /** ShortcutInfo.isLongLived는 숨김 API라 리플렉션으로 조회 */
    private fun isLongLived(shortcut: ShortcutInfo): Boolean =
        ReflectionHelpers.callInstanceMethod(shortcut, "isLongLived")
}
