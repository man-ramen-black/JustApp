# 규칙

- 항상 한국어로 답변합니다. 기술 용어와 코드 식별자는 원문을 유지합니다.
- 항상 존댓말로 답변합니다.
- 규칙은 목적과 의미를 해치지 않는 선에서 최대한 간결하게 작성합니다.
- 변수명은 언더바(`_`)로 시작하지 않습니다.
- 변수명·함수명에 `btn`, `tv`, `img` 같은 줄임말을 사용하지 않고 전체 단어로 작성합니다.
- 코드에서 타입은 FQCN 대신 `import` 후 단순 이름으로 작성합니다.
- 모든 주석은 명사형으로 끝맺고 `~한다`·`~된다` 같은 서술형 종결을 쓰지 않습니다. (예: `select_app_button 클릭`)
- ViewModel KDoc에 해당 ViewModel을 참조하는 화면을 작성하고, 그 화면의 import도 추가합니다. (예: `UsageTimerViewModel`: `[UsageTimerScreen]`)
- 모든 ViewModel은 `EventViewModel`을 상속합니다.
- ViewModel 코루틴은 `viewModelScope.launch` 대신 `EventViewModel`의 `launch`를 사용합니다.
- 모든 로컬 데이터 저장은 `BaseDataStore`를 상속한 DataStore로 구현합니다.
- 데이터 저장·조회 클래스는 `Model`이 아닌 `Repository`로 명명합니다.
- 작업을 마치면 변경한 파일을 `git add`로 스테이징합니다.
- md 파일을 생성하면 답변 가장 마지막에 해당 파일 경로를 출력합니다.
