package com.black.app.ui.common.selectapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.app.broadcast.PackageInstallTestFixture
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_LABEL
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_PACKAGE
import com.black.app.util.InstalledAppResolver
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [selectedAppUiItem]의 설치 앱 표시 모델 변환과 미설치 앱 대체 표시 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SelectedAppUiItemTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * 화면 표시용 변환이 설치된 앱은 라벨·아이콘을, 미설치 앱은 packageName·기본 아이콘을 쓰는지 검증
     *
     * Given: 앱 하나 설치, 다른 패키지는 미설치
     * When: 두 packageName으로 selectedAppUiItem 호출
     * Then: 설치 앱은 설치 라벨·아이콘, 미설치 앱은 라벨이 packageName이고 아이콘이 기본 액티비티 아이콘
     */
    @Test
    fun test_01_selectedAppUiItemFallsBackWhenNotInstalled() = runTest {
        /** Given **/
        val missingPackage = "com.example.missing"
        PackageInstallTestFixture.installApp(context, LAUNCHER_PACKAGE, LAUNCHER_LABEL)
        val resolver = InstalledAppResolver(context)

        /** When **/
        val installedItem = resolver.selectedAppUiItem(LAUNCHER_PACKAGE)
        val missingItem = resolver.selectedAppUiItem(missingPackage)

        /** Then **/
        assertEquals(LAUNCHER_LABEL, installedItem.label)
        assertSame(context.packageManager.getApplicationIcon(LAUNCHER_PACKAGE), installedItem.icon)
        assertEquals(missingPackage, missingItem.packageName)
        assertEquals(missingPackage, missingItem.label)
        assertEquals(context.packageManager.defaultActivityIcon.javaClass, missingItem.icon.javaClass)
    }
}
