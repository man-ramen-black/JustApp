---
name: ui-test
description: 이 저장소 컨벤션(BaseUiTest 상속, @HiltAndroidTest, Espresso 헬퍼, test_NN_ + GWT)에 맞춰 Fragment용 계측 UI 테스트 코드를 생성한다. 사용자가 특정 Fragment·화면의 "UI 테스트", "계측 테스트", "Espresso 테스트", "androidTest 만들어줘/생성"을 요청하면 반드시 사용한다. 기기·에뮬레이터에서 Fragment를 띄워 검증하는 계측 테스트가 대상이며, JUnit 단위 테스트(test 소스셋)나 Compose 테스트에는 쓰지 않는다.
---

# UI 테스트 코드 생성 스킬

`app/src/androidTest`에서 Fragment를 계측 환경에 띄워 검증하는 Espresso UI 테스트를 저장소 컨벤션에 맞게 생성한다. 기준 예시: `app/src/androidTest/java/com/black/app/ui/main/etc/BlackFragmentTest.kt`(패턴 B).

## 작업 순서

화면마다 다르므로 추측하지 말고 소스를 읽어 검증 대상을 도출한다.

1. **대상 Fragment 확정** — `Glob`으로 `**/<이름>Fragment.kt`를 찾는다. 모호하면 확인.
2. **정보 수집** — 세 곳을 읽는다.
   - Fragment `.kt`: `findNavController()` 사용 여부(→ NavController 부착), 클릭이 트리거하는 navigate 액션, `title`, `layoutResId`.
   - 레이아웃 XML: `@+id` 목록, 정적 `android:text`(→ `assertText`), 데이터바인딩 `android:visibility="@{...}"`(→ 초기 조건별 `assertEffectiveVisible`/`assertEffectiveGone`).
   - 네비게이션 그래프(`app/src/main/res/navigation/*.xml`): 이 Fragment destination id, 액션 도착 id.
3. **호스팅 전략** — 생명주기 중 `findNavController()`에 도달하면 `TestNavHostController` 부착(패턴 A), 아니면 컨테이너 추가만(패턴 B). 애매하면 A. 둘 다 `references/templates.md`.
4. **시나리오 도출** — 아래 "기본 시나리오".
5. **파일 작성** — 메인 패키지를 미러링한 경로(`androidTest/.../<FragmentName>Test.kt`).
6. **마무리** — `git add`. 빌드·실행은 에뮬레이터가 필요해 범위 밖.

## 클래스 구조

```kotlin
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class XxxFragmentTest : BaseUiTest() { ... }
```

**모든 계측(UI) 테스트는 예외 없이 `BaseUiTest`를 상속한다** — Fragment·Espresso 테스트뿐 아니라 UiAutomator 기반 테스트도 포함한다.

`BaseUiTest`가 Hilt 룰·`onHostActivity`(HiltTestActivity 호스팅)·scenario 자동 close를 제공하므로 `@RunWith`/`@get:Rule`을 직접 쓰지 않는다. 공통 필드 `instrumentation`·`device`·`context`(applicationContext)도 제공하므로 하위 클래스에서 다시 선언하지 않고 그대로 쓴다. 셋업·정리는 `@Before`/`@After` 대신 `setup()`/`teardown()` 훅을 override 한다(`baseSetup`/`baseTeardown`이 호출). `@HiltAndroidTest`·`@FixMethodOrder`는 상속되지 않으니 클래스마다 직접 단다 — `NAME_ASCENDING`이 있어야 `test_01`, `test_02` 순번이 실행 순서가 된다. `@AndroidEntryPoint`가 아닌 Fragment도 동일 구조를 쓴다.

기기 설정(`settings`)이나 이 앱의 앱 작업(appops)을 바꾸는 테스트는 `BaseUiTest`의 `putSetting`·`setAppOpMode`를 쓴다. 처음 바꿀 때 테스트 전 값을 보관해 `teardown` 뒤(하위 `teardown`이 실패해도) 되돌리므로 백업·복원 코드를 따로 두지 않는다. 테스트 기기가 사용자 실사용 기기라 바꾼 값이 남으면 안 되기 때문이다. shell로 줄 수 있는 권한(다른 앱 위에 표시 등)이 필요하면 `assumeTrue`로 건너뛰지 않고 `setAppOpMode`로 허용한다. (금지: `device.executeShellCommand("settings put system screen_off_timeout 10000")`, 허용: `putSetting("system", "screen_off_timeout", "10000")`) 앱 저장값(DataStore 등)을 바꾸는 테스트도 setup·teardown에 백업·복원을 직접 쓰지 않고 `preserveValue(read, restore)`로 원래 값을 등록해 같은 단계에서 되돌린다. 잠긴 화면에서 의미가 없는 검증은 `assumeUnlocked()`로 화면을 켜고 잠금 해제를 확인한다.

## Espresso 헬퍼

검증·인터랙션은 `com.black.app.testutil` 헬퍼를 import해서 쓰고, raw `onView`를 테스트에 직접 쓰지 않는다(실패 시 뷰 이름+사유를 메시지에 담는 책임이 헬퍼에 모여 있다).

