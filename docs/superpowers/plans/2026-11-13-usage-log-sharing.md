# 기기 사용 기록 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 폰의 앱 사용, 화면 켜짐·꺼짐, 알림, Wi-Fi 연결·해제를 기록해 암호화한 뒤 디스코드 웹후크로 집 PC에 보내는 `기기 사용 기록` 기능과, PC 쪽 참고 스크립트를 만든다.

**Architecture:** 수집기 셋(시스템 사용 기록 읽기, 알림 접근 서비스, 알림 접근 서비스 안의 Wi-Fi 감시)이 Room DB에 기록을 쌓는다. WorkManager 주기 작업이 1시간마다 사용 기록을 읽고, 고유 1회 전송 작업이 미전송 기록만 JSON Lines → gzip → AES-256-GCM으로 묶어 웹후크에 올린다. 계산 규칙(구간 만들기, 거르기, 집 판정, 암호화·분할, 응답 해석)은 Android 의존 없는 순수 Kotlin으로 두고 유닛 테스트한다.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room 2.8.4, DataStore, WorkManager 2.10.1(새 의존성), kotlinx.serialization, `javax.crypto`, `HttpURLConnection`. PC 쪽은 Python 3 + `cryptography`.

**Spec:** `docs/superpowers/specs/2026-11-13-usage-log-sharing-design.md`

- 시작 HEAD: `f35bd066be32f5986657b04cc163da7e8115a6ec` (release 최신, `테스트: 계산기 계산 함수, 저장소, ViewModel 유닛 테스트와 화면 계측 테스트 추가`)
- 브랜치: `feature/usage-log-sharing`, 워크트리 `.claude/worktrees/usage-log-sharing`
- 이 계획 문서와 설계 문서는 커밋하지 않는다.

## Global Constraints

- 단계마다 멈춘다: 1단계(Task 1\~8) 끝, 2단계(Task 9\~13) 끝, 3단계(Task 14) 끝에서 사용자 검토를 받기 전에는 다음 단계를 시작하지 않는다.
- 새 의존성은 WorkManager(`androidx.work:work-runtime-ktx`·`work-testing` 2.10.1) 하나다. HTTP는 `HttpURLConnection`으로 한다. 2.10.1이 받아지지 않으면 2.10.0을 쓴다.
- minSdk 24, targetSdk 34, compileSdk 36을 바꾸지 않는다.
- 개인 데이터는 회사 PC로 가져오지 않는다. 알림 제목·본문, SSID, 웹후크 주소, 암호화 키를 로그에 남기지 않는다(core `Log`는 stable 앱에서도 출력됨). 로그에는 건수·종류·결과 코드만 남긴다. 기록 DB를 adb로 꺼내거나 최근 기록 화면을 캡처하지 않는다. 계측 테스트는 가짜 기록만 쓴다.
- 기록 형식은 설계 5.4절의 필드 이름(`package`, `appName`, `t` 등)과 `type` 값을 그대로 쓴다.
- 코드 규칙(저장소 `CLAUDE.md`): 변수명 `_` 시작 금지, `btn`·`tv`·`img` 같은 줄임말 금지, FQCN 대신 import, 주석은 명사형 종결, KDoc 표준 형식, 비동기는 코루틴(`CountDownLatch`·`Thread`·`AsyncTask` 금지).
- ViewModel은 `.claude/rules/viewmodel.md`, DataStore·Repository는 `.claude/rules/data-layer.md`를 따른다(`EventViewModel`, `launch`, `Event{이름}`, 동사 함수명, `BaseDataStore` + `blackDataStore(name)`).
- 화면 문구는 한국어 `strings.xml`에 둔다. 한 문자열에 문장이 둘 이상이면 마침표 뒤 `\n`, 조작 방법·자명한 결과를 설명하는 안내 문장 금지, 갈래 서술을 엠대시로 잇지 않는다.
- 기기 작업은 갤럭시 S24(`R3CX1015TQT`)에서만 `ANDROID_SERIAL=R3CX1015TQT`로 한다. S25(`R3CY109Y4JY`)와 Pixel(`58071FDCQ000YQ`)은 연결돼 있어도 건드리지 않는다. S24가 없으면 연결을 요청하고 기기 없이 되는 작업을 계속한다.
- 개발 앱(`installDebug`)은 기기 확인이 필요한 커밋마다 다시 설치하고, 실사용 앱(`installStable`)은 모든 단계가 끝난 뒤에만 설치한다.
- 기존 구현을 먼저 쓴다. JSON은 core `JsonUtil`(`@Serializable` 모델, 한 줄용 설정), 앱 라벨은 `InstalledAppResolver`, 권한 확인·설정 화면 이동은 `PermissionHelper` companion, 기간 문자열은 `DataUtil`, 설정 행·대화상자는 core `SettingRows`·`ConfirmDialog`를 쓴다. 모자라면 새 방식을 옆에 만들지 말고 그 구현을 보완하고 테스트를 붙인다(사용자 지시).
- 커밋은 `commit` 스킬(`.claude/skills/commit/SKILL.md`)대로 대상 파일을 나열해 스테이징하고 pathspec으로 커밋한다. `docs/`는 커밋하지 않는다.

## Review Focus

1. 알림 본문에 줄바꿈·따옴표가 있으면 JSON Lines의 한 줄이 깨질 수 있다. 한 기록은 한 줄을 유지하고 원문이 그대로 복원돼야 한다. (Task 1 `test_05`)
2. SSID가 따옴표로 감싸져 오거나 `<unknown ssid>`로 오면 집 판정이 틀린다. 따옴표는 벗기고, 알 수 없음은 `null`로 둔다. (Task 5 `test_01`)
3. 수집을 끄고 다시 켜면 꺼져 있던 기간의 앱 사용 구간이나 Wi-Fi 변화가 섞일 수 있다. 다시 켜면 열린 구간과 마지막 Wi-Fi 상태를 비우고 `collection_off` 빈 구간만 남겨야 한다. (Task 6 `test_04`)
4. 웹후크 주소를 붙여 넣을 때 앞뒤 공백·줄바꿈이 섞이면 전송이 계속 실패한다. 저장 전에 앞뒤 공백을 지워야 한다. (Task 12 `test_10`)
5. 디스코드가 429로 응답하면 기록이 미전송으로 남아 다음 주기까지 밀린다. `retry_after`만큼 기다렸다가 같은 묶음을 다시 보내야 한다. (Task 11 `test_05`)

## 파일 구조

| 경로(app 모듈 `com.black.app` 기준) | 책임 |
|---|---|
| `usagelog/record/UsageLogRecord.kt` | 기록 종류(sealed)와 열거형 |
| `usagelog/record/UsageLogState.kt` | 저장해 두는 진행 상태(`OpenUsageSession`, `WifiState`) |
| `usagelog/record/UsageLogRecordCodec.kt` | DB 행 변환, 전송용 JSON 한 줄 생성 |
| `model/database/usagelog/` | Room 엔티티·DAO·DB(`UsageLog.db`) |
| `model/UsageLogRepository.kt` | 기록 추가·미전송 조회·전송 표시·보관 정리 |
| `model/datastore/UsageLogDataStore.kt`, `model/UsageLogSettingsRepository.kt` | 설정·커서·상태 저장 |
| `usagelog/collect/` | 사용 기록 읽기, 구간 만들기, 알림 규칙, Wi-Fi 상태 계산, 알림 접근 서비스, Wi-Fi 감시 |
| `usagelog/UsageLogCollectionController.kt` | 수집 켜기·끄기 |
| `usagelog/work/` | 주기 수집 작업, 전송 작업, 작업 예약 |
| `usagelog/upload/` | 암호화, 전송 묶음, 웹후크 형식·클라이언트, 전송 실행 |
| `ui/main/usagelog/` | 화면, ViewModel, 상태, 시스템 상태 조회 |
| `docs/usage-log-pc/` (커밋 안 함) | PC 쪽 안내 문서, 참고 스크립트, 비서 설명문 |

## Task 2 수정 1회차로 바뀐 인터페이스

사용자 지시로 Task 1·2의 JSON 처리를 core `JsonUtil` 기반으로 바꿨다(`.superpowers/sdd/…/task-2-fix-1.md`). 아래 태스크 본문의 옛 이름은 이 표대로 읽는다.

| 옛 이름 | 새 이름 |
|---|---|
| `ScreenRecord(ScreenEventKind.SCREEN_ON, t)` 등 | `ScreenOnRecord(t)`, `ScreenOffRecord(t)`, `UnlockRecord(t)` (`ScreenEventKind` 없음) |
| `WifiRecord(true, t, ssid, home, inferred)` | `WifiConnectedRecord(t, ssid, home, inferred)` (`WifiRecord`는 둘의 sealed 상위 타입) |
| `WifiRecord(false, t, ssid, home, inferred)` | `WifiDisconnectedRecord(t, ssid, home, inferred)` |
| `record.connected` | `record is WifiConnectedRecord` |
| `UsageLogRecordCodec.typeOf`·`encodeFields`·`decode(type, time, fields)` | `encode(record)`, `decode(json): UsageLogRecord?`, `toJsonLine(id, record)` |
| `UsageLogEntity.type`·`fields` | `UsageLogEntity.json` |
| JSON 직접 생성(`buildJsonObject`, `Json.encodeToString`) | `@Serializable` 모델 + `JsonUtil` 한 줄용 설정 |

## 실행 순서와 병렬

- 1단계: Task 1 → Task 2 → (Task 3 ∥ Task 4) → Task 5 → Task 6 → Task 7 → Task 8. Task 4와 Task 5는 `UsageLogListenerService`·매니페스트를 함께 고치므로 순서대로 한다.
- 2단계: (Task 9 ∥ Task 10) → Task 11 → Task 12 → Task 13
- 3단계: Task 14
- 실행 전 준비: `git worktree add -b feature/usage-log-sharing .claude/worktrees/usage-log-sharing release` 후 설계·계획 문서와 `local.properties`를 워크트리에 복사하고 `EnterWorktree(path)`로 옮긴다.

