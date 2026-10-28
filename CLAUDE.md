# 규칙

## 소통·작업 방식

- 항상 한국어로 답변합니다. 기술 용어와 코드 식별자는 원문을 유지합니다.
- 항상 존댓말로 답변합니다.
- 규칙은 목적과 의미를 해치지 않는 선에서 최대한 간결하게 작성합니다.
- 마크다운으로 렌더되는 글(응답·md 문서·PR 본문)에서 물결표는 `\~`로 이스케이프합니다. 한 줄에 둘 이상이면 그 사이가 취소선으로 렌더되기 때문이며, 코드 조각·코드 블록 안은 제외합니다.
- `박는다`·`박제`·`고아`(orphan) 같은 비유를 응답·문서·주석·식별자에 쓰지 않고, 무엇이 반영·기록됐는지, 무엇이 없어졌는지를 그대로 씁니다.
- 권장안을 자신 있게 고를 수 있는 결정은 묻지 않고 권장안으로 진행하며 그 사실을 한 줄로 알립니다. 남은 질문은 `AskUserQuestion` 한 번에 묶어서 묻습니다. 묻는 것은 취향·컨벤션 충돌, 우열이 갈리지 않는 트레이드오프, 되돌리기 어려운 작업뿐입니다.
- 설계가 정해지면 작성 여부를 되묻지 않고 바로 문서를 작성합니다. 단, 작성된 설계 문서 본문의 사용자 검토는 생략하지 않습니다.
- "마무리해줘"는 그 흐름의 마지막 단계까지 수행하라는 뜻으로, 남은 단계를 나열한 채 지시를 기다리지 않습니다(push는 별도 지시가 필요합니다).
- 이유·의도를 묻는 질문에는 코드를 고치지 않고 설명만 하며, 설명 전에 그 영역의 규칙부터 확인합니다.
- 작업이 막히면 조용히 우회하지 않고 응답 첫 줄과 `PushNotification`으로 알립니다. 다른 프로세스의 자원 점유(포트·파일 락)로 막혔으면 묻지 않고 그 프로세스를 종료한 뒤 재시도하고, 종료 사실을 알립니다.
- 스킬 절차 문구와 이 문서·사용자 메모리의 규칙이 충돌하면 이 문서·메모리가 우선합니다.
- 코드 리뷰에서 이 문서·스킬이 정한 패턴은 지적하지 않고, 그 외 발견은 확신도·심각도를 붙여 스스로 거르지 않고 모두 보고합니다.
- 새로 만드는 파일 이름은 한글 없이 영문 kebab-case로 쓰고, md 문서의 체크 항목은 표준 체크박스(`- [ ]`·`- [x]`)만 씁니다.
- md 파일을 생성하면 답변 가장 마지막에 해당 파일 경로를 출력합니다.
- 새 PC의 상태 표시줄(Clawd 마스코트·HUD) 설정이나 수정 요청은 `.claude/statusline/README.md`를 따르며, 설치는 `node .claude/statusline/install.mjs`로 합니다.
- Orca(ADE) 안에서 실행되는 경우가 많습니다. Orca 조작은 `orca-cli` 스킬을 쓰되, Claude Code 자체 기능(`ListAgents`·`SendMessage`)으로 되는 일에는 Orca를 쓰지 않습니다. 단축키·복사 등 Orca 환경 문제는 `.claude/references/orca-environment.md`를 먼저 봅니다.

## 코드 작성

