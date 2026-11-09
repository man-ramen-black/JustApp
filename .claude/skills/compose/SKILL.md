---
name: compose
description: 이 저장소의 Jetpack Compose 화면 개발 컨벤션이다. 새 Compose 화면 작성, 기존 `*Screen.kt` 수정, 기존 XML/DataBinding Fragment를 Compose로 전환("이 화면 Compose로 바꿔줘", "XML을 Compose로 전환"), Compose 화면 계측 테스트("Compose UI 테스트", "Screen 테스트") 작성에 명시적으로 요청하지 않아도 사용한다. Fragment 계측 테스트는 `ui-test`, JVM 유닛 테스트는 `unittest` 스킬이 담당한다.
argument-hint: [대상 화면 또는 Fragment]
---

# Compose 화면 스킬

참고 구현: `TextEditorScreen`(ViewModel·이벤트·다이얼로그·ActivityResult), `UsageTimerScreen`(기존 DialogFragment 연동), `MainScreen`(Fragment 안 `ComposeView` 호스팅). 경로는 `app/src/main/java/com/black/app/ui/main/` 아래다.

## 파일 구성

| 파일 | 내용 |
|---|---|
| `XxxScreen.kt` | public `XxxScreen`(stateful) + private `XxxContent`(stateless, 상태 주입 테스트 예외는 "Compose UI 테스트" 절) + 그 화면 전용 private 하위 Composable + testTag 상수 `internal object XxxTestTags` |
| `XxxUiState.kt` | 화면 상태 `data class`. 필드마다 KDoc 한 줄 |
| `XxxViewModel.kt` | `EventViewModel` 상속, `uiState: StateFlow<XxxUiState>` 노출, KDoc에 `.claude/rules/viewmodel.md` 형식으로 `[XxxScreen]` 링크 |

feature 모듈 기능의 화면은 그 모듈의 `ui` 패키지에 두고 문자열도 그 모듈 `strings.xml`에 둔다(`FloatingSettingScreen`). 계측 테스트는 `BaseUiTest`가 있는 app의 androidTest(호스트 `HiltTestActivity`는 `app/src/uitest`)에 같은 패키지로 두므로 `XxxTestTags`는 public으로 연다.

```kotlin
/** TextEditor 화면 */
@Composable
fun TextEditorScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TextEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is TextEditorViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    TextEditorContent(
        uiState = uiState,
        modifier = modifier,
        onClickSave = viewModel::save,
    )
}

/** TextEditor 화면 콘텐츠(상태 무관 렌더링 전용) */
@Composable
private fun TextEditorContent(
    uiState: TextEditorUiState,
    modifier: Modifier = Modifier,
    onClickSave: () -> Unit,
) { /* 순수 UI */ }
```

- **Screen**은 `hiltViewModel()`·상태 수집·이벤트 처리·Context가 필요한 동작을 맡는다. **Content**는 `uiState`와 콜백만 받는다.
- Context가 필요한 동작(토스트·`startActivity`·ActivityResult 실행·`supportFragmentManager`)은 ViewModel이 직접 하지 않는다. ViewModel은 이벤트(`EventXxx`)만 보내고 Screen이 `CollectEvents`로 받아 수행한다. ActivityResult는 Screen의 `rememberLauncherForActivityResult`로 만든다.
- ViewModel 함수는 처리하는 동작(`save`), Content 콜백 매개변수는 `on`+동사(`onClickSave`)로 짓고 Screen에서 연결한다(`.claude/rules/viewmodel.md`). 참고 구현 ViewModel의 `onClickXxx`·`onPause` 함수는 이 규칙 이전 이름이라 따르지 않는다.
- 화면 생명주기 처리는 `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshStatus() }`처럼 Screen에서 한다.
- 메인 목록에서 들어가는 하위 화면은 Material 3 small top app bar처럼 뒤로 가기와 제목을 한 줄에 둔 상단 바를 쓰고, 그 아래에 큰 제목을 따로 두지 않는다. Screen은 `onNavigateUp`을 필수 매개변수로 받고, 화면 틀은 core의 `ToolScreenScaffold`(상단 바 + 스크롤 콘텐츠)를 쓴다. 스크롤하지 않는 화면은 `ToolTopBar`만 쓰고 화면 동작 버튼은 상단 바 `actions`에 `TopBarIconButton`으로 둔다(`TextEditorScreen`).
- 다이얼로그는 표시 여부를 `uiState`(예: `showResetConfirm`)에 두고 Screen에서 Material3 `AlertDialog`로 그린다. 아직 전환되지 않은 `DialogFragment`는 Screen에서 `supportFragmentManager`로 띄운다(`UsageTimerScreen`의 `SelectAppDialogFragment`).