---

## 1단계: 수집·저장·화면

### Task 1: 기록 모델과 JSON 변환

**적용 스킬:** `unit-test` (`.claude/skills/unit-test/SKILL.md`), `commit` (`.claude/skills/commit/SKILL.md`)

**Files:**
- Create: `app/src/main/java/com/black/app/usagelog/record/UsageLogRecord.kt`
- Create: `app/src/main/java/com/black/app/usagelog/record/UsageLogState.kt`
- Create: `app/src/main/java/com/black/app/usagelog/record/UsageLogRecordCodec.kt`
- Test: `app/src/test/java/com/black/app/usagelog/record/UsageLogRecordCodecTest.kt`

**Interfaces:**
- Produces:

```kotlin
sealed interface UsageLogRecord { val time: Long }
data class AppUsageRecord(val packageName: String, val appName: String, val start: Long, val end: Long) : UsageLogRecord // time = start
enum class ScreenEventKind(val type: String) { SCREEN_ON("screen_on"), SCREEN_OFF("screen_off"), UNLOCK("unlock") }
data class ScreenRecord(val kind: ScreenEventKind, override val time: Long) : UsageLogRecord
data class NotificationPostedRecord(val key: String, val packageName: String, val appName: String, override val time: Long, val title: String?, val text: String?, val category: String?) : UsageLogRecord
enum class RemovalReason(val value: String) { CLICK("click"), DISMISS("dismiss"), APP_CANCEL("app_cancel"), OTHER("other") }
data class NotificationRemovedRecord(val key: String, val packageName: String, override val time: Long, val reason: RemovalReason) : UsageLogRecord
data class WifiRecord(val connected: Boolean, override val time: Long, val ssid: String?, val home: Boolean?, val inferred: Boolean) : UsageLogRecord
enum class GapCause(val value: String) { COLLECTION_OFF("collection_off"), USAGE_EVENTS_EXPIRED("usage_events_expired"), UNSENT_DELETED("unsent_deleted") }
data class GapRecord(val start: Long, val end: Long, val cause: GapCause) : UsageLogRecord // time = start

@Serializable data class OpenUsageSession(val packageName: String, val start: Long, val pausedAt: Long?)
data class WifiState(val connected: Boolean, val ssid: String?)

object UsageLogRecordCodec {
    fun typeOf(record: UsageLogRecord): String
    fun encodeFields(record: UsageLogRecord): String          // DB용, type·t·id를 뺀 필드 JSON 객체
    fun decode(type: String, time: Long, fields: String): UsageLogRecord
    fun toJsonLine(id: String, record: UsageLogRecord): String // 설계 5.4절 한 줄, 키 순서 id, type, t, 나머지
}
```

`toJsonLine`은 `buildJsonObject`로 만들어 키 순서를 고정한다. `null` 필드는 생략하지 않고 `null`로 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
test_01_notificationJsonLineMatchesSpec
    assertEquals(
        """{"id":"a1b2-7","type":"notification","t":1762999000000,"key":"0|com.kakao.talk|2|null|10164","package":"com.kakao.talk","appName":"카카오톡","title":"홍길동","text":"내일 몇 시?","category":"msg"}""",
        UsageLogRecordCodec.toJsonLine("a1b2-7", NotificationPostedRecord("0|com.kakao.talk|2|null|10164", "com.kakao.talk", "카카오톡", 1762999000000, "홍길동", "내일 몇 시?", "msg")))
test_02_everyRecordRoundTripsThroughDatabaseFields   // 9가지 기록(화면 3종 포함) 모두 decode(typeOf(r), r.time, encodeFields(r)) == r
test_03_typeNamesMatchSpec   // app_usage, screen_on, screen_off, unlock, notification, notification_removed, wifi_connected, wifi_disconnected, gap
test_04_nullFieldsWrittenAsNull   // title=null → "\"title\":null", WifiRecord(home=null) → "\"home\":null"
test_05_newlineAndQuoteStayOnOneLine   // text="첫 줄\n\"둘째\" 줄" → line에 '\n' 문자 없음, Json.parseToJsonElement(line).jsonObject["text"]!!.jsonPrimitive.content == 원문
test_06_appUsageAndGapLinesUseStartAsTime
    // AppUsageRecord("com.google.android.youtube","YouTube",1000,61000) → {"id":"x-1","type":"app_usage","t":1000,"package":"com.google.android.youtube","appName":"YouTube","start":1000,"end":61000}
    // GapRecord(1000,5000,COLLECTION_OFF) → {"id":"x-2","type":"gap","t":1000,"start":1000,"end":5000,"cause":"collection_off"}
```

`notification_removed`의 필드는 `key`, `package`, `reason`, `wifi_*`의 필드는 `ssid`, `home`, `inferred`다.

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.record.UsageLogRecordCodecTest"` → 컴파일 실패
- [ ] **Step 3: 위 Interfaces대로 구현**
- [ ] **Step 4: 통과 확인**: 같은 명령 → PASS
- [ ] **Step 5: 커밋**: `추가: 기기 사용 기록 모델과 JSON 변환 추가`

### Task 2: 기록 DB와 설정 저장소

**적용 스킬:** `unit-test`, `commit`

**Files:**
- Modify: `core/src/main/java/com/black/core/model/BaseDataStore.kt` (`get`·`update`에 `logValue: Boolean = true` 추가. `false`면 키 이름만 로그)
- Create: `app/src/main/java/com/black/app/model/database/usagelog/UsageLogEntity.kt`, `UsageLogDao.kt`, `UsageLogDatabase.kt`
- Create: `app/src/main/java/com/black/app/model/UsageLogRepository.kt`
- Create: `app/src/main/java/com/black/app/model/datastore/UsageLogDataStore.kt`
- Create: `app/src/main/java/com/black/app/model/UsageLogSettingsRepository.kt`
- Test: `app/src/test/java/com/black/app/model/UsageLogRepositoryTest.kt`, `app/src/test/java/com/black/app/model/UsageLogSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1의 기록 타입, `UsageLogRecordCodec`, `OpenUsageSession`, `WifiState`
- Produces:

```kotlin
@Entity(tableName = "usage_log", indices = [Index("sent"), Index("time")])
data class UsageLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val type: String, val time: Long, val fields: String, val sent: Boolean = false, val createdAt: Long)
// UsageLogDatabase: version 1, exportSchema = false, 파일 "UsageLog.db", StudyPopupDatabase와 같은 getInstance 싱글턴

data class StoredUsageLogRecord(val id: Long, val record: UsageLogRecord)
class UsageLogRepository(private val dao: UsageLogDao) {
    @Inject constructor(@ApplicationContext context: Context) : this(UsageLogDatabase.getInstance(context).dao())
    suspend fun add(records: List<UsageLogRecord>, now: Long = System.currentTimeMillis())
    suspend fun getUnsent(limit: Int): List<StoredUsageLogRecord>          // id 오름차순
    suspend fun markSent(ids: List<Long>)
    fun recentRecords(limit: Int): Flow<List<StoredUsageLogRecord>>      // time 내림차순, 같으면 id 내림차순
    fun unsentCount(): Flow<Int>
    suspend fun purgeExpired(now: Long)  // createdAt < now - RETENTION_MILLIS 삭제, 지운 미전송이 있으면 GapRecord(그 기록들의 최소 time, 최대 time, UNSENT_DELETED) 추가
    companion object { const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000 }
}

class UsageLogSettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    val collectionEnabled: Flow<Boolean>
    suspend fun isCollectionEnabled(): Boolean
    suspend fun setCollectionEnabled(enabled: Boolean): Result<Unit>
    suspend fun getCollectionOffSince(): Long                       // 0이면 없음
    suspend fun setCollectionOffSince(time: Long): Result<Unit>
    val homeSsids: Flow<Set<String>>
    suspend fun getHomeSsids(): Set<String>
    suspend fun addHomeSsid(ssid: String): Result<Unit>
    suspend fun removeHomeSsid(ssid: String): Result<Unit>
    suspend fun getUsageCursor(): Long
    suspend fun setUsageCursor(time: Long): Result<Unit>
    suspend fun getOpenSession(): OpenUsageSession?
    suspend fun setOpenSession(session: OpenUsageSession?): Result<Unit>
    suspend fun getLastWifiState(): WifiState                        // 기본 WifiState(false, null)
    suspend fun setLastWifiState(state: WifiState): Result<Unit>
    suspend fun getInstallId(): String                               // 없으면 UUID를 만들어 저장
}
```

- 집 SSID 목록, 마지막 Wi-Fi SSID, 열린 구간은 `logValue = false`로 읽고 쓴다.
- `UsageLogDataStore`에 테스트 격리용 `suspend fun clear(): Result<Unit>`을 두고, 설정 테스트는 `@Before`에서 부른다. 기록 테스트는 `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`로 만든 DAO를 생성자에 넘긴다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// UsageLogRepositoryTest
test_01_addedRecordsAreUnsentInIdOrder        // add 3건 → getUnsent(10).map { it.record } == 추가 순서
test_02_markSentExcludesFromUnsent            // 첫 건 markSent → getUnsent(10).size == 2
test_03_recentRecordsNewestFirstLimited       // time 100, 300, 200 → recentRecords(2).first() 의 time == [300, 200]
test_04_unsentCountFollowsMarkSent            // 3 → markSent 1건 → 2
test_05_purgeDeletesOldAndAddsGapForUnsent
    // now=40일, createdAt=now-31일인 미전송 time 1000·5000, createdAt=now-31일인 전송됨 1건, createdAt=now인 미전송 1건
    // purgeExpired(now) → 남은 기록: 최근 1건 + GapRecord(1000, 5000, UNSENT_DELETED)(미전송)
test_06_purgeWithoutOldUnsentAddsNoGap        // 오래된 전송됨만 있으면 지우기만 하고 gap 없음
// UsageLogSettingsRepositoryTest
test_01_defaults        // enabled false, offSince 0, homeSsids 빈 집합, cursor 0, openSession null, lastWifi WifiState(false, null)
test_02_homeSsidAddRemove   // add "MyHome_5G", "MyHome_2G" → remove "MyHome_2G" → setOf("MyHome_5G")
test_03_installIdStable     // 두 번 읽은 값이 같고 비어 있지 않음
test_04_openSessionRoundTrip   // OpenUsageSession("com.a", 1000, 2000) 저장·조회, null 저장 후 null
test_05_lastWifiStateRoundTrip // WifiState(true, "MyHome_5G"), WifiState(true, null)
```

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.model.UsageLogRepositoryTest" --tests "com.black.app.model.UsageLogSettingsRepositoryTest"`
- [ ] **Step 3: 구현**: `BaseDataStore` 변경은 기존 호출부를 고치지 않는 기본값 매개변수로만 한다.
- [ ] **Step 4: 통과 확인**: 같은 명령, 그리고 `./gradlew :core:testDebugUnitTest`(BaseDataStore 기존 테스트가 있으면)
- [ ] **Step 5: 커밋**: `추가: 기기 사용 기록 DB와 설정 저장소 추가` (core 변경은 본문에 적음)

