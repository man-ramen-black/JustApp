package com.black.app.ui.common.selectapp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [InstalledAppResolver]의 라벨 조회 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class InstalledAppResolverTest : BaseTest() {

    private val packageManager = mockk<PackageManager>()
    private val context = mockk<Context> { every { packageManager } returns this@InstalledAppResolverTest.packageManager }
    private val resolver = InstalledAppResolver(context)

    /**
     * 설치된 앱은 라벨을 돌려주고 아이콘은 조회하지 않는지 검증
     *
     * Given: com.a가 라벨 "에이"로 설치됨
     * When: resolveLabel
     * Then: "에이", 아이콘 조회 없음
     */
    @Test
    fun test_01_resolveLabelReturnsLabelWithoutIcon() {
        /** Given **/
        val info = ApplicationInfo()
        every { packageManager.getApplicationInfo("com.a", 0) } returns info
        every { packageManager.getApplicationLabel(info) } returns "에이"

        /** When / Then **/
        assertEquals("에이", resolver.resolveLabel("com.a"))
        verify(exactly = 0) { packageManager.getApplicationIcon(any<ApplicationInfo>()) }
    }

    /**
     * 설치되지 않은 앱은 패키지 이름을 돌려주는지 검증
     *
     * Given: com.gone이 설치되지 않음
     * When: resolveLabel
     * Then: "com.gone"
     */
    @Test
    fun test_02_resolveLabelFallsBackToPackageName() {
        /** Given **/
        every { packageManager.getApplicationInfo("com.gone", 0) } throws PackageManager.NameNotFoundException()

        /** When / Then **/
        assertEquals("com.gone", resolver.resolveLabel("com.gone"))
    }
}