## 네비게이션 등록

1. `ui/navigation/AppRoute.kt`에 `@Serializable data object Xxx : AppRoute` 추가
2. `ui/navigation/AppNavHost.kt`에 `composable<AppRoute.Xxx> { XxxScreen(onNavigateUp = { navController.navigateUp() }, modifier = Modifier.fillMaxSize()) }` 등록
3. 다른 화면에서 이동할 때 Fragment는 주입받은 `AppNavigator`의 `navigate(AppRoute.Xxx)`(연타 방지가 필요하면 `navigateSafely`)를 쓴다.

`AppNavHost`는 `MainActivity`에서 `BlackTheme` + `Surface` 안에 있으므로 등록된 Screen은 테마를 다시 감싸지 않는다. Fragment 안에 `ComposeView`로 호스팅할 때만 `BlackTheme`으로 감싸고 `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`를 설정한다(`MainFragment`).

## 작성 규칙

- 색·모서리·텍스트 스타일은 core 모듈 `ui/theme`(`com.black.core.ui.theme`)의 `AppColors`(`Color.kt`)·`AppShapes`(`Shape.kt`)·`AppTypography`(`Type.kt`)를 쓴다. 필요한 값이 없으면 `Color(0x...)`나 Text의 색·크기·글꼴·자간 인자를 화면마다 반복해 쓰지 말고 해당 객체에 의미 이름으로 추가한 뒤 참조한다. 한 번만 쓰는 변형은 가까운 스타일을 `copy`한다. 새 텍스트 스타일은 `Type.kt`의 `BaseTextStyle`에서 만든다(Text에 `style`을 넘기면 `LocalTextStyle`의 줄 높이가 빠진다). Material 컴포넌트 기본색은 `BlackTheme`이 정한다.
- 여러 화면이 함께 쓰는 Composable(`ActionButton` 등)은 app·feature 모듈이 함께 쓰도록 core 모듈 `ui/component`(`com.black.core.ui.component`)에 public으로 둔다. app 모듈에 `internal`로 두지 않는다.
- 검증 대상 요소에는 testTag를 달고, 태그 문자열은 Screen 파일의 `internal object XxxTestTags`에 `const val`로 둬 화면과 테스트가 같은 상수를 참조한다(`TextEditorTestTags`). `Modifier.testTag("save_button")`·`onNodeWithTag("save_button")`처럼 문자열을 직접 쓰지 않는다. 값은 snake_case이고, XML에서 전환한 화면이면 기존 `@+id` 이름을 이어 쓴다.

## XML Fragment → Compose 전환

1. **동작 목록 작성**: Fragment·레이아웃 XML·ViewModel을 읽어 클릭, 관찰(LiveData·`StateFlow`·`events` 수집), 생명주기 처리, 다른 화면 이동을 빠짐없이 나열한다. 전환 후 이 목록과 대조한다.
2. **ViewModel 정리**: 화면 상태를 `XxxUiState` 하나로 모아 `StateFlow`로 노출하고, Fragment가 `repeatOnLifecycle` 안에서 수집하던 `events`의 처리는 Screen의 `CollectEvents`로 옮긴다. 이벤트 정의는 `.claude/rules/viewmodel.md`를 따른다.
3. **Screen 작성**: 위 파일 구성대로 만들고, 레이아웃 수치(여백·크기)는 원본 XML 값을 대조해 옮긴다. 추측해서 넣지 않는다.
4. **교체**: `AppNavHost`의 `AndroidFragment<XxxFragment>`를 위 2번 형식의 `XxxScreen` 호출로 바꾼다.
5. **정리**: Fragment와 레이아웃 XML을 지우기 전에 참조를 모두 찾는다(`grep -rn "XxxFragment\|fragment_xxx" app/src feature`). import·바로가기 Activity·`main_tab_navigation.xml`·`<include layout>`에 남은 참조를 함께 정리한다. 문자열·drawable은 다른 곳에서 쓰지 않는 것이 확인될 때만 지운다.
6. **테스트 전환**: 기존 Fragment 계측 테스트는 아래 Compose UI 테스트로 옮기고, ViewModel 유닛 테스트는 `unittest` 스킬로 갱신한다.