### Task 3: 앱 사용·화면 기록 수집

**적용 스킬:** `unit-test`, `commit`

**Files:**
- Create: `app/src/main/java/com/black/app/usagelog/collect/RawUsageEvent.kt`
- Create: `app/src/main/java/com/black/app/usagelog/collect/UsageSessionBuilder.kt`
- Create: `app/src/main/java/com/black/app/usagelog/collect/UsageEventSource.kt` (인터페이스 + `SystemUsageEventSource`)
- Modify: `app/src/main/java/com/black/app/ui/common/selectapp/InstalledAppResolver.kt` (`fun resolveLabel(packageName: String): String` 추가, `resolve`가 같은 라벨 조회를 쓰게 정리)
- Create: `app/src/main/java/com/black/app/usagelog/collect/UsageStatsCollector.kt`
- Test: `app/src/test/java/com/black/app/usagelog/collect/UsageSessionBuilderTest.kt`, `UsageStatsCollectorTest.kt`

**Interfaces:**
- Consumes: Task 1 기록 타입·`OpenUsageSession`, Task 2 `UsageLogRepository`·`UsageLogSettingsRepository`
- Produces:

```kotlin
enum class RawUsageEventKind { RESUMED, PAUSED, SCREEN_ON, SCREEN_OFF, UNLOCK }
data class RawUsageEvent(val kind: RawUsageEventKind, val packageName: String?, val time: Long)
data class UsageSessionResult(val records: List<UsageLogRecord>, val openSession: OpenUsageSession?)
object UsageSessionBuilder {
    fun build(events: List<RawUsageEvent>, openSession: OpenUsageSession?, appNameOf: (String) -> String): UsageSessionResult
}
fun interface UsageEventSource { fun query(begin: Long, end: Long): List<RawUsageEvent> }
class SystemUsageEventSource(context: Context) : UsageEventSource
class UsageStatsCollector(source: UsageEventSource, settings: UsageLogSettingsRepository, repository: UsageLogRepository, appNameOf: (String) -> String, clock: () -> Long = System::currentTimeMillis) {
    @Inject constructor(@ApplicationContext context: Context, settings: UsageLogSettingsRepository, repository: UsageLogRepository, installedAppResolver: InstalledAppResolver)
    suspend fun collect()
    companion object { const val SETTLE_DELAY_MILLIS = 10 * 60 * 1000L; const val EXPIRY_CHECK_AGE_MILLIS = 3L * 24 * 60 * 60 * 1000; const val EXPIRY_GAP_MIN_MILLIS = 60 * 60 * 1000L }
}
```

- 구간 규칙: `RESUMED(p)`는 열린 구간이 없으면 연다. 같은 앱이면 `pausedAt`을 비우고 이어 간다. 다른 앱이면 열린 구간을 `pausedAt ?: 이 시각`에 닫고 새로 연다. `PAUSED(p)`는 열린 구간과 같은 앱일 때만 `pausedAt`을 기록한다. `SCREEN_OFF`는 열린 구간을 `pausedAt ?: 이 시각`에 닫고 `ScreenOffRecord`도 남긴다. 끝이 시작보다 크지 않은 구간은 버린다. 기록은 `time` 순서로 돌려준다.
- `SystemUsageEventSource`: `UsageEvents.Event` 종류 1(`ACTIVITY_RESUMED`)→RESUMED, 2(`ACTIVITY_PAUSED`)→PAUSED, 15→SCREEN_ON, 16→SCREEN_OFF, 18(`KEYGUARD_HIDDEN`)→UNLOCK, 나머지는 버린다. 권한이 없어 예외가 나면 빈 목록을 돌려준다. 앱 이름은 `InstalledAppResolver.resolveLabel`(설치되지 않았으면 패키지 이름)을 쓰고, 수집기 안에서 패키지별로 캐시한다.
- `collect()`: companion `Mutex`로 직렬화한다. 수집이 꺼져 있으면 돌아간다. `end = now - SETTLE_DELAY_MILLIS`, `end <= cursor`면 돌아간다. `end - cursor > EXPIRY_CHECK_AGE_MILLIS`이고 첫 이벤트 시각(없으면 `end`)이 `cursor + EXPIRY_GAP_MIN_MILLIS`보다 늦으면 `GapRecord(cursor, 첫 이벤트 시각, USAGE_EVENTS_EXPIRED)`를 넣고 열린 구간을 버린다. 기록을 먼저 저장하고 그 뒤에 열린 구간과 커서(`end`)를 저장한다.

- [ ] **Step 1: 실패하는 테스트 작성** (`appNameOf = { "${it}앱" }`, 이벤트 표기 `R`=RESUMED, `P`=PAUSED, `OFF`=SCREEN_OFF)

```kotlin
// UsageSessionBuilderTest
test_01_switchToOtherAppClosesAtPause          // [R a 1000, P a 2000, R b 2100] → [AppUsageRecord("a","a앱",1000,2000)], open = OpenUsageSession("b",2100,null)
test_02_activityChangeInsideSameAppKeepsOneSession // [R a 1000, P a 1500, R a 1600, P a 3000, OFF 3100] → [AppUsageRecord(a,1000,3000), ScreenOffRecord(3100)], open = null
test_03_screenOffWithoutPauseClosesAtScreenOff // [R a 1000, OFF 2000] → [AppUsageRecord(a,1000,2000), ScreenOffRecord(2000)]
test_04_openSessionCarriesToNextBuild          // build([R a 1000]) → records 빈 목록, open(a,1000,null); build([P a 5000, R b 5100], 그 open) → [AppUsageRecord(a,1000,5000)], open(b,5100,null)
test_05_resumeOtherAppWithoutPauseClosesAtResume // [R a 1000, R b 4000] → [AppUsageRecord(a,1000,4000)]
test_06_screenEventsKeepOrder                  // [SCREEN_ON 100, UNLOCK 200, R a 300] → [ScreenOnRecord(100), UnlockRecord(200)], open(a,300,null)
test_07_pauseOfOtherPackageIgnored             // [R a 1000, P b 1500, R c 2000] → [AppUsageRecord(a,1000,2000)]
test_08_zeroLengthSessionDropped               // [R a 1000, R b 1000] → records 빈 목록
test_09_pauseAndResumeAtSameTime               // [R a 1000, P a 2000, R b 2000] → [AppUsageRecord(a,1000,2000)]
// UsageStatsCollectorTest (가짜 source가 받은 begin·end 기록, now = 10일)
test_01_disabledCollectsNothing                // 꺼짐 → source 호출 없음, 커서 그대로
test_02_readsUntilTenMinutesAgoAndMovesCursor  // cursor = now-1시간 → query(begin = cursor, end = now-10분), 저장된 커서 == now-10분
test_03_recentTenMinutesDeferred               // cursor = now-5분 → source 호출 없음
test_04_expiredGapWhenCursorIsOld              // cursor = now-5일, 첫 이벤트 now-2일 → GapRecord(cursor, now-2일, USAGE_EVENTS_EXPIRED) 저장, 저장돼 있던 열린 구간은 버림
test_05_noGapWhenEventsStartNearCursor         // cursor = now-5일, 첫 이벤트 cursor+10분 → gap 없음
test_06_openSessionPersisted                   // [R a now-30분] → getOpenSession() == OpenUsageSession(a, now-30분, null)
```

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.collect.UsageSessionBuilderTest" --tests "com.black.app.usagelog.collect.UsageStatsCollectorTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋**: `추가: 시스템 사용 기록에서 앱 사용 구간과 화면 기록 수집 추가`

### Task 4: 알림 기록

**적용 스킬:** `unit-test`, `commit`

**Files:**
- Create: `app/src/main/java/com/black/app/usagelog/collect/NotificationRecordRules.kt`
- Create: `app/src/main/java/com/black/app/usagelog/collect/NotificationDuplicateFilter.kt`
- Create: `app/src/main/java/com/black/app/usagelog/collect/UsageLogListenerService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (알림 접근 서비스 선언), `app/src/main/res/values/strings.xml` (`usage_log_listener_label` = `기기 사용 기록`)
- Test: `app/src/test/java/com/black/app/usagelog/collect/NotificationRecordRulesTest.kt`, `NotificationDuplicateFilterTest.kt`

**Interfaces:**
- Consumes: Task 1 `NotificationPostedRecord`·`NotificationRemovedRecord`·`RemovalReason`, Task 2 저장소, Task 3 `InstalledAppResolver.resolveLabel`
- Produces:

```kotlin
data class NotificationSnapshot(val key: String, val packageName: String, val postTime: Long, val isOngoing: Boolean, val isGroupSummary: Boolean)
object NotificationRecordRules {
    fun shouldRecord(snapshot: NotificationSnapshot, ownPackageName: String): Boolean
    fun removalReason(reasonCode: Int): RemovalReason
    fun resolveText(lastMessageText: CharSequence?, bigText: CharSequence?, text: CharSequence?): String? // 비어 있지 않은 첫 값
}
class NotificationDuplicateFilter(maxEntries: Int = 500) { fun isDuplicate(key: String, title: String?, text: String?): Boolean }
class UsageLogListenerService : NotificationListenerService()
```

```xml
<service
    android:name=".usagelog.collect.UsageLogListenerService"
    android:label="@string/usage_log_listener_label"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

