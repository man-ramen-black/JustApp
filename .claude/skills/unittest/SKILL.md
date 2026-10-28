---
name: unittest
description: 이 저장소 컨벤션(BaseTest 상속, MockK, test_NN_ + GWT)에 맞춰 JVM 유닛 테스트(`src/test`)를 작성한다. ViewModel·Repository·DataStore·유틸 클래스의 "유닛 테스트", "단위 테스트", "테스트 코드 작성해줘", "테스트 케이스 추가해줘"를 요청하면 반드시 사용한다. 기기에서 화면을 띄우는 계측 테스트(`src/androidTest`)는 `ui-test`(Fragment)·`compose`(Compose 화면) 스킬이 담당한다.
argument-hint: [대상 클래스명 또는 파일 경로]
---

# 유닛 테스트 작성 스킬

`$ARGUMENTS`의 유닛 테스트를 저장소 컨벤션에 맞게 작성한다. 기준 예시: `app/src/test/java/com/black/app/ui/maintab/main/usagetimer/UsageTimerViewModelTest.kt`(ViewModel), `app/src/test/java/com/black/app/model/UsageTimerRepositoryTest.kt`(Repository).

## 작업 순서

1. **대상 읽기**: 생성자 의존성, public 함수, `uiState` 등 상태 흐름, `sendEvent`로 보내는 이벤트, `init`에서 도는 비동기 로드를 파악한다. 추측하지 않는다.
2. **기존 테스트 확인**: 같은 대상의 테스트 파일이 있으면 그 파일에 이어서 쓰고 순번을 잇는다.
3. **파일 위치**: 메인 소스 패키지를 미러링한 `<모듈>/src/test/java/.../<대상>Test.kt`.
4. **작성**: 아래 규칙.
5. **실행·통과 확인**: 작성으로 끝내지 않는다. 실패하면 원인을 고친 뒤 다시 돌린다.
   ```
   ./gradlew :app:testDebugUnitTest --tests "com.black.app.ui.maintab.main.usagetimer.UsageTimerViewModelTest"
   ```
   core 모듈 대상이면 `:core:testDebugUnitTest`를 쓴다.

## 클래스 구조

```kotlin
/**
 * [UsageTimerViewModel]의 상태 로드·저장·이벤트 발송 동작 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: UsageTimerRepository

    @MockK
    lateinit var installedAppResolver: InstalledAppResolver

    @Before
    fun setupViewModel() {
        coEvery { repository.getPauseDuration() } returns 3
    }

    /**
     * ViewModel 생성 후 init의 비동기 초기 로드 완료까지 대기
     */
    private fun createViewModel() = UsageTimerViewModel(repository, installedAppResolver)
        .also { waitCoroutine() }
}
```

- **`com.black.test.BaseTest`를 상속한다**(`:test` 모듈). Robolectric 러너, `MockKRule`(`@MockK` 필드 초기화), `TestDispatcherRule`(`Dispatchers.Main`을 `StandardTestDispatcher`로 교체), `android.util.Log` 정적 목업, `after()`의 `unmockkAll()`을 제공하므로 하위 클래스에서 다시 선언하지 않는다.
- `@FixMethodOrder(MethodSorters.NAME_ASCENDING)`를 달아 `test_01`·`test_02` 순번이 실행 순서가 되게 한다(상속되지 않으므로 클래스마다 단다).
- 의존성은 `@MockK lateinit var` 또는 `@Before`의 `mockk()`로 만든다. 기본 응답은 `@Before`에서 정하고, 테스트마다 다른 값은 그 테스트의 Given에서 덮는다.
- 생성자로 `CoroutineScope`를 받는 ViewModel(예: `TextEditorViewModel`의 `mainScope`)은 `TestScope(testDispatcherRule.testDispatcher)`를 넘겨 테스트 스케줄러에 묶는다.
- `EventViewModel.launch`는 `viewModelScope`(Main)에서 돌므로, 상태 변화를 확인하기 전에 `waitCoroutine()`으로 대기열을 비운다. `init` 로드가 있는 ViewModel은 생성 헬퍼에서 바로 대기한다.

## 테스트 함수 스타일

