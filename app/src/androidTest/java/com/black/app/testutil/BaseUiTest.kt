package com.black.app.testutil

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
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
 * - [waitUntil]·[waitUntilFlowValue]: Compose 노드가 아닌 조건·Flow 값 대기
 * - [assumeUnlocked]: 화면 켜기·잠금 해제 후 잠겨 있으면 테스트 건너뜀
 * - [launchHost]/[launchActivity]: 실행한 [ActivityScenario]를 보관해 [teardown]에서 자동으로 close 한다.
 * - [currentActivity]: 보관 중인 scenario의 현재 Activity 인스턴스
 * - [putSetting]/[setAppOpMode]: 기기 설정·앱 작업 변경. 테스트 전 값을 보관해 [teardown] 뒤 자동 복원
 * - [preserveValue]: 앱 저장값·싱글톤 상태의 테스트 전 값 보관. 기기 설정과 같은 단계에서 등록 역순으로 자동 복원
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

    /** [putSetting]·[setAppOpMode]·[preserveValue]가 등록한 테스트 전 값 복원 작업, [restoreOriginalValues]가 등록 역순으로 실행 */
    private val restoreActions = mutableListOf<suspend () -> Unit>()

    /** [putSetting]으로 바꾼 기기 설정(namespace와 설정 키), 처음 바꿀 때만 테스트 전 값을 보관하는 데 사용 */
    private val changedSettings = HashSet<Pair<String, String>>()

    /** [setAppOpMode]로 바꾼 이 앱의 앱 작업, 처음 바꿀 때만 테스트 전 모드를 보관하는 데 사용 */
    private val changedAppOperations = HashSet<String>()

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
            try {
                // 하위 클래스 정리가 실패해도 사용자 기기 설정·앱 저장값은 테스트 전 값으로 복원
                restoreOriginalValues()
            } finally {
                scenario?.close()
                scenario = null
            }
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
     * [launchHost]·[launchActivity]로 실행해 보관 중인 scenario의 현재 Activity 인스턴스
     *
     * 재생성(recreate)되면 새 인스턴스로 바뀌므로 필드에 보관하지 않고 쓸 때마다 호출.
     * 테스트 스레드에서 호출하고, 돌려받은 Activity의 창·뷰 상태는 [instrumentation]의 runOnMainSync 안에서 읽음
     */
    protected fun currentActivity(): Activity {
        // launchActivity가 어떤 Activity 타입으로 실행했든 Activity로만 다루므로 안전한 캐스팅
        @Suppress("UNCHECKED_CAST")
        val currentScenario = checkNotNull(scenario) { "실행한 Activity가 없습니다(launchHost·launchActivity 먼저 호출)" }
            as ActivityScenario<Activity>
        var activity: Activity? = null
        currentScenario.onActivity { activity = it }
        return checkNotNull(activity)
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
     * testTag가 [tag]인 입력 칸의 커서 위치에 [text] 입력
     *
     * 칸의 기존 값을 지우지 않음(빈 칸이면 [text]만 남음). 기존 값까지 바꾸려면 [replaceTagText] 사용
     */
    protected fun inputTagText(tag: String, text: String): SemanticsNodeInteraction {
        val node = composeRule.onNodeWithTag(tag)
        node.performTextInput(text)
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
     * [text]를 가진 노드가 컴포지션에 있는지 검증
     *
     * 스크롤 밖이라 화면에 보이지 않아도 통과
     */
    protected fun assertTextExists(text: String): SemanticsNodeInteraction =
        composeRule.onNodeWithText(text).assertExists()

    /**
     * [text]를 가진 노드가 컴포지션에 없는지 검증
     *
     * 스크롤 밖 노드까지 확인해 화면 어디에도 표시되지 않아야 하는 값(키 원문 등) 검증에 사용. [substring]이 true면 부분 일치
     */
    protected fun assertTextNotExists(text: String, substring: Boolean = false) {
        composeRule.onNodeWithText(text, substring = substring).assertDoesNotExist()
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

    /**
     * [text]를 가진 노드 클릭
     *
     * [scrollTo]가 true면 먼저 화면에 보이도록 스크롤
     */
    protected fun clickText(text: String, scrollTo: Boolean = false): SemanticsNodeInteraction {
        val node = composeRule.onNodeWithText(text)
        if (scrollTo) {
            node.performScrollTo()
        }
        return node.performClick()
    }

    /**
     * testTag가 [tag]인 노드를 그린 이미지
     *
     * 투명도·색처럼 의미 트리에 드러나지 않는 표시를 픽셀로 비교할 때 사용. [scrollTo]가 true면 먼저 화면에 보이도록 스크롤
     */
    protected fun captureTag(tag: String, scrollTo: Boolean = false): ImageBitmap {
        val node = composeRule.onNodeWithTag(tag)
        if (scrollTo) {
            node.performScrollTo()
        }
        return node.captureToImage()
    }

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

    /**
     * [condition]이 참이 될 때까지 [intervalMillis] 간격으로 최대 [timeoutMillis] 확인 후 충족 여부 반환
     *
     * 창 배치·dumpsys 출력·싱글톤 상태처럼 Compose 노드 대기로 확인할 수 없는 조건에 사용.
     * 제한 시간이 지나면 한 번 더 확인해 마지막 간격 대기 중 충족된 경우도 참으로 판정.
     * 실제 시간 대기라 가상 시간으로 delay를 건너뛰는 runTest가 아닌 runBlocking 안에서 호출
     */
    protected suspend fun waitUntil(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        intervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
        condition: suspend () -> Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            delay(intervalMillis)
        }
        return condition()
    }

    /**
     * [flow]가 [predicate]를 만족하는 값을 낼 때까지 최대 [timeoutMillis] 대기 후 충족 여부 반환
     *
     * DataStore 저장·서비스 실행 상태처럼 Flow로 노출되는 비동기 반영 확인에 사용
     */
    protected suspend fun <T> waitUntilFlowValue(
        flow: Flow<T>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        predicate: (T) -> Boolean,
    ): Boolean {
        // 만족하는 값이 null일 수 있어 값 대신 도달 여부로 판정
        return withTimeoutOrNull(timeoutMillis) {
            flow.first(predicate)
            true
        } ?: false
    }

    /**
     * 화면을 켜고 비보안 키가드를 해제한 뒤 최대 [timeoutMillis] 동안 잠금 해제를 기다리고, 그래도 잠겨 있으면 테스트 건너뜀
     *
     * 잠금 중에는 시스템이 오버레이 창을 숨겨 잠금 해제 상태가 전제인 오버레이 검증에 사용.
     * 키가드 해제는 비동기라 바로 판정하지 않고 기다림. 보안 잠금(PIN 등)은 테스트에서 해제할 수 없고
     * 해제 요청 시 PIN 입력 화면만 뜨므로 요청 없이 건너뜀
     */
    protected suspend fun assumeUnlocked(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        device.wakeUp()
        if (!keyguardManager.isDeviceSecure) {
            device.executeShellCommand("wm dismiss-keyguard")
        }
        val unlocked = waitUntil(timeoutMillis) { !keyguardManager.isKeyguardLocked }
        assumeTrue("기기가 잠겨 있어 건너뜀(잠금 해제 상태에서 실행 필요)", unlocked)
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
        if (settingKey !in changedSettings) {
            val originalValue = getSetting(namespace, key)
            restoreActions += {
                if (originalValue == null) {
                    device.executeShellCommand("settings delete $namespace $key")
                } else {
                    device.executeShellCommand("settings put $namespace $key $originalValue")
                }
            }
            changedSettings += settingKey
        }
        device.executeShellCommand("settings put $namespace $key $value")
    }

    /**
     * 이 앱의 앱 작업 [operation](SYSTEM_ALERT_WINDOW 등)을 [mode](allow 등)로 변경
     *
     * shell 권한으로 권한 화면 없이 직접 변경. 처음 바꿀 때 테스트 전 모드를 보관해 [teardown] 뒤 자동으로 되돌림
     */
    protected fun setAppOpMode(operation: String, mode: String) {
        if (operation !in changedAppOperations) {
            val originalMode = getAppOpMode(operation)
            restoreActions += { device.executeShellCommand("appops set ${context.packageName} $operation $originalMode") }
            changedAppOperations += operation
        }
        device.executeShellCommand("appops set ${context.packageName} $operation $mode")
    }

    /**
     * [read]로 읽은 테스트 전 값을 돌려주고, [teardown] 뒤 [putSetting]과 같은 복원 단계에서 그 값으로 [restore] 실행
     *
     * DataStore 저장값·싱글톤 상태처럼 shell 명령으로 바꿀 수 없는 실사용 기기 값을 테스트가 바꾸기 전에 호출.
     * [read]가 실패하면 등록하지 않아 테스트 전 값을 모르는 채로 덮어쓰지 않음.
     * [read]는 runBlocking으로 기다리므로 메인 스레드가 아닌 테스트 스레드(setup·테스트 본문)에서 호출
     */
    protected fun <T> preserveValue(read: suspend () -> T, restore: suspend (T) -> Unit): T {
        val originalValue = runBlocking { read() }
        restoreActions += { restore(originalValue) }
        return originalValue
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

    /**
     * [putSetting]·[setAppOpMode]·[preserveValue]로 바꾼 값을 등록 역순으로 테스트 전 값으로 복원
     *
     * 역순이라 권한(앱 작업)을 먼저 허용하고 그 권한으로 바꾼 앱 상태는 권한을 되돌리기 전에 복원.
     * 하나가 실패해도 나머지를 모두 복원한 뒤 첫 실패를 던짐(이후 실패는 suppressed로 첨부)
     */
    private fun restoreOriginalValues() {
        var firstFailure: Throwable? = null
        for (restore in restoreActions.asReversed()) {
            try {
                runBlocking { restore() }
            } catch (error: Throwable) {
                val failure = firstFailure
                if (failure == null) {
                    firstFailure = error
                } else {
                    failure.addSuppressed(error)
                }
            }
        }
        restoreActions.clear()
        changedSettings.clear()
        changedAppOperations.clear()
        firstFailure?.let { throw it }
    }

    /** 하위 클래스 확장용 셋업 훅. hiltRule.inject() 이후 호출된다. */
    protected open fun setup() {}

    /** 하위 클래스 확장용 정리 훅. scenario close 이전에 호출된다. */
    protected open fun teardown() {}

    companion object {
        /** [waitUntilTagExists]·[waitUntilTagMatches]·[waitUntilTextExists]·[waitUntil]·[waitUntilFlowValue] 기본 대기 시간 */
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L

        /** [waitUntil] 기본 확인 간격 */
        const val DEFAULT_POLL_INTERVAL_MILLIS = 200L

        /** 앱 작업에 따로 정한 모드가 없을 때의 모드 이름(appops set으로 되돌릴 때도 사용) */
        private const val APP_OP_MODE_DEFAULT = "default"
    }
}