- 제거 이유: `REASON_CLICK`(1)→CLICK, `REASON_CANCEL`(2)·`REASON_CANCEL_ALL`(3)→DISMISS, `REASON_APP_CANCEL`(8)·`REASON_APP_CANCEL_ALL`(9)→APP_CANCEL, 나머지→OTHER. API 26 미만의 인자 하나짜리 콜백은 OTHER로 기록한다.
- 서비스: `onCreate`에서 `SupervisorJob() + Dispatchers.IO` 범위를 만들고 `collectionEnabled`를 구독해 최신 값을 들고 있는다. 꺼져 있으면 아무것도 기록하지 않는다. 게시 알림은 `shouldRecord`와 `isDuplicate`를 거쳐 저장한다. 제목은 `EXTRA_TITLE`, 본문은 `resolveText(MessagingStyle 마지막 메시지, EXTRA_BIG_TEXT, EXTRA_TEXT)`를 쓴다. 마지막 메시지는 `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification`으로 읽는다. 제거 알림은 `shouldRecord`만 거친다. `onDestroy`에서 범위를 취소한다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// NotificationRecordRulesTest
test_01_ongoingSkipped / test_02_groupSummarySkipped / test_03_ownPackageSkipped / test_04_normalRecorded
test_05_removalReasonMapping   // 1→CLICK, 2·3→DISMISS, 8·9→APP_CANCEL, 10→OTHER
test_06_resolveTextPriority    // ("마지막","긴 본문","본문")→"마지막", (null,"긴 본문","본문")→"긴 본문", (null,null,"본문")→"본문", (null,"",null)→null
// NotificationDuplicateFilterTest
test_01_sameContentSameKeyIsDuplicate   // 첫 호출 false, 같은 key·제목·본문 두 번째 true
test_02_changedTextNotDuplicate
test_03_otherKeyNotDuplicate
test_04_evictsOldestBeyondMax           // maxEntries=2, key a·b·c 뒤 a를 같은 내용으로 → false
```

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.collect.NotificationRecordRulesTest" --tests "com.black.app.usagelog.collect.NotificationDuplicateFilterTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**, `./gradlew :app:compileDebugKotlin`
- [ ] **Step 5: 커밋**: `추가: 알림 접근 서비스로 알림 게시와 제거 기록 추가`

### Task 5: Wi-Fi 연결·해제와 집 여부

**적용 스킬:** `unit-test`, `commit`

**Files:**
- Create: `app/src/main/java/com/black/app/usagelog/collect/WifiStateTracker.kt`
- Create: `app/src/main/java/com/black/app/usagelog/collect/WifiMonitor.kt`
- Modify: `app/src/main/java/com/black/app/usagelog/collect/UsageLogListenerService.kt` (`onListenerConnected`에서 시작, `onListenerDisconnected`·`onDestroy`에서 중지)
- Modify: `app/src/main/AndroidManifest.xml` (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`)
- Test: `app/src/test/java/com/black/app/usagelog/collect/WifiStateTrackerTest.kt`

**Interfaces:**
- Consumes: Task 1 `WifiRecord`(`WifiConnectedRecord`·`WifiDisconnectedRecord`)·`WifiState`, Task 2 설정·기록 저장소
- Produces:

```kotlin
object WifiStateTracker {
    fun normalizeSsid(raw: String?): String?       // 앞뒤 큰따옴표 제거, "<unknown ssid>"·빈 문자열 → null
    fun isHome(ssid: String?, homeSsids: Set<String>): Boolean?
    fun onConnected(previous: WifiState, ssid: String?, homeSsids: Set<String>, time: Long, inferred: Boolean = false): List<WifiRecord>
    fun onDisconnected(previous: WifiState, homeSsids: Set<String>, time: Long, inferred: Boolean = false): List<WifiRecord>
    fun reconcile(persisted: WifiState, current: WifiState, homeSsids: Set<String>, time: Long): List<WifiRecord> // inferred = true
}
fun interface HomeTransitionListener { fun onHomeTransition(arrived: Boolean) }
class WifiMonitor(context: Context, settings: UsageLogSettingsRepository, repository: UsageLogRepository, scope: CoroutineScope, listener: HomeTransitionListener) {
    fun start()
    fun stop()
}
```

- `WifiMonitor`: `TRANSPORT_WIFI` 요청으로 `registerNetworkCallback`을 등록한다. API 31 이상은 `NetworkCallback(FLAG_INCLUDE_LOCATION_INFO)`의 `onCapabilitiesChanged`에서 `transportInfo as? WifiInfo`로 SSID를 읽고, 그 미만은 `WifiManager.connectionInfo`로 읽는다. 네트워크마다 첫 `onCapabilitiesChanged`를 연결로, 그 네트워크의 `onLost`를 해제로 본다.
- 현재 Wi-Fi 상태는 수집 여부와 관계없이 메모리에 들고 있는다. 수집이 켜져 있을 때만 `onConnected`·`onDisconnected` 결과를 저장하고 `lastWifiState`를 갱신한다. `collectionEnabled`가 `true`로 바뀌는 순간 `reconcile(저장된 상태, 현재 상태)`를 실행한다.
- `home == true`이고 추정이 아닌 기록이 생기면 `listener.onHomeTransition(record is WifiConnectedRecord)`를 부른다. 1단계의 listener는 아무 일도 하지 않고, Task 11에서 전송 예약에 연결한다.

- [ ] **Step 1: 실패하는 테스트 작성** (`homes = setOf("MyHome_5G")`)

```kotlin
test_01_normalizeSsid        // "\"MyHome_5G\""→"MyHome_5G", "<unknown ssid>"→null, ""→null, null→null, "Cafe"→"Cafe"
test_02_isHome               // "MyHome_5G"→true, "Cafe"→false, null→null
test_03_connectFromDisconnectedRecordsOne   // onConnected(WifiState(false,null),"MyHome_5G",homes,100) == [WifiConnectedRecord(100,"MyHome_5G",true,false)]
test_04_connectSameSsidAgainRecordsNothing  // previous WifiState(true,"MyHome_5G") → 빈 목록
test_05_connectDifferentSsidRecordsDisconnectThenConnect // previous (true,"MyHome_5G"), "Cafe" → [WifiDisconnectedRecord(100,"MyHome_5G",true,false), WifiConnectedRecord(100,"Cafe",false,false)]
test_06_disconnectRecordsPreviousSsidAndHome  // previous (true,"MyHome_5G") → [WifiDisconnectedRecord(100,"MyHome_5G",true,false)]
test_07_disconnectWhenAlreadyDisconnectedRecordsNothing
test_08_reconcileMarksInferred   // persisted (true,"MyHome_5G"), current (false,null) → [WifiDisconnectedRecord(100,"MyHome_5G",true,true)]
test_09_reconcileSameStateRecordsNothing
test_10_unknownSsidRecordsNullHome  // onConnected(previous 해제, null, homes, 100) == [WifiConnectedRecord(100,null,null,false)]
```

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.collect.WifiStateTrackerTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**, `./gradlew :app:compileDebugKotlin`
- [ ] **Step 5: 커밋**: `추가: Wi-Fi 연결과 해제, 집 여부 기록 추가`

### Task 6: 수집 켜기·끄기와 1시간 주기 수집 작업

**적용 스킬:** `unit-test`, `commit`

**Files:**
- Modify: `gradle/libs.versions.toml` (`work = "2.10.1"`, `androidx-work-runtime-ktx`, `androidx-work-testing`), `app/build.gradle.kts`
- Create: `app/src/main/java/com/black/app/usagelog/work/UsageLogScheduler.kt`
- Create: `app/src/main/java/com/black/app/usagelog/work/UsageLogCollectWorker.kt`
- Create: `app/src/main/java/com/black/app/usagelog/UsageLogCollectionController.kt`
- Test: `app/src/test/java/com/black/app/usagelog/UsageLogCollectionControllerTest.kt`, `app/src/test/java/com/black/app/usagelog/work/UsageLogSchedulerTest.kt`

**Interfaces:**
- Consumes: Task 2 저장소, Task 3 `UsageStatsCollector`
- Produces:

```kotlin
class UsageLogScheduler @Inject constructor(@ApplicationContext context: Context) {
    fun schedulePeriodicCollect()   // 고유 주기 작업 PERIODIC_COLLECT_WORK_NAME, 1시간, 제약 없음, ExistingPeriodicWorkPolicy.KEEP
    fun cancelPeriodicCollect()
    companion object { const val PERIODIC_COLLECT_WORK_NAME = "usage_log_collect" }
}
class UsageLogCollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)
    // UsageStatsCollector(applicationContext, …).collect() 후 UsageLogRepository(applicationContext).purgeExpired(now), 예외면 Result.retry()
class UsageLogCollectionController(settings: UsageLogSettingsRepository, repository: UsageLogRepository, scheduler: UsageLogScheduler, clock: () -> Long = System::currentTimeMillis) {
    @Inject constructor(settings: UsageLogSettingsRepository, repository: UsageLogRepository, scheduler: UsageLogScheduler)
    suspend fun enable(): Result<Unit>
    suspend fun disable(): Result<Unit>
    suspend fun ensureScheduled()   // 켜져 있으면 schedulePeriodicCollect
}
```

