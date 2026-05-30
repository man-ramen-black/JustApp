package com.black.app.testutil

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * 모든 계측(UI) 테스트의 공통 베이스.
 *
 * 하위 클래스에서의 어노테이션 처리:
 * - [RunWith]는 @Inherited 라서 하위 클래스에서 생략할 수 있다.
 * - @HiltAndroidTest는 상속되지 않고(클래스별로 테스트 컴포넌트가 생성됨) 하위 클래스마다 반드시 직접 선언해야 한다.
 * - @FixMethodOrder도 상속되지 않으므로, 메서드 실행 순서를 강제하려면 하위 클래스마다 직접 선언해야 한다.
 *
 * 제공 기능:
 * - [hiltRule]: Hilt 주입 규칙. [setup] 호출 전에 inject()가 먼저 수행된다.
 * - [launchHost]/[launchActivity]: 실행한 [ActivityScenario]를 보관해 [teardown]에서 자동으로 close 한다.
 * - [setup]/[teardown]: 하위 클래스가 override 하여 확장하는 훅. super 호출이 필요 없도록 프레임워크 훅과 분리했다.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseUiTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private var scenario: ActivityScenario<out Activity>? = null

    @Before
    fun baseSetup() {
        hiltRule.inject()
        setup()
    }

    @After
    fun baseTeardown() {
        teardown()
        scenario?.close()
        scenario = null
    }

    /**
     * [HiltTestActivity]를 실행하고 scenario를 보관한다. teardown에서 자동으로 close 된다.
     */
    protected fun launchHost(): ActivityScenario<HiltTestActivity> {
        return launchActivity(HiltTestActivity::class.java)
    }

    /**
     * [activityClass]를 실행하고 scenario를 보관한다. teardown에서 자동으로 close 된다.
     * 이미 실행 중인 scenario가 있으면 먼저 close 한다.
     */
    protected fun <A : Activity> launchActivity(activityClass: Class<A>): ActivityScenario<A> {
        scenario?.close()
        return ActivityScenario.launch(activityClass).also { scenario = it }
    }

    /**
     * [HiltTestActivity]를 실행한 뒤 액티비티 인스턴스로 [block]을 실행한다.
     * scenario는 보관되어 teardown에서 자동으로 close 된다.
     */
    protected fun onHostActivity(block: (HiltTestActivity) -> Unit) {
        launchHost().onActivity(block)
    }

    /** 하위 클래스 확장용 셋업 훅. hiltRule.inject() 이후 호출된다. */
    protected open fun setup() {}

    /** 하위 클래스 확장용 정리 훅. scenario close 이전에 호출된다. */
    protected open fun teardown() {}
}