- 변수명은 언더바(`_`)로 시작하지 않습니다.
- 변수명·함수명에 `btn`, `tv`, `img` 같은 줄임말을 사용하지 않고 전체 단어로 작성합니다.
- 코드에서 타입은 FQCN 대신 `import` 후 단순 이름으로 작성합니다.
- 모든 주석은 명사형으로 끝맺고 `~한다`·`~된다` 같은 서술형 종결을 쓰지 않으며, 내용이 한 줄인 KDoc은 여러 줄로 펼치지 않고 한 줄로 씁니다. (예: `/** select_app_button 클릭 */`)
- 코드 수정 시 기존 주석을 임의로 제거하지 않습니다. 주석·KDoc에는 코드에서 읽히지 않는 이유·제약·함정만 쓰고, 작업 이력(`기존엔 X였음`, `실기기 확인 완료`)은 남기지 않습니다.
- 탐색에 오래 걸린 코드에는 이후 탐색을 돕는 요약 주석을 추가합니다.
- 함수 시그니처·내부 동작·외부 구조는 추측하지 않고 실제 소스를 확인한 뒤 코드를 작성합니다. `head`·`tail`로 자른 검색 결과만으로 "없다"고 단정하지 않습니다.
- 스레드 전환·비동기·대기는 코루틴으로 작성합니다. `CountDownLatch`·`Thread` 직접 생성·`AsyncTask`를 새로 쓰지 않고, 기존 코드가 옛 방식이어도 본보기로 삼지 않습니다. `runBlocking`에 메인 디스패처를 넘길 때는 교착을 피하도록 `Dispatchers.Main.immediate`를 사용합니다.
- 결함은 기존 경로를 재사용해 최소로 고치고, 같은 결함 패턴이 남은 코드가 있으면 같은 작업에서 함께 고칩니다.
- 버그 보고를 받으면 코드를 고치기 전에 `.claude/references/known-external-issues.md`에 원인이 외부로 확인된 같은 증상이 있는지 먼저 봅니다.
- 진행 표시·로딩처럼 켜 두면 사용자를 막는 상태는 성공·실패·취소 모든 경로에서 꺼지도록 보장합니다.

## ViewModel·데이터

- ViewModel KDoc에 해당 ViewModel을 참조하는 화면을 작성하고, 그 화면의 import도 추가합니다. (예: `UsageTimerViewModel`: `[UsageTimerScreen]`)
- 모든 ViewModel은 `EventViewModel`을 상속합니다.
- ViewModel 코루틴은 `viewModelScope.launch` 대신 `EventViewModel`의 `launch`를 사용합니다.
- ViewModel 일회성 이벤트는 해당 ViewModel 내부에 `ViewModelEvent`를 구현한 sealed class로 정의하고, 하위 이벤트는 sealed class와 같은 레벨(ViewModel 직속)에 `Event{이벤트명}` data class·data object로 명명합니다. (예: `UsageTimerViewModel.EventShowTimerView`)
- 이벤트 전송·구독은 `EventViewModel`의 `sendEvent(event)`·`events`를 사용하고, Compose에서 구독은 `EventViewModel`의 `CollectEvents` 헬퍼를 사용합니다.
- 모든 로컬 데이터 저장은 `BaseDataStore`를 상속한 DataStore로 구현합니다.
- 데이터 저장·조회 클래스는 `Model`이 아닌 `Repository`로 명명합니다.

## git·테스트

- 작업은 release에서 분기한 `{타입}/{주제-kebab-case}` 브랜치에서 진행하고 release로 머지합니다. 타입은 `feature`, `fix`, `refactor`, `design`, `test`를 사용합니다. (예: `feature/usage-timer-compose`)
- `git push`는 명시적으로 지시받았을 때만 합니다. 머지 커밋 메시지는 git 기본 형식(`Merge branch '<브랜치>' into release`)을 그대로 사용합니다.
- 테스트 코드를 작성·수정하면 관련 테스트를 실제로 실행해 통과를 확인해야 완료입니다(컴파일 성공만으로는 미완료). 실패하면 변경 전 형상에서 같은 테스트를 돌려 이번 변경 탓인지 가리고, 변경과 무관한 전체 테스트는 요청받았을 때만 실행합니다. 실행할 수 없으면 사유와 미검증 범위를 알립니다.
- 작업을 마치면 변경한 파일을 `git add`로 스테이징합니다. (Write·Edit한 파일은 `.claude/hooks/auto-git-add.mjs`가 자동 스테이징하며 `docs/`는 제외)