- `enable()`: `offSince > 0`이면 `GapRecord(offSince, now, COLLECTION_OFF)` 추가 → 커서 `now` → 열린 구간 `null` → `lastWifiState = WifiState(false, null)` → `offSince = 0` → 마지막에 `collectionEnabled = true` → 주기 작업 예약. 켜짐을 마지막에 저장해 Wi-Fi 감시의 `reconcile`이 비운 상태를 기준으로 돈다. 저장 실패가 나면 그 결과를 돌려준다.
- `disable()`: `collectionEnabled = false` → `offSince = now` → 열린 구간 `null` → 주기 작업 취소
- 작업자는 기존 `UsageTimerAccessibility`처럼 저장소를 직접 만든다(hilt-work를 들이지 않음).

- [ ] **Step 1: 실패하는 테스트 작성** (scheduler는 mockk)

```kotlin
// UsageLogCollectionControllerTest
test_01_firstEnableAddsNoGapAndSchedules     // enable() → getUnsent 빈 목록, isCollectionEnabled true, verify { scheduler.schedulePeriodicCollect() }
test_02_reEnableAddsCollectionOffGap         // clock 1000에 disable, 5000에 enable → [GapRecord(1000,5000,COLLECTION_OFF)], cursor 5000
test_03_disableClearsOpenSessionAndCancels   // 열린 구간 저장 → disable → getOpenSession() null, offSince == now, verify { scheduler.cancelPeriodicCollect() }
test_04_enableResetsWifiStateAndCursor       // lastWifi (true,"MyHome_5G")·cursor 10 → enable(now=9000) → lastWifi WifiState(false,null), cursor 9000
// UsageLogSchedulerTest (WorkManagerTestInitHelper.initializeTestWorkManager)
test_01_schedulePeriodicEnqueuesOneUniqueWork   // 두 번 예약해도 getWorkInfosForUniqueWork(PERIODIC_COLLECT_WORK_NAME).get().size == 1, state ENQUEUED
test_02_cancelPeriodicCancels                   // 취소 후 state CANCELLED
```

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.UsageLogCollectionControllerTest" --tests "com.black.app.usagelog.work.UsageLogSchedulerTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋**: 의존성 추가는 `기타: WorkManager 의존성 추가`, 나머지는 `추가: 기기 사용 기록 수집 켜기, 끄기와 1시간 주기 수집 작업 추가`

### Task 7: 기기 사용 기록 화면(전송 섹션 제외)

**적용 스킬:** `compose` (`.claude/skills/compose/SKILL.md`), `unit-test`, `commit`

**Files:**
- Create: `app/src/main/java/com/black/app/ui/main/usagelog/UsageLogUiState.kt`, `UsageLogViewModel.kt`, `UsageLogScreen.kt`, `UsageLogSystemStatus.kt`
- Create: `app/src/main/res/drawable/ic_usage_log.xml` (Material Symbols `history` 모양, `ic_timer.xml`과 같은 형식)
- Modify: `app/src/main/java/com/black/app/ui/navigation/AppRoute.kt` (`UsageLog`), `AppNavHost.kt`, `app/src/main/java/com/black/app/ui/main/MainFragment.kt` (`앱 설치 알림` 다음), `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/com/black/app/ui/main/MainScreenTest.kt`
- Modify: `core/src/main/java/com/black/core/util/PermissionHelper.kt` (companion에 `isUsageAccessGranted`, `showUsageAccessSettings`, `isNotificationListenerEnabled`, `showNotificationListenerSettings` 추가), `core/src/test/java/com/black/core/util/PermissionHelperTest.kt`(추가 함수 케이스)
- Test: `app/src/test/java/com/black/app/ui/main/usagelog/UsageLogViewModelTest.kt`, `app/src/androidTest/java/com/black/app/ui/main/usagelog/UsageLogScreenTest.kt`

**Interfaces:**
- Consumes: Task 2 저장소, Task 3 `UsageStatsCollector`, Task 5 `WifiStateTracker.normalizeSsid`, Task 6 `UsageLogCollectionController`
- Produces:

```kotlin
data class UsageLogUiState(
    val collectionEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val fineLocationGranted: Boolean = false,
    val backgroundLocationGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val homeSsids: List<String> = emptyList(),      // 이름순
    val currentSsid: String? = null,
    val recentRecords: List<StoredUsageLogRecord> = emptyList(),
)
class UsageLogSystemStatus @Inject constructor(@ApplicationContext context: Context) {   // PermissionHelper companion에 위임하는 얇은 주입용 래퍼
    fun isUsageAccessGranted(): Boolean        // PermissionHelper.isUsageAccessGranted(context) (새로 추가)
    fun isNotificationAccessGranted(): Boolean // PermissionHelper.isNotificationListenerEnabled(context, UsageLogListenerService::class.java) (새로 추가)
    fun isFineLocationGranted(): Boolean
    fun isBackgroundLocationGranted(): Boolean // API 29 미만은 true
    fun isBatteryOptimizationIgnored(): Boolean // PermissionHelper
    fun currentSsid(): String?                 // WifiManager.connectionInfo.ssid → normalizeSsid
}
@HiltViewModel class UsageLogViewModel @Inject constructor(settings: UsageLogSettingsRepository, repository: UsageLogRepository, controller: UsageLogCollectionController, collector: UsageStatsCollector, status: UsageLogSystemStatus) : EventViewModel() {
    val uiState: StateFlow<UsageLogUiState>
    fun setCollectionEnabled(enabled: Boolean)
    fun refreshStatus()            // 권한·현재 SSID 재조회 후 collector.collect()
    fun registerCurrentWifi()
    fun removeHomeWifi(ssid: String)
    fun openUsageAccessSettings(); fun openNotificationAccessSettings(); fun requestLocationPermission(); fun openBatteryOptimizationSettings()
    // 이벤트: EventOpenUsageAccessSettings, EventOpenNotificationAccessSettings, EventRequestForegroundLocation, EventRequestBackgroundLocation, EventOpenBatteryOptimizationSettings, EventShowToast(@StringRes val messageResId: Int)
}
internal object UsageLogTestTags { TITLE, COLLECTION_SWITCH, USAGE_ACCESS_STATUS, NOTIFICATION_ACCESS_STATUS, LOCATION_STATUS, BATTERY_STATUS, REGISTER_WIFI_BUTTON, EMPTY_HOME_WIFI, EMPTY_RECORDS, RECENT_RECORDS; fun homeWifiDeleteButton(ssid: String): String }
```

- 최근 기록은 `repository.recentRecords(100)`을 구독한다. 위치 권한 요청은 정확한 위치가 없으면 `EventRequestForegroundLocation`(Screen이 `ACCESS_COARSE_LOCATION`·`ACCESS_FINE_LOCATION`을 한 번에 요청), 정확한 위치만 있으면 `EventRequestBackgroundLocation`이다. 위치 상태 행은 둘 다 있을 때 `허용됨`이다.
- Screen: `LifecycleEventEffect(ON_RESUME) { viewModel.refreshStatus() }`, 진입 때 `controller.ensureScheduled()`. 사용 정보 접근은 `PermissionHelper.showUsageAccessSettings`(`Settings.ACTION_USAGE_ACCESS_SETTINGS`), 알림 접근은 `PermissionHelper.showNotificationListenerSettings`(API 30 이상 `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`에 컴포넌트 지정, 그 미만 `ACTION_NOTIFICATION_LISTENER_SETTINGS`), 배터리는 기존 `PermissionHelper.showIgnoreBatteryOptimizationSettings`로 연다. 앱 사용 시간 같은 기간 문자열은 `DataUtil.milliSecondsToTimeString`을 먼저 검토하고, 모자라면 `DataUtil`을 보완한다.
- Content는 권한 상태에 따라 갈려 `AppInstallNotificationContent`처럼 `internal`로 열고 KDoc에 이유를 적는다.
- 문자열(이 값 그대로): `fragment_name_usage_log` `기기 사용 기록`, 섹션 `수집`·`권한`·`집 Wi-Fi`·`최근 기록`, 스위치 `기록 수집`, 권한 행 `사용 정보 접근`·`알림 접근`·`위치 항상 허용`·`배터리 사용량 제한 없음`, 상태 `허용됨`·`허용 필요`, 등록 버튼 `지금 연결된 %1$s 등록`(SSID 모를 때 `지금 연결된 Wi-Fi 등록`), `삭제`, `등록된 Wi-Fi 없음`, `기록 없음`, 토스트 `usage_log_save_failed` `저장하지 못했습니다.`, `usage_log_wifi_unknown` `연결된 Wi-Fi 이름을 읽을 수 없습니다.\n위치 권한과 위치 설정을 확인해 주세요.` 최근 기록 한 줄 형식은 빌더가 정한다(시각 + 종류별 내용, 앱 사용은 앱 이름과 사용 시간).

- [ ] **Step 1: 실패하는 ViewModel 테스트 작성** (의존성은 mockk)

```kotlin
test_01_initialStateReflectsSettingsAndStatus   // collectionEnabled flow true, homeSsids {"B","A"} → uiState.homeSsids == ["A","B"], 권한 값 반영
test_02_setCollectionEnabledCallsController     // true → coVerify { controller.enable() }, false → disable()
test_03_registerCurrentWifiAddsSsid             // status.currentSsid() "MyHome_2G" → coVerify { settings.addHomeSsid("MyHome_2G") }
test_04_registerWithoutSsidShowsToast           // null → EventShowToast(R.string.usage_log_wifi_unknown), addHomeSsid 호출 없음
test_05_removeHomeWifiRemoves
test_06_requestLocationAsksForegroundThenBackground // fine false → EventRequestForegroundLocation, fine true·background false → EventRequestBackgroundLocation
test_07_refreshStatusCollects                   // coVerify { collector.collect() }
test_08_recentRecordsFollowRepository
test_09_enableFailureShowsToast                 // enable() 실패 → EventShowToast(R.string.usage_log_save_failed)
```

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.ui.main.usagelog.UsageLogViewModelTest"`
- [ ] **Step 3: ViewModel·상태·시스템 상태 구현 후 통과 확인**
- [ ] **Step 4: 화면 계측 테스트 작성** (가짜 기록만 사용)

