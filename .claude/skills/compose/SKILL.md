---
name: compose
description: 이 저장소의 Jetpack Compose 화면 개발 컨벤션이다. 새 Compose 화면 작성, 기존 `*Screen.kt` 수정, 기존 XML/DataBinding Fragment를 Compose로 전환("이 화면 Compose로 바꿔줘", "XML을 Compose로 전환"), Compose 화면 계측 테스트("Compose UI 테스트", "Screen 테스트") 작성에 명시적으로 요청하지 않아도 사용한다. Fragment 계측 테스트는 `ui-test`, JVM 유닛 테스트는 `unittest` 스킬이 담당한다.
argument-hint: [대상 화면 또는 Fragment]
---

# Compose 화면 스킬

참고 구현: `TextEditorScreen`(ViewModel·이벤트·다이얼로그·ActivityResult), `UsageTimerScreen`(기존 DialogFragment 연동), `MainScreen`(Fragment 안 `ComposeView` 호스팅). 경로는 `app/src/main/java/com/black/app/ui/maintab/main/` 아래다.

## 파일 구성

| 파일 | 내용 |
|---|---|
| `XxxScreen.kt` | public `XxxScreen`(stateful) + private `XxxContent`(stateless) + 그 화면 전용 private 하위 Composable |
| `XxxUiState.kt` | 화면 상태 `data class`. 필드마다 KDoc 한 줄 |
| `XxxViewModel.kt` | `EventViewModel` 상속, `uiState: StateFlow<XxxUiState>` 노출, KDoc에 `[XxxScreen]` 참조 |

```kotlin
/** TextEditor 화면 */
@Composable
fun TextEditorScreen(
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
        onClickSave = viewModel::onClickSave,
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
- 화면 생명주기 처리는 `LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }`처럼 Screen에서 한다.
- 다이얼로그는 표시 여부를 `uiState`(예: `showResetConfirm`)에 두고 Screen에서 Material3 `AlertDialog`로 그린다. 아직 전환되지 않은 `DialogFragment`는 Screen에서 `supportFragmentManager`로 띄운다(`UsageTimerScreen`의 `SelectAppDialogFragment`).

## 네비게이션 등록

1. `ui/navigation/AppRoute.kt`에 `@Serializable data object Xxx : AppRoute` 추가
2. `ui/navigation/AppNavHost.kt`에 `composable<AppRoute.Xxx> { XxxScreen(Modifier.fillMaxSize()) }` 등록
3. 다른 화면에서 이동할 때 Fragment는 주입받은 `AppNavigator`의 `navigate(AppRoute.Xxx)`(연타 방지가 필요하면 `navigateSafely`)를 쓴다.

`AppNavHost`는 `MainActivity`에서 `BlackTheme` + `Surface` 안에 있으므로 등록된 Screen은 테마를 다시 감싸지 않는다. Fragment 안에 `ComposeView`로 호스팅할 때만 `BlackTheme`으로 감싸고 `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`를 설정한다(`MainFragment`).

## 작성 규칙

- 색·모서리는 `BoldListTokens`(`ui/theme/BoldListTokens.kt`)를 쓴다. 필요한 값이 없으면 `Color(0x...)`를 화면에 직접 쓰지 말고 토큰에 의미 이름으로 추가한 뒤 참조한다. Material 컴포넌트 기본색은 `BlackTheme`이 정한다.
- 검증 대상 요소에는 `Modifier.testTag("snake_case")`를 단다. XML에서 전환한 화면이면 기존 `@+id` 이름을 그대로 이어 쓴다(`save_button` 등). 그래야 테스트 전환 시 의미가 유지된다.
- 줄임말 금지(`btn`·`tv`)와 FQCN 금지, 주석 명사형 종결은 CLAUDE.md를 따른다.

## XML Fragment → Compose 전환

1. **동작 목록 작성**: Fragment·레이아웃 XML·ViewModel을 읽어 클릭, 관찰(LiveData·`collectEvent` 문자열 액션), 생명주기 처리, 다른 화면 이동을 빠짐없이 나열한다. 전환 후 이 목록과 대조한다.
2. **ViewModel 정리**: 화면 상태를 `XxxUiState` 하나로 모아 `StateFlow`로 노출하고, 문자열 액션 이벤트는 CLAUDE.md 규칙대로 타입 이벤트(`EventXxx`)로 바꾼다.
3. **Screen 작성**: 위 파일 구성대로 만들고, 레이아웃 수치(여백·크기)는 원본 XML 값을 대조해 옮긴다. 추측해서 넣지 않는다.
4. **교체**: `AppNavHost`의 `AndroidFragment<XxxFragment>`를 `XxxScreen(Modifier.fillMaxSize())`로 바꾼다.
5. **정리**: Fragment와 레이아웃 XML을 지우기 전에 참조를 모두 찾는다(`grep -rn "XxxFragment\|fragment_xxx" app/src feature`). import·바로가기 Activity·`main_tab_navigation.xml`·`<include layout>`에 남은 참조를 함께 정리한다. 문자열·drawable은 다른 곳에서 쓰지 않는 것이 확인될 때만 지운다.
6. **테스트 전환**: 기존 Fragment 계측 테스트는 아래 Compose UI 테스트로 옮기고, ViewModel 유닛 테스트는 `unittest` 스킬로 갱신한다.

## Compose UI 테스트

`app/src/androidTest/.../XxxScreenTest.kt`에 작성한다. 기준 예시: `TextEditorScreenTest`.

```kotlin
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TextEditorScreenTest : BaseUiTest() {

    /** TextEditorScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostTextEditorScreen() {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    TextEditorScreen()
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
        composeRule.onNodeWithTag("new_button").performClick()

        /** Then **/
        composeRule.onNodeWithTag("reset_confirm_dialog").assertIsDisplayed()
    }
}
```

- `BaseUiTest`가 `hiltRule`·`composeRule`·`onHostActivity`를 제공한다. `@HiltAndroidTest`·`@FixMethodOrder`는 상속되지 않으므로 클래스마다 단다.
- `HiltTestActivity`에 `setContent`로 Screen을 띄우면 `hiltViewModel()` 주입까지 실제 경로로 검증된다. Content는 private이므로 Screen을 통해 검증한다.
- 함수명 `test_{2자리순번}_{camelCase 동작}`, KDoc 요약 + `Given:`/`When:`/`Then:`, 본문 `/** Given **/` 구획은 `ui-test` 스킬과 같다.

## 검증

```
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "<ViewModel 테스트 FQCN>"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<Screen 테스트 FQCN>
```

계측 테스트는 기기가 연결돼 있을 때 실행해 통과를 확인한다. 기기가 없으면 `./gradlew :app:assembleDebugAndroidTest`로 컴파일까지 확인하고, 실행하지 못한 사실과 미검증 범위를 보고한다.
