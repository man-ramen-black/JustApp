package com.black.app.ui.navigation

import androidx.navigation.navOptions
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [applyDefaultNavOptions]의 기본 화면 이동 옵션 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppNavOptionsTest : BaseTest() {

    /**
     * 기본 옵션이 맨 위 중복 방지와 상태 복원을 켜는지 검증
     *
     * Given: 빈 옵션 빌더
     * When: applyDefaultNavOptions 적용
     * Then: launchSingleTop true, restoreState true
     */
    @Test
    fun test_01_defaultOptionsEnableSingleTopAndRestoreState() = runTest {
        /** Given / When **/
        val options = navOptions { applyDefaultNavOptions() }

        /** Then **/
        assertTrue(options.shouldLaunchSingleTop())
        assertTrue(options.shouldRestoreState())
    }

    /**
     * 기본 옵션 뒤에 지정한 popUpTo가 함께 유지되는지 검증
     *
     * Given: 딥링크의 백스택 비우기처럼 Main까지 비우는 popUpTo
     * When: applyDefaultNavOptions 적용 뒤 popUpTo<AppRoute.Main>(inclusive false) 지정
     * Then: 기본 옵션 유지, popUpTo 대상 AppRoute.Main, inclusive false
     */
    @Test
    fun test_02_defaultOptionsKeepPopUpTo() = runTest {
        /** Given / When **/
        val options = navOptions {
            applyDefaultNavOptions()
            popUpTo<AppRoute.Main> { inclusive = false }
        }

        /** Then **/
        assertTrue(options.shouldLaunchSingleTop())
        assertTrue(options.shouldRestoreState())
        assertEquals(AppRoute.Main::class, options.popUpToRouteClass)
        assertFalse(options.isPopUpToInclusive())
    }
}