```kotlin
test_01_titleAndSectionsDisplayed            // Screen 호스팅 → "기기 사용 기록", "수집", "권한", "집 Wi-Fi", "최근 기록"
test_02_permissionRowsShowGrantedAndRequired // Content: usageAccess true, notificationAccess false → USAGE_ACCESS_STATUS에 "허용됨", NOTIFICATION_ACCESS_STATUS에 "허용 필요"
test_03_clickRequiredPermissionInvokesCallback
test_04_homeWifiListAndDelete                // homeSsids ["MyHome_5G"] → 표시, homeWifiDeleteButton("MyHome_5G") 클릭 → 콜백 인자 "MyHome_5G"
test_05_registerButtonShowsCurrentSsid       // currentSsid "MyHome_2G" → "지금 연결된 MyHome_2G 등록"
test_06_recentRecordsDisplayed               // ScreenOnRecord(…) → "화면 켜짐"
test_07_emptyStatesDisplayed                 // EMPTY_HOME_WIFI, EMPTY_RECORDS
test_08_navigateUpInvokesCallback            // ToolTopBarTestTags.NAVIGATE_UP_BUTTON 클릭
```

- [ ] **Step 5: Screen·네비게이션·메뉴·아이콘·문자열 구현**, `MainScreenTest`를 `9 TOOLS`로 바꾸고 `defaultItemList`·`test_02_allItemsDisplayed`에 `기기 사용 기록`(`R.drawable.ic_usage_log`, `앱 설치 알림` 다음)을 추가
- [ ] **Step 6: 검증**: `./gradlew :app:compileDebugKotlin`, ViewModel 테스트, `ANDROID_SERIAL=R3CX1015TQT ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.black.app.ui.main.usagelog.UsageLogScreenTest,com.black.app.ui.main.MainScreenTest` → 결과 XML에서 실패·건너뜀 0. S24가 없으면 `./gradlew :app:assembleDebugAndroidTest`까지 하고 미실행을 보고한다.
- [ ] **Step 7: 커밋**: 화면은 `추가: 기기 사용 기록 화면과 메인 메뉴 추가`, 테스트는 `테스트: 기기 사용 기록 화면 ViewModel 유닛 테스트와 계측 테스트 추가`

### Task 8: 1단계 기기 확인과 사용자 검토 (멈춤 지점)

- [ ] **Step 1:** `ANDROID_SERIAL=R3CX1015TQT ./gradlew :app:installDebug`
- [ ] **Step 2:** 개인 데이터를 보지 않는 확인만 한다. `adb -s R3CX1015TQT shell dumpsys notification | grep UsageLogListenerService`(연결 여부), `dumpsys jobscheduler | grep -c usage_log`(주기 작업 등록), 앱 로그의 종류별 기록 건수
- [ ] **Step 3:** 사용자에게 개발 앱 `(Dev)JustApp`에서 권한 네 가지를 주고, 집 Wi-Fi를 등록하고, 수집을 켠 뒤, 최근 기록에 앱 사용·화면·알림·Wi-Fi가 쌓이는지 폰 화면으로 확인해 달라고 요청한다. 하루치 건수로 설계 13절의 토큰 추정을 다시 확인한다.
- [ ] **Step 4:** 사용자가 판단할 것(코드 구조, 화면, 선택지)만 정리해 보고하고 2단계로 넘어가지 않는다.

---

## 2단계: 전송

### Task 9: 암호화와 전송 묶음

**적용 스킬:** `unit-test`, `commit`

**Files:**
- Create: `app/src/main/java/com/black/app/usagelog/upload/SendReason.kt`, `UsageLogCipher.kt`, `UsageLogBatchEncoder.kt`
- Test: `app/src/test/java/com/black/app/usagelog/upload/UsageLogCipherTest.kt`, `UsageLogBatchEncoderTest.kt`

**Interfaces:**
- Consumes: Task 1 `UsageLogRecordCodec.toJsonLine`, Task 2 `StoredUsageLogRecord`
- Produces:

```kotlin
enum class SendReason(val value: String) { PERIODIC("periodic"), HOME_ARRIVE("home_arrive"), HOME_LEAVE("home_leave"), MANUAL("manual") }
data class BatchContext(val sentAt: Long, val reason: SendReason, val device: String, val appPackage: String, val tzOffsetMinutes: Int, val installId: String)
class EncodedBatch(val sequence: Long, val header: String, val fileName: String, val recordIds: List<Long>, val attachment: ByteArray)
object UsageLogCipher {
    const val KEY_SIZE_BYTES = 32; const val NONCE_SIZE_BYTES = 12
    fun generateKey(): ByteArray
    fun randomNonce(): ByteArray
    fun encrypt(plain: ByteArray, key: ByteArray, nonce: ByteArray = randomNonce()): ByteArray // nonce + 암호문(끝 16바이트 태그)
    fun decrypt(payload: ByteArray, key: ByteArray): ByteArray
    fun encodeKey(key: ByteArray): String      // android.util.Base64 NO_WRAP
    fun decodeKey(text: String): ByteArray?    // Base64가 아니거나 32바이트가 아니면 null
}
object UsageLogBatchEncoder {
    const val MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024
    fun plainText(sequence: Long, context: BatchContext, records: List<StoredUsageLogRecord>): String // meta 줄 + 기록 줄, 줄마다 "\n"
    fun encode(records: List<StoredUsageLogRecord>, firstSequence: Long, context: BatchContext, key: ByteArray, maxAttachmentBytes: Int = MAX_ATTACHMENT_BYTES, nonceProvider: () -> ByteArray = UsageLogCipher::randomNonce): List<EncodedBatch>
}
```

- 기록 `id`는 `"${installId}-${storedId}"`, 머리말은 `{"kind":"justapp-usage-log","v":1,"seq":N}`, 파일 이름은 `usage-log-N.bin`이다.
- meta 줄과 머리말은 `@Serializable` data class를 `JsonUtil` 한 줄용 설정으로 만든다. 기록 줄은 `UsageLogRecordCodec.toJsonLine`을 쓴다.
- 분할: 전체를 압축·암호화해 한도를 넘고 기록이 둘 이상이면 앞뒤 절반으로 나눠 다시 만든다. 순번은 `firstSequence`부터 이어 붙인다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// UsageLogCipherTest
test_01_encryptDecryptRoundTrip
test_02_wrongKeyFails           // assertThrows<GeneralSecurityException>
test_03_tamperedPayloadFails    // 마지막 바이트 뒤집기
test_04_payloadStartsWithNonce  // 고정 nonce → payload.copyOfRange(0,12) == nonce, size == 12 + plain.size + 16
test_05_decodeKeyRejectsWrongLength  // 16바이트 Base64 → null, "not base64" → null
// UsageLogBatchEncoderTest
test_01_plainTextFirstLineIsMeta
    // context(sentAt 1763000000000, HOME_LEAVE, "SM-S921N", "com.black.app", 540, "install-1"), seq 123
    // 첫 줄 == {"type":"meta","v":1,"seq":123,"sentAt":1763000000000,"reason":"home_leave","device":"SM-S921N","app":"com.black.app","tzOffsetMinutes":540}
test_02_recordIdsCombineInstallIdAndRowId   // StoredUsageLogRecord(7, …) 줄에 "\"id\":\"install-1-7\""
test_03_decryptAndGunzipGivesPlainText
test_04_headerAndFileNameMatchSpec          // seq 5 → header {"kind":"justapp-usage-log","v":1,"seq":5}, fileName "usage-log-5.bin"
test_05_splitsWhenOverLimit                 // 기록 50건, maxAttachmentBytes 600 → 묶음 2개 이상, seq가 firstSequence부터 연속, recordIds를 이으면 원래 id 순서, 기록 2건 이상인 묶음은 600바이트 이하
```

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.upload.UsageLogCipherTest" --tests "com.black.app.usagelog.upload.UsageLogBatchEncoderTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋**: `추가: 기기 사용 기록 전송 묶음 생성과 AES-GCM 암호화 추가`

### Task 10: 디스코드 웹후크 클라이언트

**적용 스킬:** `unit-test`, `commit`

**Files:**
- Create: `app/src/main/java/com/black/app/usagelog/upload/DiscordWebhookFormat.kt`, `DiscordWebhookClient.kt`
- Test: `app/src/test/java/com/black/app/usagelog/upload/DiscordWebhookFormatTest.kt`

**Interfaces:**
- Produces:

```kotlin
sealed interface WebhookResult {
    data object Success : WebhookResult
    data class RateLimited(val retryAfterMillis: Long) : WebhookResult
    data class Failure(val statusCode: Int?, val message: String) : WebhookResult
}
fun interface WebhookSender { suspend fun send(webhookUrl: String, content: String, fileName: String, attachment: ByteArray): WebhookResult }
object DiscordWebhookFormat {
    fun isValidWebhookUrl(url: String): Boolean   // https://(discord.com|discordapp.com|ptb.discord.com|canary.discord.com)/api/webhooks/<숫자>/<토큰>, 공백 있으면 false
    fun multipartBody(boundary: String, content: String, fileName: String, attachment: ByteArray): ByteArray
        // part "payload_json"(application/json): {"content":<content>,"attachments":[{"id":0,"filename":<fileName>}]}
        // part "files[0]"(application/octet-stream, filename=<fileName>), 끝은 "--<boundary>--\r\n"
    fun parseResponse(statusCode: Int, body: String?, retryAfterHeader: String?): WebhookResult
        // 2xx → Success, 429 → body의 retry_after(초, 소수) 올림 ms, 없으면 헤더 초, 둘 다 없으면 5000, 그 밖 → Failure(statusCode, body 앞 200자)
}
class DiscordWebhookClient @Inject constructor() : WebhookSender   // HttpURLConnection, Dispatchers.IO, 연결·읽기 30초, 예외 → Failure(null, 예외 클래스 이름)
```

- `payload_json`과 429 응답 본문(`retry_after`)은 `@Serializable` data class와 `JsonUtil`로 만들고 읽는다.
- 로그에 웹후크 주소를 남기지 않는다(토큰 포함).

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
test_01_validWebhookUrls        // "https://discord.com/api/webhooks/123/abc_DEF-1" true, discordapp.com true
test_02_invalidWebhookUrls      // "http://discord.com/api/webhooks/123/abc", "https://example.com/api/webhooks/123/abc", "https://discord.com/api/webhooks/123", " https://discord.com/api/webhooks/123/abc" → false
test_03_multipartContainsPayloadJsonAndFile
    // body 문자열에 name="payload_json", "content":"{\"kind\":\"justapp-usage-log\",\"v\":1,\"seq\":5}", name="files[0]"; filename="usage-log-5.bin", 첨부 바이트, 끝 "--b--\r\n"
test_04_parse2xxSuccess         // 200, 204 → Success
test_05_parse429UsesBodyRetryAfter   // {"retry_after": 1.5} → RateLimited(1500)
test_06_parse429FallsBackToHeader    // body null, header "2" → RateLimited(2000)
test_07_parseOtherFailure            // 404 → Failure(404, …)
```

