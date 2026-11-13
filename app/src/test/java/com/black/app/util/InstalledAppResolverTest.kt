package com.black.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.black.app.broadcast.PackageInstallTestFixture
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_LABEL
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_PACKAGE
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [InstalledAppResolver]의 설치 앱 조회, 미설치 처리, [ApplicationInfo] 조회 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class InstalledAppResolverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * 설치된 앱을 packageName으로 찾으면 라벨·아이콘을 돌려주는지 검증
     *
     * Given: 라벨과 아이콘을 가진 앱 설치
     * When: find(packageName) 호출
     * Then: packageName, 라벨, 아이콘이 설치한 앱과 같음
     */
    @Test
    fun test_01_findReturnsInstalledApp() = runTest {
        /** Given **/
        PackageInstallTestFixture.installApp(context, LAUNCHER_PACKAGE, LAUNCHER_LABEL)

        /** When **/
        val installedApp = InstalledAppResolver(context).find(LAUNCHER_PACKAGE)

        /** Then **/
        assertEquals(LAUNCHER_PACKAGE, installedApp?.packageName)
        assertEquals(LAUNCHER_LABEL, installedApp?.label)
        assertSame(context.packageManager.getApplicationIcon(LAUNCHER_PACKAGE), installedApp?.icon)
    }

    /**
     * 설치되지 않은 packageName은 예외 없이 null을 돌려주는지 검증
     *
     * Given: 패키지 미설치
     * When: find(packageName) 호출
     * Then: null
     */
    @Test
    fun test_02_findReturnsNullWhenNotInstalled() = runTest {
        /** Given **/
        // 패키지를 설치하지 않음(별도 준비 없음)

        /** When **/
        val installedApp = InstalledAppResolver(context).find(LAUNCHER_PACKAGE)

        /** Then **/
        assertNull(installedApp)
    }

    /**
     * 이미 가진 ApplicationInfo로 조회하면 패키지 정보를 다시 조회하지 않는지 검증
     *
     * Given: ApplicationInfo로 라벨·아이콘만 돌려주는 PackageManager
     * When: find(applicationInfo) 호출
     * Then: ApplicationInfo의 packageName과 조회한 라벨·아이콘 반환, getApplicationInfo 미호출
     */
    @Test
    fun test_03_findWithApplicationInfoSkipsPackageLookup() = runTest {
        /** Given **/
        val applicationInfo = ApplicationInfo().apply { packageName = LAUNCHER_PACKAGE }
        val icon = ColorDrawable()
        val packageManager: PackageManager = mockk()
        val mockContext: Context = mockk()
        every { mockContext.packageManager } returns packageManager
        every { packageManager.getApplicationLabel(applicationInfo) } returns LAUNCHER_LABEL
        every { packageManager.getApplicationIcon(applicationInfo) } returns icon

        /** When **/
        val installedApp = InstalledAppResolver(mockContext).find(applicationInfo)

        /** Then **/
        assertEquals(InstalledApp(LAUNCHER_PACKAGE, LAUNCHER_LABEL, icon), installedApp)
        verify(exactly = 0) { packageManager.getApplicationInfo(any<String>(), any<Int>()) }
    }

    /**
     * 화면 표시용 조회가 설치된 앱은 라벨·아이콘을, 미설치 앱은 packageName·기본 아이콘을 쓰는지 검증
     *
     * Given: 앱 하나 설치, 다른 패키지는 미설치
     * When: 두 packageName으로 resolve 호출
     * Then: 설치 앱은 설치 라벨·아이콘, 미설치 앱은 라벨이 packageName이고 아이콘이 기본 액티비티 아이콘
     */
    @Test
    fun test_04_resolveFallsBackWhenNotInstalled() = runTest {
        /** Given **/
        val missingPackage = "com.example.missing"
        PackageInstallTestFixture.installApp(context, LAUNCHER_PACKAGE, LAUNCHER_LABEL)
        val resolver = InstalledAppResolver(context)

        /** When **/
        val installedItem = resolver.resolve(LAUNCHER_PACKAGE)
        val missingItem = resolver.resolve(missingPackage)

        /** Then **/
        assertEquals(LAUNCHER_LABEL, installedItem.label)
        assertSame(context.packageManager.getApplicationIcon(LAUNCHER_PACKAGE), installedItem.icon)
        assertEquals(missingPackage, missingItem.packageName)
        assertEquals(missingPackage, missingItem.label)
        assertEquals(context.packageManager.defaultActivityIcon.javaClass, missingItem.icon.javaClass)
    }
}
