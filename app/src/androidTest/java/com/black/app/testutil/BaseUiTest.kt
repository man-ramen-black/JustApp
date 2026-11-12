package com.black.app.testutil

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
 * - [composeRule]: Compose UI 검증 규칙. 호스팅 없는 빈 규칙이라 Compose 미사용 테스트에도 무해하다.
 * - [assertTagDisplayed]·[clickTag]·[waitUntilTagExists]·[waitUntilTagMatches] 등: testTag·텍스트 기준으로 반복되는 Compose 검증·조작 헬퍼
 * - [launchHost]/[launchActivity]: 실행한 [ActivityScenario]를 보관해 [teardown]에서 자동으로 close 한다.
 * - [putSetting]/[setAppOpMode]: 기기 설정·앱 작업 변경. 테스트 전 값을 보관해 [teardown] 뒤 자동 복원
 * - [setup]/[teardown]: 하위 클래스가 override 하여 확장하는 훅. super 호출이 필요 없도록 프레임워크 훅과 분리했다.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    /** 계측 인스트루먼테이션 */
    protected val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** UiAutomator 디바이스 핸들 */
    protected val device = UiDevice.getInstance(instrumentation)

    /** 애플리케이션 컨텍스트 */
    protected val context = ApplicationProvider.getApplicationContext<Context>()

    private var scenario: ActivityScenario<out Activity>? = null

    /** [putSetting]으로 바꾼 기기 설정의 테스트 전 값. 키는 namespace와 설정 키, 값이 없던 설정은 null */
    private val originalSettings = LinkedHashMap<Pair<String, String>, String?>()

    /** [setAppOpMode]로 바꾼 이 앱의 앱 작업별 테스트 전 모드 */
    private val originalAppOpModes = LinkedHashMap<String, String>()

    @Before
    fun baseSetup() {
        hiltRule.inject()
        setup()
    }

    @After
    fun baseTeardown() {
        try {
            teardown()
        } finally {
            // 하위 클래스 정리가 실패해도 사용자 기기 설정은 테스트 전 값으로 복원
            restoreDeviceState()
            scenario?.close()
            scenario = null
        }
    }

    /** [HiltTestActivity]를 실행하고 scenario를 보관한다. teardown에서 자동으로 close 된다. */
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

    /**
     * testTag가 [tag]인 노드가 화면에 표시되는지 검증
     *
     * [scrollTo]가 true면 먼저 화면에 보이도록 스크롤
     */
    protected fun assertTagDisplayed(tag: String, scrollTo: Boolean = false): SemanticsNodeInteraction {
        val node = composeRule.onNodeWithTag(tag)
        if (scrollTo) {
            node.performScrollTo()
        }
        return node.assertIsDisplayed()
    }

    /**
     * testTag가 [tag]인 노드가 컴포지션에 있는지 검증
     *
     * 스크롤 밖이라 화면에 보이지 않아도 통과
     */
    protected fun assertTagExists(tag: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(tag).assertExists()

    /** testTag가 [tag]인 노드가 컴포지션에 없는지 검증 */
    protected fun assertTagNotExists(tag: String) {
        composeRule.onNodeWithTag(tag).assertDoesNotExist()
    }

    /**
     * testTag가 [tag]인 노드의 텍스트가 [text]와 일치하는지 검증
     *
     * [useUnmergedTree]가 true면 부모에 병합되지 않은 트리에서 노드를 찾음(입력 칸의 placeholder처럼 자손에 달린 태그)
     */
    protected fun assertTagText(
        tag: String,
        text: String,
        useUnmergedTree: Boolean = false,
    ): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(tag, useUnmergedTree).assertTextEquals(text)

    /**
     * testTag가 [tag]인 입력 칸의 텍스트를 [text]로 교체
     *
     * 칸의 기존 값 위에 덮어씀.
     */
    protected fun replaceTagText(tag: String, text: String): SemanticsNodeInteraction {
        val node = composeRule.onNodeWithTag(tag)
        node.performTextReplacement(text)
        return node
    }

    /**
     * testTag가 [tag]인 노드가 [matcher]를 만족하는지 검증
     *
     * 스위치 켜짐(`isOn`)·클릭 가능 여부(`hasClickAction`)처럼 텍스트 밖의 의미 검증에 사용
     */
    protected fun assertTagMatches(tag: String, matcher: SemanticsMatcher): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(tag).assert(matcher)

    /**
     * testTag가 [tag]인 노드의 텍스트에 [texts]가 모두 있는지 검증
     *
     * 자손 의미를 병합한 노드(라벨 + 값 행 등)는 자손 텍스트까지 검사. [substring]이 true면 부분 일치,
     * [scrollTo]가 true면 먼저 화면에 보이도록 스크롤
     */
    protected fun assertTagTextContains(
        tag: String,
        vararg texts: String,
        substring: Boolean = false,
        scrollTo: Boolean = false,
    ): SemanticsNodeInteraction {
        val node = composeRule.onNodeWithTag(tag)
        if (scrollTo) {
            node.performScrollTo()
        }
        texts.forEach { node.assertTextContains(it, substring = substring) }
        return node
    }

    /**
     * [text]를 가진 노드가 화면에 표시되는지 검증
     *
     * [scrollTo]가 true면 먼저 화면에 보이도록 스크롤
     */
    protected fun assertTextDisplayed(text: String, scrollTo: Boolean = false): SemanticsNodeInteraction {
        val node = composeRule.onNodeWithText(text)
        if (scrollTo) {
            node.performScrollTo()
        }
        return node.assertIsDisplayed()
    }

    /**
     * testTag가 [tag]인 노드 클릭
     *
     * [scrollTo]가 true면 먼저 화면에 보이도록 스크롤
     */
    protected fun clickTag(tag: String, scrollTo: Boolean = false): SemanticsNodeInteraction {
        val node = composeRule.onNodeWithTag(tag)
        if (scrollTo) {
            node.performScrollTo()
        }
        return node.performClick()
    }

    /** [text]를 가진 노드 클릭 */
    protected fun clickText(text: String): SemanticsNodeInteraction =
        composeRule.onNodeWithText(text).performClick()

    /**
     * testTag가 [tag]인 노드가 생길 때까지 최대 [timeoutMillis] 대기
     *
     * 비동기 로드가 끝난 뒤 표시되는 다이얼로그처럼 바로 검증할 수 없는 노드에 사용
     */
    protected fun waitUntilTagExists(tag: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * testTag가 [tag]이고 [matcher]를 만족하는 노드가 생길 때까지 최대 [timeoutMillis] 대기
     *
     * [matcher]는 확인할 때마다 다시 만들어, 서비스 실행 상태처럼 대기 중에 바뀌는 기대값도 반영.
     * DataStore 읽기처럼 Compose 유휴 대기가 기다리지 않는 비동기 상태 반영에 사용.
     * [useUnmergedTree]가 true면 부모에 병합되지 않은 트리에서 노드를 찾음
     */
    protected fun waitUntilTagMatches(
        tag: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        useUnmergedTree: Boolean = false,
        matcher: () -> SemanticsMatcher,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasTestTag(tag) and matcher(), useUnmergedTree)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** [text]를 가진 노드가 생길 때까지 최대 [timeoutMillis] 대기 */
    protected fun waitUntilTextExists(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 기기 설정 [namespace](system·secure·global)의 [key] 값, 값이 없으면 null */
    protected fun getSetting(namespace: String, key: String): String? {
        val value = device.executeShellCommand("settings get $namespace $key").trim()
        // 값이 없는 설정은 "null"로 출력
        return value.takeUnless { it.isEmpty() || it == "null" }
    }

    /**
     * 기기 설정 [namespace]의 [key]를 [value]로 변경
     *
     * 처음 바꿀 때 테스트 전 값을 보관해 [teardown] 뒤 자동으로 되돌림(값이 없던 설정은 삭제).
     * shell 명령은 셸을 거치지 않고 공백으로만 나뉘므로 [value]에 공백·따옴표를 쓰지 않음
     */
    protected fun putSetting(namespace: String, key: String, value: String) {
        val settingKey = namespace to key
        if (settingKey !in originalSettings) {
            originalSettings[settingKey] = getSetting(namespace, key)
        }
        device.executeShellCommand("settings put $namespace $key $value")
    }

    /**
     * 이 앱의 앱 작업 [operation](SYSTEM_ALERT_WINDOW 등)을 [mode](allow 등)로 변경
     *
     * shell 권한으로 권한 화면 없이 직접 변경. 처음 바꿀 때 테스트 전 모드를 보관해 [teardown] 뒤 자동으로 되돌림
     */
    protected fun setAppOpMode(operation: String, mode: String) {
        if (operation !in originalAppOpModes) {
            originalAppOpModes[operation] = getAppOpMode(operation)
        }
        device.executeShellCommand("appops set ${context.packageName} $operation $mode")
    }

    /**
     * 이 앱의 앱 작업 [operation] 패키지 모드
     *
     * appops get 출력의 `OPERATION: mode; time=...` 줄에서 모드만 추출, 따로 정한 모드가 없으면(No operations.) default.
     * `Uid mode: OPERATION: mode` 줄은 appops set이 바꾸지 않는 uid 모드라 제외
     */
    private fun getAppOpMode(operation: String): String {
        return device.executeShellCommand("appops get ${context.packageName} $operation")
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$operation:") }
            ?.substringAfter(':')
            ?.substringBefore(';')
            ?.trim()
            ?: APP_OP_MODE_DEFAULT
    }

    /** [putSetting]·[setAppOpMode]로 바꾼 기기 설정·앱 작업을 테스트 전 값으로 복원(설정은 바꾼 순서의 역순) */
    private fun restoreDeviceState() {
        originalSettings.entries.reversed().forEach { (settingKey, value) ->
            val (namespace, key) = settingKey
            if (value == null) {
                device.executeShellCommand("settings delete $namespace $key")
            } else {
                device.executeShellCommand("settings put $namespace $key $value")
            }
        }
        originalSettings.clear()
        originalAppOpModes.forEach { (operation, mode) ->
            device.executeShellCommand("appops set ${context.packageName} $operation $mode")
        }
        originalAppOpModes.clear()
    }

    /** 하위 클래스 확장용 셋업 훅. hiltRule.inject() 이후 호출된다. */
    protected open fun setup() {}

    /** 하위 클래스 확장용 정리 훅. scenario close 이전에 호출된다. */
    protected open fun teardown() {}

    companion object {
        /** [waitUntilTagExists]·[waitUntilTagMatches]·[waitUntilTextExists] 기본 대기 시간 */
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L

        /** 앱 작업에 따로 정한 모드가 없을 때의 모드 이름(appops set으로 되돌릴 때도 사용) */
        private const val APP_OP_MODE_DEFAULT = "default"
    }
}
