package com.black.app.usagelog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** 기기 사용 기록 수집에 필요한 권한이 매니페스트에 선언됐는지 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogManifestTest : BaseTest() {

    /** 병합된 매니페스트가 요청하는 권한 목록 */
    private fun requestedPermissions(): List<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return packageInfo.requestedPermissions?.toList().orEmpty()
    }

    /**
     * 사용 정보 접근 권한이 선언됐는지 검증
     *
     * 선언이 없으면 설정 앱의 사용 정보 접근 화면에서 앱을 허용할 수 없음
     *
     * Given: 병합된 앱 매니페스트
     * When: 요청 권한 목록 조회
     * Then: PACKAGE_USAGE_STATS 포함
     */
    @Test
    fun test_01_declaresUsageStatsPermission() = runTest {
        /** Given / When **/
        val permissions = requestedPermissions()

        /** Then **/
        assertTrue(permissions.contains(Manifest.permission.PACKAGE_USAGE_STATS))
    }

    /**
     * Wi-Fi 이름을 읽는 데 필요한 위치 권한 세 가지가 선언됐는지 검증
     *
     * Given: 병합된 앱 매니페스트
     * When: 요청 권한 목록 조회
     * Then: 대략적인 위치, 정확한 위치, 백그라운드 위치 포함
     */
    @Test
    fun test_02_declaresLocationPermissions() = runTest {
        /** Given / When **/
        val permissions = requestedPermissions()

        /** Then **/
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }
}