| 헬퍼 | 용도 |
|---|---|
| `assertDisplayed(id, rootMatcher = null)` | 화면에 실제로 표시(isDisplayed)되는지(다이얼로그 창 안이면 `rootMatcher = isDialog()`) |
| `assertText(id, text)` | 텍스트 일치 |
| `assertEffectiveVisible(id)` | effectiveVisibility VISIBLE(스크롤 무관) |
| `assertEffectiveGone(id)` | effectiveVisibility GONE |
| `clickView(id, scrollTo = false)` | 클릭(가려질 수 있으면 `scrollTo = true`) |
| `typeText(id, text)` | 입력 후 키보드 닫기 |

비동기 반영은 `BaseUiTest`의 `waitUntil`(조건)·`waitUntilFlowValue`(Flow 값)로 기다리고, `SystemClock.sleep`이나 테스트마다 만든 폴링 루프·제한 시간을 쓰지 않는다. 상단바 표시 여부는 testutil `isStatusBarVisible`, 현재 호스트 Activity는 `currentActivity()`로 얻는다.

최상단 앵커 뷰(보통 타이틀)는 `assertDisplayed`로 실제 렌더링을 증명하고, 스크롤로 가려질 수 있는 뷰는 `assertEffectiveVisible`로 검증한다. 네비게이션 결과는 JUnit `assertEquals`로 `navController.currentDestination?.id`를 확인한다. 필요한 헬퍼가 없으면 raw `onView` 대신 `EspressoExtensions.kt`에 기존 `checkView`/`performOnView` 패턴(실패 시 뷰 이름+한국어 메시지)으로 추가한다.

## 검증할 View에 id가 없을 때

헬퍼는 모두 id 기반이라 검증 대상 View에 `@+id`가 없으면 타게팅할 수 없다. **테스트를 위해 프로덕션 레이아웃에 id를 조용히 추가하지 않는다.** id 없는 View와 그래서 빠지는 검증을 사용자에게 보고하고, (a) 기존 네이밍(snake_case)에 맞춰 id 추가, (b) 이미 id 있는 View로 한정 중 선택하게 한다. id를 추가하면 그 레이아웃 변경을 의식적으로 명시한다. id 유무는 2단계에서 미리 확인한다.

## 테스트 함수 스타일

- 함수명 `test_{2자리순번}_{테스트명}` (예: `test_01_contentNotOverlappingStatusBarAfterExit`)
- KDoc: 한 줄 요약 + `Given:`/`When:`/`Then:`
- 본문: 단계를 `/** Given **/`, `/** When **/`, `/** Then **/`로 구획. 조작 없는 단계는 "별도 조작 없음"으로 명시.
- 주석·KDoc은 CLAUDE.md 규칙대로 명사형 종결(`~한다` 금지).

```kotlin
/**
 * BlackFragment 이탈 후 콘텐츠가 status bar와 겹치지 않는지 검증
 *
 * Given:
 * - BlackFragment 호스팅(onResume에서 immersive 모드 진입, status bar 숨김)
 * When:
 * - BlackFragment 제거(onPause에서 immersive 모드 해제, status bar 복원)
 * Then:
 * - 콘텐츠 컨테이너 상단이 status bar 아래로 복원(겹침 없음)
 */
@Test
fun test_01_contentNotOverlappingStatusBarAfterExit() = runBlocking<Unit> {
    /** Given **/
    val scenario = hostBlackFragment()
    waitUntilStatusBarVisibility(scenario, visible = false, failureMessage = "immersive 진입 후 status bar가 숨겨지지 않았습니다")

    /** When **/
    removeBlackFragment(scenario)
    waitUntilStatusBarVisibility(scenario, visible = true, failureMessage = "BlackFragment 이탈 후 status bar가 다시 표시되지 않았습니다")

    /** Then **/
    // Window 재배치(insets 적용)가 비동기이므로 복원 완료까지 대기 후 겹침 검증
    waitUntilContainerTop(scenario, "BlackFragment 이탈 후 컨테이너 상단이 status bar 아래로 복원되지 않았습니다") { top -> top > 0 }
    assertBelowStatusBar(containerId)
}
```

## 기본 시나리오

레이아웃·Fragment에서 도출되는 만큼만 만든다(억지 금지).

1. **핵심 View 노출** — 타이틀+주요 조작 View. 정적 타이틀은 `assertText`로 텍스트까지.
2. **정적 라벨** — 버튼 등 정적 `android:text`. 없으면 생략.
3. **네비게이션** — 클릭이 `navigate(...)`로 이어지는 뷰마다 클릭 후 destination 전환 확인.
4. **조건부 visibility** — 데이터바인딩 visibility의 초기 상태.

입력·이벤트(Toast/Snackbar)는 검증이 불안정하므로 요청 시에만 다룬다.

---

호스팅 헬퍼 전체 코드(패턴 A/B)·import·헬퍼 확장 예시는 `references/templates.md` 참고.