- [ ] **Step 2: 실패 확인**: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.upload.DiscordWebhookFormatTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋**: `추가: 디스코드 웹후크 multipart 전송 클라이언트 추가`

### Task 11: 전송 실행과 작업 연결

**적용 스킬:** `unit-test`, `commit`

**Files:**
- Modify: `app/src/main/java/com/black/app/model/datastore/UsageLogDataStore.kt`, `app/src/main/java/com/black/app/model/UsageLogSettingsRepository.kt`
- Create: `app/src/main/java/com/black/app/usagelog/upload/UsageLogUploader.kt`
- Create: `app/src/main/java/com/black/app/usagelog/work/UsageLogSendWorker.kt`
- Modify: `UsageLogScheduler.kt`(`enqueueSend`), `UsageLogCollectWorker.kt`(수집 뒤 `enqueueSend(PERIODIC)`), `UsageLogCollectionController.kt`(`enable`에서 키 보장), `UsageLogListenerService.kt`(집 도착·출발 → `enqueueSend`)
- Test: `app/src/test/java/com/black/app/usagelog/upload/UsageLogUploaderTest.kt`, 기존 `UsageLogSchedulerTest`·`UsageLogSettingsRepositoryTest`·`UsageLogCollectionControllerTest`에 이어 쓰기

**Interfaces:**
- Consumes: Task 3 `UsageStatsCollector`, Task 9 인코더·암호화, Task 10 `WebhookSender`·`DiscordWebhookFormat`
- Produces:

```kotlin
data class LastSendResult(val time: Long, val success: Boolean)
// UsageLogSettingsRepository 추가 (웹후크 주소·키는 logValue = false)
val webhookUrl: Flow<String>; suspend fun getWebhookUrl(): String; suspend fun setWebhookUrl(url: String): Result<Unit>
val encryptionKey: Flow<String>; suspend fun getEncryptionKey(): String; suspend fun setEncryptionKey(key: String): Result<Unit>
suspend fun ensureEncryptionKey(): Result<String>    // 없으면 UsageLogCipher.encodeKey(generateKey()) 저장
suspend fun getSendSequence(): Long; suspend fun setSendSequence(sequence: Long): Result<Unit>
val lastSendResult: Flow<LastSendResult?>; suspend fun setLastSendResult(result: LastSendResult): Result<Unit>

sealed interface UploadResult {
    data object NotConfigured : UploadResult
    data object NothingToSend : UploadResult
    data class Sent(val recordCount: Int) : UploadResult
    data class Failed(val retryable: Boolean) : UploadResult
}
data class DeviceInfo(val device: String, val appPackage: String, val tzOffsetMinutes: (Long) -> Int)
class UsageLogUploader(repository: UsageLogRepository, settings: UsageLogSettingsRepository, collector: UsageStatsCollector, sender: WebhookSender, deviceInfo: DeviceInfo, clock: () -> Long = System::currentTimeMillis) {
    @Inject constructor(@ApplicationContext context: Context, repository: UsageLogRepository, settings: UsageLogSettingsRepository, collector: UsageStatsCollector, sender: DiscordWebhookClient)
    suspend fun upload(reason: SendReason): UploadResult
    companion object { const val MAX_RECORDS_PER_ROUND = 5000; const val MAX_RATE_LIMIT_ATTEMPTS = 3 }
}
// UsageLogScheduler 추가
fun enqueueSend(reason: SendReason)  // 고유 1회 작업 SEND_WORK_NAME = "usage_log_send", ExistingWorkPolicy.APPEND_OR_REPLACE, NetworkType.CONNECTED, 입력 KEY_REASON, 지수 재시도 1분, API 31 이상만 setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)
```

- `upload()`: companion `Mutex` 안에서 실행한다. 수집이 켜져 있으면 `collector.collect()` → `purgeExpired(now)` → 주소(`trim`)가 유효하지 않거나 키가 없으면 `NotConfigured` → `getUnsent(MAX_RECORDS_PER_ROUND)`가 빌 때까지 묶음을 만들어 차례로 보낸다. 성공한 묶음마다 `markSent`와 `setSendSequence(seq)`를 저장한다. 429는 `retryAfterMillis`만큼 `delay` 후 같은 묶음을 최대 3번까지 다시 보낸다. 실패하면 `setLastSendResult(LastSendResult(now, false))`를 저장하고 `Failed(retryable = statusCode == null || statusCode >= 500 || 429 반복)`를 돌려준다. 하나라도 보냈으면 `LastSendResult(now, true)`, 보낼 것이 없으면 `NothingToSend`다. 이미 전송됨으로 표시된 기록은 다시 담지 않는다.
- 이 순서라 주기 작업은 직접 보내지 않고 같은 고유 1회 작업을 넣으므로 전송끼리 겹치지 않는다. `Mutex`는 같은 프로세스 안의 이중 실행을 한 번 더 막는다.
- `UsageLogSendWorker`: `NotConfigured`·`NothingToSend`·`Sent` → `Result.success()`, `Failed(true)` → `Result.retry()`, `Failed(false)` → `Result.failure()`

- [ ] **Step 1: 실패하는 테스트 작성** (가짜 `WebhookSender`가 호출을 기록, collector는 mockk)

```kotlin
// UsageLogUploaderTest
test_01_notConfiguredWithoutUrlOrKey      // NotConfigured, sender 호출 없음
test_02_sendsUnsentAndMarksSent           // 기록 3건 → content == {"kind":"justapp-usage-log","v":1,"seq":1}, fileName "usage-log-1.bin", 복호화 줄 수 4, 미전송 0, sequence 1, lastSendResult.success
test_03_alreadySentRecordsAreNotResent    // test_02 뒤 upload 다시 → NothingToSend, sender 호출 1회 그대로
test_04_serverFailureKeepsRecordsUnsent   // Failure(500) → Failed(true), 미전송 3, lastSendResult.success false
test_05_rateLimitedRetriesAfterDelay      // RateLimited(1000) 다음 Success → Sent(3), sender 호출 2회, 가상 시간 1000ms 경과
test_06_clientErrorNotRetryable           // Failure(404) → Failed(false)
test_07_collectsBeforeSendingWhenEnabled  // coVerify { collector.collect() }
test_08_metaReasonMatchesArgument         // HOME_LEAVE → 복호화한 meta 줄의 "reason":"home_leave"
// UsageLogSchedulerTest
test_03_enqueueSendChainsUniqueWork       // MANUAL 두 번 → getWorkInfosForUniqueWork(SEND_WORK_NAME) 2건, 첫 작업 입력 reason "manual"
// UsageLogSettingsRepositoryTest
test_06_ensureEncryptionKeyCreatesOnce    // 두 번 불러도 같은 값, decodeKey 결과 32바이트
// UsageLogCollectionControllerTest
test_05_enableCreatesEncryptionKey
```

- [ ] **Step 2: 실패 확인**: 위 네 테스트 클래스 실행
- [ ] **Step 3: 구현과 연결**
- [ ] **Step 4: 통과 확인**, `./gradlew :app:compileDebugKotlin`
- [ ] **Step 5: 커밋**: `추가: 미전송 기록 웹후크 전송 작업과 집 도착, 출발 즉시 전송 추가`

### Task 12: 화면 전송 섹션

**적용 스킬:** `compose`, `unit-test`, `commit`

**Files:**
- Modify: `UsageLogUiState.kt`, `UsageLogViewModel.kt`, `UsageLogScreen.kt`, `strings.xml`
- Test: `UsageLogViewModelTest.kt`, `UsageLogScreenTest.kt`에 이어 쓰기

**Interfaces:**
- Consumes: Task 11 설정 추가분·`UsageLogScheduler.enqueueSend`, Task 10 `isValidWebhookUrl`, Task 9 `UsageLogCipher`
- Produces:

```kotlin
// UsageLogUiState 추가
val webhookUrl: String = "", val webhookUrlValid: Boolean = false, val encryptionKey: String = "",
val lastSendResult: LastSendResult? = null, val pendingCount: Int = 0, val showRegenerateKeyConfirm: Boolean = false
// UsageLogViewModel 추가 (생성자에 UsageLogScheduler 추가)
fun updateWebhookUrl(text: String)     // 앞뒤 공백 제거 후 저장
fun copyEncryptionKey()                // EventCopyEncryptionKey(key)
fun requestRegenerateKey(); fun confirmRegenerateKey(); fun dismissRegenerateKey()
fun sendNow()                          // 주소가 유효하면 scheduler.enqueueSend(MANUAL), 아니면 EventShowToast(R.string.usage_log_webhook_invalid)
// UsageLogTestTags 추가: SEND_SECTION, WEBHOOK_URL_FIELD, ENCRYPTION_KEY, COPY_KEY_BUTTON, REGENERATE_KEY_BUTTON, REGENERATE_KEY_DIALOG, REGENERATE_KEY_CONFIRM, LAST_SEND_STATUS, SEND_NOW_BUTTON
```

