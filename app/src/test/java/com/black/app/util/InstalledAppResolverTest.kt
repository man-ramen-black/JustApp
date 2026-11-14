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

/** [InstalledAppResolver]의 설치 앱 조회, 미설치 처리, [ApplicationInfo] 조회, 라벨만 조회 검증 */
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
     * 설치된 앱의 라벨만 조회하면 아이콘을 불러오지 않는지 검증
     *
     * Given: 라벨 "에이"로 설치된 com.a를 돌려주는 PackageManager
     * When: resolveLabel 호출
     * Then: "에이", 아이콘 조회 없음
     */
    @Test
    fun test_04_resolveLabelReturnsLabelWithoutIcon() = runTest {
        /** Given **/
        val applicationInfo = ApplicationInfo()
        val packageManager: PackageManager = mockk()
        val mockContext: Context = mockk()
        every { mockContext.packageManager } returns packageManager
        every { packageManager.getApplicationInfo("com.a", 0) } returns applicationInfo
        every { packageManager.getApplicationLabel(applicationInfo) } returns "에이"

        /** When **/
        val label = InstalledAppResolver(mockContext).resolveLabel("com.a")

        /** Then **/
        assertEquals("에이", label)
        verify(exactly = 0) { packageManager.getApplicationIcon(any<ApplicationInfo>()) }
    }

    /**
     * 설치되지 않은 앱의 라벨은 packageName으로 대신하는지 검증
     *
     * Given: 패키지 미설치
     * When: resolveLabel 호출
     * Then: packageName 그대로
     */
    @Test
    fun test_05_resolveLabelFallsBackToPackageName() = runTest {
        /** Given **/
        // 패키지를 설치하지 않음(별도 준비 없음)

        /** When **/
        val label = InstalledAppResolver(context).resolveLabel(LAUNCHER_PACKAGE)

        /** Then **/
        assertEquals(LAUNCHER_PACKAGE, label)
    }
}
