---
paths:
  - "**/src/**/*ViewModel.kt"
  - "**/src/**/*Screen.kt"
  - "**/src/**/*Fragment.kt"
---

# ViewModel·화면 이벤트

- ViewModel KDoc에 해당 ViewModel을 참조하는 화면을 링크로 작성하고, 그 화면의 import도 추가합니다. (예: `/** [UsageTimerScreen]의 상태·이벤트 관리 ViewModel */`)
- 모든 ViewModel은 `EventViewModel`을 상속합니다.
- ViewModel 코루틴은 `viewModelScope.launch` 대신 `EventViewModel`의 `launch`를 사용합니다.
- ViewModel 함수는 호출하는 UI 이벤트·생명주기가 아니라 처리하는 동작을 동사로 명명합니다(Android 공식 UI events 가이드). `on`+동사 이름은 Composable 콜백 매개변수에만 쓰고 Screen에서 연결하며, 기존 ViewModel의 `onClickXxx` 형식은 본보기로 삼지 않습니다. (금지: `fun onClickSendTest()`·`fun onResume()`, 허용: `fun sendTestNotification()`·`fun refreshStatus()`)
- ViewModel 일회성 이벤트는 해당 ViewModel 내부에 `ViewModelEvent`를 구현한 sealed class로 정의하고, 하위 이벤트는 sealed class와 같은 레벨(ViewModel 직속)에 `Event{이벤트명}` data class·data object로 명명합니다. (예: `UsageTimerViewModel.EventShowTimerView`)
- 이벤트 전송·구독은 `EventViewModel`의 `sendEvent(event)`·`events`를 사용하고, Compose에서 구독은 `EventViewModel`의 `CollectEvents` 헬퍼를 사용합니다.