- 키는 앞 4자 + `…` + 뒤 3자로 가려 보여 준다. 복사는 `ClipData`에 민감 표시(API 33 이상 `ClipDescription.EXTRA_IS_SENSITIVE`, 그 미만 `"android.content.extra.IS_SENSITIVE"`)를 붙여 클립보드 미리보기에 키가 보이지 않게 한다.
- 문자열: 섹션 `전송`, `웹후크 주소`, `암호화 키`, `복사`, `새로 만들기`, 대화상자 `새 키를 만들면 PC 쪽 키도 바꿔야 합니다.\n새로 만들까요?`(확인 `새로 만들기`, `ConfirmDialog` 사용), `마지막 전송`, 상태 값 `%1$s %2$s · 대기 %3$d건`(`성공`·`실패`), 보낸 적 없으면 `없음 · 대기 %1$d건`, `지금 보내기`, 토스트 `usage_log_webhook_invalid` `웹후크 주소를 확인해 주세요.`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// UsageLogViewModelTest
test_10_updateWebhookUrlSavesTrimmed    // "  https://discord.com/api/webhooks/1/abc\n" → coVerify { settings.setWebhookUrl("https://discord.com/api/webhooks/1/abc") }, webhookUrlValid true
test_11_sendNowEnqueuesManual           // 유효 → verify { scheduler.enqueueSend(SendReason.MANUAL) }, 무효 → 토스트·호출 없음
test_12_regenerateKeyAfterConfirm       // request → showRegenerateKeyConfirm true, confirm → setEncryptionKey(기존과 다른 32바이트 키), false로 돌아감
test_13_copyEncryptionKeySendsEvent
test_14_pendingCountAndLastSendFollowRepository
// UsageLogScreenTest
test_09_sendSectionShowsMaskedKeyAndPending   // encryptionKey "abcdEFGHijklMNOPqrstUVWXyz0123456789+/AB=" → "abcd…AB=", pendingCount 23 → "대기 23건"
test_10_regenerateConfirmDialog               // showRegenerateKeyConfirm true → 대화상자 표시, 확인 클릭 → 콜백
test_11_sendNowClickInvokesCallback
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인** (ViewModel 유닛 테스트, S24 계측 테스트 `UsageLogScreenTest`)
- [ ] **Step 3: 커밋**: 화면 `추가: 기기 사용 기록 화면에 웹후크 주소, 암호화 키, 전송 상태 섹션 추가`, 테스트 `테스트: 기기 사용 기록 전송 섹션 테스트 추가`

### Task 13: 2단계 기기 확인과 사용자 검토 (멈춤 지점)

- [ ] **Step 1:** `ANDROID_SERIAL=R3CX1015TQT ./gradlew :app:installDebug`
- [ ] **Step 2:** 사용자에게 시험용 디스코드 채널의 웹후크를 만들어 개발 앱에 넣고, `지금 보내기`와 집 Wi-Fi를 껐다 켜기로 첨부 달린 메시지가 오는지 확인해 달라고 요청한다. 나는 메시지를 받아 보지 않고, 화면의 마지막 전송 결과와 로그의 응답 코드만 확인한다.
- [ ] **Step 3:** 사용자가 판단할 것만 정리해 보고하고 3단계로 넘어가지 않는다.

---

## 3단계: PC 쪽 안내 문서와 참고 스크립트

### Task 14: 참고 스크립트, 안내 문서, 비서 설명문

**적용 스킬:** `unit-test`(Kotlin 표본 테스트), `commit`(Kotlin 테스트만)

**Files:**
- Test(커밋): `app/src/test/java/com/black/app/usagelog/upload/UsageLogSampleExportTest.kt`: 고정 키(바이트 0..31)·고정 nonce(바이트 0..11)로 가짜 기록 묶음을 만들어 `app/build/usage-log-sample/`에 `sample.bin`, `key.txt`(Base64), `plain.jsonl`을 쓴다.
- Create(커밋 안 함): `docs/usage-log-pc/usage_log.py`, `docs/usage-log-pc/test_usage_log.py`, `docs/usage-log-pc/README.md`, `docs/usage-log-pc/assistant-prompt.md`

**Interfaces:**
- Consumes: Task 9 `UsageLogBatchEncoder`·`UsageLogCipher`, 설계 5.4절 형식, 설계 13절 조회 명령
- Produces (`usage_log.py`, 표준 라이브러리 + `cryptography`):

```text
python3 usage_log.py ingest <attachment.bin>     # 복호화·gunzip, INSERT OR IGNORE(id), 출력은 "새 기록 N건, 중복 M건"뿐
python3 usage_log.py decrypt <attachment.bin>    # 평문 출력(문제 확인용)
python3 usage_log.py summary <YYYY-MM-DD>
python3 usage_log.py timeline <YYYY-MM-DD> [HH:MM] [HH:MM]
python3 usage_log.py notifications <날짜|날짜~날짜> [--app 이름] [--search 단어] [--new]
python3 usage_log.py home <날짜|날짜~날짜>
python3 usage_log.py apps <날짜~날짜> [--app 이름]
python3 usage_log.py sql "<SELECT 문>"           # 읽기 전용 연결, SELECT만, 최대 200행
# 환경 변수: USAGE_LOG_KEY(Base64 키), USAGE_LOG_DB(기본 ~/.usage_log/usage_log.sqlite3)
```

- 기록 시각은 그 기록이 들어온 묶음 meta의 `tzOffsetMinutes`로 현지 시각(`14:05`)으로 바꾼다. 출력에는 패키지 이름, 알림 `key`, epoch 시각을 넣지 않는다.
- `notifications`는 거르지 않고 조건에 맞는 알림을 모두 출력하고, 제거 기록이 있으면 줄 끝에 `열어 봄`·`지움`을 붙인다. `--new`는 조회 위치 테이블에 마지막으로 돌려준 기록 위치를 저장한다.
- `home`은 집 Wi-Fi 해제 뒤 3분 안에 다시 연결되면 떠난 것으로 보지 않는다(`USAGE_LOG_HOME_DEBOUNCE_MINUTES`로 변경 가능).
- 결과가 300줄을 넘으면 앞 300줄과 `외 N줄`만 출력한다.
- `README.md`(한국어): 설치, 키 설정, 전용 채널과 봇 연동 방법(봇이 `kind`로 메시지를 알아보고 첨부를 `ingest`에 넘긴 뒤 메시지 삭제, Message Content Intent 필요), 쌓기와 조회 원칙, 조회 명령 사용 예.
- `README.md`에 놓치지 않기 절을 둔다. 폰은 디스코드가 받았다고 응답하면 그 기록을 다시 보내지 않으므로, 봇은 켜질 때 채널에 남은 메시지를 거슬러 읽어 모두 `ingest`해야 한다. 처리한 메시지는 지우므로 채널에 남은 메시지가 곧 아직 처리하지 않은 기록이다. 새 메시지만 받는 봇이면 PC나 봇이 꺼져 있던 동안의 기록이 `gap` 표시도 없이 빠진다. discord.py 기준 예시 코드(켜질 때 `channel.history(oldest_first=True)` 처리, 이후 `on_message`)를 싣는다. 저장소 없이 이 폴더만으로 이해되게 쓴다.
- `assistant-prompt.md`: 비서 시스템 프롬프트·도구 설명에 넣을 설명문, 500토큰 안쪽(명령 목록과 예, 원본 파일을 읽지 말 것, 요청이 없으면 조회하지 말 것).

- [ ] **Step 1:** Kotlin 표본 테스트 작성·실행: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.upload.UsageLogSampleExportTest"` → 세 파일 생성
- [ ] **Step 2:** Python 테스트 작성(`unittest`, 임시 DB)

```text
test_decrypt_kotlin_sample          # app/build/usage-log-sample/sample.bin을 key.txt로 풀면 plain.jsonl과 같음
test_ingest_twice_has_no_duplicates # 두 번 ingest → 두 번째 "새 기록 0건"
test_summary_counts                 # 가짜 기록으로 앱별 사용 시간·화면 켠 횟수·잠금 해제 횟수
test_notifications_returns_all_and_marks_removed   # 클릭 제거된 알림 줄 끝 "열어 봄", 거르는 알림 없음
test_notifications_new_returns_only_new            # --new 두 번째 호출은 그 사이 들어온 알림만
test_home_debounce                  # 해제 후 2분 뒤 재연결은 떠남 없음, 5분 뒤 재연결은 떠남 1번
test_sql_rejects_write              # "DELETE FROM records" 거부
test_output_has_no_machine_values   # 출력에 패키지 이름, key, 13자리 epoch 없음
```

- [ ] **Step 3:** `usage_log.py` 구현 후 실행: 스크래치패드에 venv를 만들어 `cryptography`를 설치하고 `<venv>/bin/python -I docs/usage-log-pc/test_usage_log.py` → 모두 통과
- [ ] **Step 4:** `README.md`, `assistant-prompt.md` 작성
- [ ] **Step 5:** Kotlin 표본 테스트만 커밋: `테스트: PC 쪽 복호화 확인용 전송 묶음 표본 생성 테스트 추가`
- [ ] **Step 6:** 사용자에게 산출물을 보고하고, 집 PC로 옮기는 방법을 정한다. 마무리(스쿼시, 실사용 앱 설치)는 사용자 검토 뒤에 한다.

---

## 마무리 (3단계 검토 뒤)

- [ ] `squash` 스킬로 시작 HEAD `f35bd066be32f5986657b04cc163da7e8115a6ec` 이후 커밋을 성격별(기타, 추가, 테스트)로 스쿼시한다. `docs/`는 넣지 않는다.
- [ ] 모든 확인이 끝난 빌드로 `ANDROID_SERIAL=R3CX1015TQT ./gradlew :app:installStable`
