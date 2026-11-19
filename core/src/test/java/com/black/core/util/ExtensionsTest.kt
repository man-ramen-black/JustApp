package com.black.core.util

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import com.black.core.util.Extensions.appLaunchIntent
import com.black.core.util.Extensions.fragmentActivity
import com.black.test.BaseTest
import com.black.test.TestUtil
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

/** [Extensions.fragmentActivity]의 ContextWrapper 탐색과 [Extensions.appLaunchIntent]의 시작 액티비티 조회 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ExtensionsTest : BaseTest() {

    private val application: Application = RuntimeEnvironment.getApplication()

    /**
     * FragmentActivity 자신이면 그대로 돌려주는지 검증
     *
     * Given: Robolectric으로 띄운 FragmentActivity
     * When: fragmentActivity 호출
     * Then: 같은 Activity 반환
     */
    @Test
    fun test_01_returnsSelfForFragmentActivity() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        /** When **/
        val found = activity.fragmentActivity()

        /** Then **/
        assertSame(activity, found)
    }

    /**
     * ContextWrapper로 여러 번 감싼 Context에서 안쪽 FragmentActivity를 찾는지 검증
     *
     * Given: FragmentActivity를 ContextWrapper로 두 번 감싼 Context
     * When: fragmentActivity 호출
     * Then: 감싼 FragmentActivity 반환
     */
    @Test
    fun test_02_findsFragmentActivityThroughContextWrapper() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val wrappedContext = ContextWrapper(ContextWrapper(activity))

        /** When **/
        val found = wrappedContext.fragmentActivity()

        /** Then **/
        assertSame(activity, found)
    }

    /**
     * FragmentActivity가 아닌 ComponentActivity면 null을 돌려주는지 검증
     *
     * Given: Robolectric으로 띄운 ComponentActivity를 ContextWrapper로 감싼 Context
     * When: fragmentActivity 호출
     * Then: null 반환
     */
    @Test
    fun test_03_returnsNullForComponentActivity() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val wrappedContext = ContextWrapper(activity)

        /** When **/
        val found = wrappedContext.fragmentActivity()

        /** Then **/
        assertNull(found)
    }

    /**
     * Activity가 없는 애플리케이션 Context면 null을 돌려주는지 검증
     *
     * Given: 애플리케이션 Context
     * When: fragmentActivity 호출
     * Then: null 반환
     */
    @Test
    fun test_04_returnsNullForApplicationContext() = runTest {
        /** Given / When **/
        val found = application.fragmentActivity()

        /** Then **/
        assertNull(found)
    }

    /**
     * 시작 액티비티가 있으면 그 액티비티를 여는 인텐트를 돌려주는지 검증
     *
     * Given: 이 앱에 MAIN·LAUNCHER 액티비티 추가
     * When: appLaunchIntent 호출
     * Then: 추가한 액티비티를 대상으로 하는 인텐트
     */
    @Test
    fun test_05_appLaunchIntentTargetsLauncherActivity() = runTest {
        /** Given **/
        TestUtil.addLauncherActivity(application, application.packageName, LAUNCHER_ACTIVITY)

        /** When **/
        val intent = application.appLaunchIntent()

        /** Then **/
        assertEquals(ComponentName(application.packageName, LAUNCHER_ACTIVITY), intent?.component)
    }

    /**
     * 시작 액티비티가 없으면 null을 돌려주는지 검증
     *
     * Given: 시작 액티비티가 없는 core 테스트 매니페스트
     * When: appLaunchIntent 호출
     * Then: null 반환
     */
    @Test
    fun test_06_appLaunchIntentIsNullWithoutLauncherActivity() = runTest {
        /** Given / When **/
        val intent = application.appLaunchIntent()

        /** Then **/
        assertNull(intent)
    }

    private companion object {
        const val LAUNCHER_ACTIVITY = "com.black.core.test.LauncherActivity"
    }
}