## Compose UI 테스트

`app/src/androidTest/.../XxxScreenTest.kt`에 작성한다. 기준 예시: `TextEditorScreenTest`.

```kotlin
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TextEditorScreenTest : BaseUiTest() {

    /** TextEditorScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostTextEditorScreen(onNavigateUp: () -> Unit = {}) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    TextEditorScreen(onNavigateUp = onNavigateUp)
                }
            }
        }
    }

    /**
     * new_button 클릭 시 초기화 확인 다이얼로그가 표시되는지 검증
     *
     * Given:
     * - TextEditorScreen 호스팅
     * When:
     * - new_button 클릭
     * Then:
     * - reset_confirm_dialog 표시
     */
    @Test
    fun test_03_clickNewShowsResetConfirmDialog() {
        /** Given **/
        hostTextEditorScreen()

        /** When **/
        clickTag(TextEditorTestTags.NEW_BUTTON)

        /** Then **/
        assertTagDisplayed(TextEditorTestTags.RESET_CONFIRM_DIALOG)
    }
}
```

- `BaseUiTest`가 `hiltRule`·`composeRule`·`onHostActivity`를 제공한다. `@HiltAndroidTest`·`@FixMethodOrder`는 상속되지 않으므로 클래스마다 단다.
- 검증·조작은 `BaseUiTest` 헬퍼(`assertTagDisplayed`·`assertTagExists`·`assertTagNotExists`·`assertTagText`·`assertTextDisplayed`·`clickTag`·`clickText`·`waitUntilTagExists`·`waitUntilTextExists`)로 쓴다. 헬퍼가 없는 `composeRule` 호출이 반복되면 테스트 클래스에 늘어놓지 말고 `BaseUiTest`에 헬퍼를 추가한다. `MainScreenTest`·`UsageTimerScreenTest`의 문자열 태그와 `composeRule.onNodeWithTag(...)` 직접 호출은 본보기로 삼지 않는다.
- `HiltTestActivity`에 `setContent`로 Screen을 띄우면 `hiltViewModel()` 주입까지 실제 경로로 검증된다. Content는 private이므로 Screen을 통해 검증한다.
    - 예외: 표시가 권한·서비스 실행 같은 기기 상태로 갈려 Screen으로는 한 분기만 실행되면, Content를 `internal`(app androidTest와 다른 모듈이면 `public`)로 열고 테스트가 `uiState`를 주입해 분기마다 고정된 기대값으로 검증한다. 연 이유는 Content KDoc에 적는다(`AppInstallNotificationContent`·`FloatingSettingContent`). 기대값을 구현과 같은 함수(`PermissionHelper` 등)나 기기 상태로 계산하지 않는다.
- 함수명 `test_{2자리순번}_{camelCase 동작}`, KDoc 요약 + `Given:`/`When:`/`Then:`, 본문 `/** Given **/` 구획, 기기 설정·앱 작업 변경 시 복원 헬퍼 사용은 `ui-test` 스킬과 같다.

## 검증

```
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "<ViewModel 테스트 FQCN>"
ANDROID_SERIAL=<시리얼> ./gradlew :app:connectedUitestAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<Screen 테스트 FQCN>
```

계측 테스트는 기기가 연결돼 있을 때 실행해 통과를 확인하고, `app/build/outputs/androidTest-results/connected/uitest/`의 XML에서 실패와 건너뜀(`skipped`) 수를 본다. 기기가 없으면 `./gradlew :app:assembleUitestAndroidTest`로 컴파일까지 확인하고, 실행하지 못한 사실과 미검증 범위를 보고한다.