- 함수명 `test_{2자리순번}_{camelCase 동작}` (예: `test_02_onAppsSelectedSavesAndUpdates`)
- KDoc: 한 줄 요약 + `Given:`/`When:`/`Then:` 한 줄씩
- 본문: `/** Given **/`, `/** When **/`, `/** Then **/`로 구획한다. 두 단계가 한 줄로 겹치면 `/** Given / When **/`로 합친다.
- suspend 함수를 호출하거나 이벤트를 수집하는 테스트는 `= runTest { }`로 감싼다.
- 주석·KDoc은 CLAUDE.md 규칙대로 명사형으로 끝맺는다.

```kotlin
/**
 * Pause 클릭 시 저장·일시정지 후 DetachTimerView·ShowToast 이벤트가 발송되는지 검증
 *
 * Given: pauseDurationInput "5" 입력, repository 저장·일시정지 모킹
 * When: onClickPause 호출
 * Then: repository.savePauseDuration(5)·pause(5) 호출 + DetachTimerView, ShowToast 이벤트 수신
 */
@Test
fun test_03_onClickPauseSavesAndSendsEvents() = runTest {
    /** Given **/
    coEvery { repository.savePauseDuration(any()) } just Runs
    coEvery { repository.pause(any()) } just Runs
    val viewModel = createViewModel()
    viewModel.onPauseDurationInputChanged("5")
    val receivedEvents = mutableListOf<ViewModelEvent>()

    /** When **/
    viewModel.onClickPause()
    viewModel.events.take(2).toList(receivedEvents)

    /** Then **/
    coVerify { repository.savePauseDuration(5) }
    assertEquals(UsageTimerViewModel.EventDetachTimerView, receivedEvents[0])
    assertEquals(UsageTimerViewModel.EventShowToast("Pause : 5m"), receivedEvents[1])
}
```

## 검증 패턴

- **이벤트**: `sendEvent`는 구독자가 없으면 이벤트를 쌓아 두고 첫 구독자에게 순서대로 넘긴다. 그래서 동작을 호출한 뒤 `viewModel.events.take(n).toList(...)`로 수집해도 된다. 받을 개수(`n`)는 정확히 맞춘다. 많으면 테스트가 끝나지 않고 대기한다.
- **결과값과 호출을 함께 검증한다.** 상태값만으로 분기 결과를 단정하지 말고, 그 결과가 의존성 호출 인자로 전달되면 `coVerify { repository.save(기대값) }`로 인자까지 확인한다.
- **조기 반환 케이스**는 정상 흐름에서 호출됐어야 할 후속 호출이 없었음을 `coVerify(exactly = 0) { ... }`로 명시한다.
- **같은 조건의 호출 검증은 한 블록으로 묶는다.** `verify`와 `verify(exactly = 0)`처럼 조건이 다를 때만 나눈다.
- **값과 Flow를 함께 노출하는 의존성**을 목업할 때는 하나의 `MutableStateFlow`에서 둘을 파생해 값이 어긋나지 않게 한다(아래 이름은 예시).
  ```kotlin
  val name = MutableStateFlow("")
  every { repository.nameFlow } returns name
  coEvery { repository.getName() } answers { name.value }
  ```
- 선택 변경을 검증할 때는 기본 선택과 구분되도록 두 번째 이상 항목을 쓴다.

## 레이어별 포인트

| 대상 | 방식 |
|---|---|
| ViewModel | 의존성 목업 + `createViewModel()` 헬퍼, 상태는 `uiState.value`, 일회성 이벤트는 `events` 수집 |
| Repository·DataStore | 목업하지 않고 `ApplicationProvider.getApplicationContext<Context>()`로 실제 DataStore를 쓴다. 테스트 간 격리를 위해 `@Before`에서 저장소를 초기화한다 |
| 유틸·순수 함수 | 입력·출력 대조. 경계값(빈 목록·0·음수·잘못된 입력)을 케이스로 나눈다 |

## 마무리

- 작성한 테스트를 실행해 통과를 확인한 뒤에만 완료로 보고한다. 실패가 이번 변경 탓인지 애매하면 변경 전 형상에서 **같은 테스트**를 돌려 가른다.
- 변경과 무관한 전체 테스트 실행은 사용자가 요청할 때만 한다.
- 새 파일은 `git add`로 스테이징한다.
