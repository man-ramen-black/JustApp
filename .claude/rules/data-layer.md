---
paths:
  - "**/src/**/model/**/*.kt"
  - "**/src/**/*DataStore.kt"
  - "**/src/**/*Repository.kt"
---

# DataStore·Repository

- 모든 로컬 데이터 저장은 `BaseDataStore`를 상속한 DataStore로 구현하고, delegate는 `preferencesDataStore` 대신 손상 파일 복구 핸들러를 넣는 `blackDataStore(name)`으로 만듭니다(핸들러가 없으면 파일이 손상된 뒤 저장이 계속 실패).
- 데이터 저장·조회 클래스는 `Model`이 아닌 `Repository`로 명명합니다.
