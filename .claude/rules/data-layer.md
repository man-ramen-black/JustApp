---
paths:
  - "**/src/**/model/**/*.kt"
  - "**/src/**/*DataStore.kt"
  - "**/src/**/*Repository.kt"
---

# DataStore·Repository

- 모든 로컬 데이터 저장은 `BaseDataStore`를 상속한 DataStore로 구현하고, delegate는 `preferencesDataStore` 대신 손상 파일 복구 핸들러를 넣는 `blackDataStore(name)`으로 만듭니다(핸들러가 없으면 파일이 손상된 뒤 저장이 계속 실패).
    - 예외: 스터디 팝업 내용 목록(`StudyPopupDatabase`)은 Room을 유지하고 DataStore로 옮기지 않습니다. 옮기면 사용자의 기존 내용을 이관하는 일회성 코드가 필요하고, 실패하면 되돌리기 어렵습니다. 새 저장소는 이 예외를 근거로 Room을 쓰지 않습니다.
    - 예외: 기기 사용 기록(`UsageLogDatabase`)은 Room을 씁니다. 기록마다 전송 여부를 두고 시각 범위로 조회·삭제하는 행 단위 기록이라, 키마다 값을 통째로 다시 쓰는 DataStore로는 기록 하나를 더할 때마다 목록 전체를 다시 써야 합니다.
- 데이터 저장·조회 클래스는 `Model`이 아닌 `Repository`로 명명합니다.
