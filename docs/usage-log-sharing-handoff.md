# 기기 사용 기록 기능 인계 문서

2026-11-13 회사 Mac에서 작업을 멈춘 시점의 상태입니다. 다른 환경에서 이 문서만 보고 이어서 작업할 수 있게 정리했습니다.

## 한눈에 보기

- 브랜치: `feature/usage-log-sharing`. 원격에 push했고 HEAD는 `505ff20`입니다.
- 분기점: release `f35bd06` (`테스트: 계산기 계산 함수, 저장소, ViewModel 유닛 테스트와 화면 계측 테스트 추가`)
- 회사 Mac의 워크트리: `.claude/worktrees/usage-log-sharing`
- 단계별 상태

| 단계 | 내용 | 상태 |
|---|---|---|
| 1단계 | 수집, 저장, 화면 | 완료. 사용자가 S24에서 확인함 |
| 2단계 | 암호화, 웹후크 전송, 화면 전송 섹션 | 구현과 검토 완료. 사용자의 디스코드 확인이 남음 |
| 3단계 | PC 쪽 참고 스크립트와 안내 문서 | 구현과 검토 완료. 사용자 검토가 남음 |
| 마무리 | 브랜치 전체 검토, 수정, 스쿼시, 실사용 앱 설치 | 시작 전 |

## 다른 환경에서 이어받는 법

1. 저장소를 받아 `feature/usage-log-sharing`을 체크아웃합니다. 워크트리 생성과 새 세션 인계는 `.claude/references/orca-environment.md`를 따릅니다.
2. 설계 문서, 계획 문서, 진행 장부는 커밋하지 않는 폴더에 있어 회사 Mac에만 있습니다.
    - 설계: `docs/superpowers/specs/2026-11-13-usage-log-sharing-design.md`
    - 계획: `docs/superpowers/plans/2026-11-13-usage-log-sharing.md`
    - 진행 장부: `.superpowers/sdd/2026-11-13-usage-log-sharing/progress.md`
    - 이 문서에는 이어서 작업하는 데 필요한 내용을 옮겨 두었습니다. 설계 원문이 필요하면 회사 Mac에서 가져옵니다.
3. 아래 "남은 작업"을 위에서부터 진행합니다.

## 커밋 구성

| 커밋 | 내용 |
|---|---|
| `4343dda` | 기타: 새 코드 작성 전 기존 구현을 찾아 쓰고 모자라면 보완하는 규칙 추가(`CLAUDE.md`) |
| `ca05080` | 수정: JsonUtil, BaseDataStore, PermissionHelper, InstalledAppResolver, ActionButton 보완과 테스트 |
| `c23e602` | 추가: 기기 사용 기록 기능(수집, 저장, 암호화 웹후크 전송, 화면) |
| `f73602c` | 테스트: 기기 사용 기록 유닛 테스트와 계측 테스트 |
| `5c04f60` | 테스트: PC 쪽 복호화 확인용 전송 묶음 표본 생성 테스트 |
| `cba2868` | 추가: PC 참고 스크립트와 안내 문서(`tools/usage-log/`), `.gitignore`에 `__pycache__/` 추가 |
| `505ff20` | 테스트: PC 참고 스크립트 테스트 |

`f73602c`까지는 36개 커밋을 스쿼시한 결과입니다. 그 뒤 세 커밋은 마무리 스쿼시 때 다시 묶습니다.

## 기능 요약

- 폰에서 모으는 것
    - 앱 사용 구간: 시스템 사용 기록(`UsageStatsManager`)에서 만들고, 1초 미만 구간은 버립니다.
    - 화면 켜짐, 꺼짐, 잠금 해제
    - 알림 게시와 제거: 알림 접근 서비스가 기록하며, 거르지 않고 모두 남깁니다.
    - Wi-Fi 연결과 해제, 집 여부: 등록한 집 SSID로 판정합니다.
- 저장: Room DB(`UsageLog.db`)와 DataStore. 30일이 지나면 정리하고, 보내기 전에 지운 범위는 `gap` 기록으로 남깁니다.
- 전송: 미전송 기록만 JSON Lines, gzip, AES-256-GCM 순으로 묶어 디스코드 웹후크에 multipart로 올립니다.
    - 메시지 본문은 머리말 `{"kind":"justapp-usage-log","v":1,"seq":N}` 한 줄입니다.
    - 첨부 `usage-log-N.bin`은 nonce 12바이트 뒤에 암호문을 붙인 것이고, 암호문 끝 16바이트가 인증 태그입니다.
    - 평문 첫 줄은 `meta`(seq, sentAt, reason, device, app, tzOffsetMinutes)이고, 기록 `id`는 설치 ID와 행 번호를 이은 값입니다.
    - 첨부가 8MB를 넘으면 나눕니다. 2xx 응답을 받은 기록만 전송됨으로 표시합니다. 429 응답은 `retry_after`만큼 기다렸다가 최대 3번 다시 보냅니다.
- 전송 시점
    - 1시간 주기: 수집 작업 뒤에 고유 작업 `usage_log_send`를 `KEEP`으로 넣습니다.
    - 집 도착·출발과 `지금 보내기`: 고유 작업 `usage_log_send_now`를 `REPLACE`로 넣습니다. 첫 재시도는 10초 뒤이고, Android 12 이상에서는 즉시 작업으로 실행합니다.
    - 두 전송이 동시에 나가지 않는 것은 업로더의 Mutex가 보장합니다.
- 화면: 메인 목록의 `기기 사용 기록`. 수집, 권한, 집 Wi-Fi, 전송(웹후크 주소, 가려진 암호화 키와 복사·새로 만들기, 마지막 전송과 대기 건수, 지금 보내기), 최근 기록 섹션이 있습니다.
- PC 쪽(`tools/usage-log/`)
    - `usage_log.py`: `ingest`, `decrypt`, `summary`, `timeline`, `notifications`, `home`, `apps`, `sql` 명령
    - `README.md`: 설치, 키 설정, 메시지 형식, 봇 연동, 놓치지 않기, 조회 원칙
    - `assistant-prompt.md`: 비서 설명문
    - PC 비서는 요청할 때만 조회하고, 쌓기는 LLM 없이 코드로만 합니다.

## 코드 위치

| 경로 | 책임 |
|---|---|
| `app/.../usagelog/record/` | 기록 종류, 진행 상태, JSON 변환(`UsageLogRecordCodec`) |
| `app/.../model/database/usagelog/`, `model/UsageLogRepository.kt` | Room DB와 기록 저장소(전송됨 표시는 900개씩 나눠 한 트랜잭션) |
| `app/.../model/datastore/UsageLogDataStore.kt`, `model/UsageLogSettingsRepository.kt` | 설정, 커서, 웹후크 주소, 암호화 키, 전송 순번, 마지막 전송 결과 |
| `app/.../usagelog/collect/` | 사용 기록 읽기, 구간 만들기, 알림 규칙, Wi-Fi 상태 계산, 알림 접근 서비스, Wi-Fi 감시 |
| `app/.../usagelog/UsageLogCollectionController.kt` | 수집 켜기·끄기, 키 보장 |
| `app/.../usagelog/work/` | 주기 수집 작업, 전송 작업, 작업 예약 |
| `app/.../usagelog/upload/` | 암호화, 전송 묶음, 웹후크 형식과 클라이언트, 전송 실행 |
| `app/.../ui/main/usagelog/` | 화면, ViewModel, 상태, 시스템 상태 조회 |
| `tools/usage-log/` | PC 쪽 참고 스크립트와 문서 |

## 남은 작업

- [ ] 2단계 디스코드 확인(사용자가 직접)
    - [ ] S24에 이 브랜치의 개발 앱을 다시 설치합니다: `ANDROID_SERIAL=R3CX1015TQT ./gradlew :app:installDebug`. 지금 S24에는 다른 세션 브랜치의 개발 앱이 깔려 있습니다.
    - [ ] 개발 앱의 알림 접근 허용이 풀려 있으니, 화면의 권한에서 `알림 접근`을 다시 허용해 달라고 요청합니다.
    - [ ] 시험용 디스코드 채널의 웹후크 주소를 `전송` → `웹후크 주소`에 넣고, `암호화 키`에 가려진 키가 보이는지 확인합니다.
    - [ ] `지금 보내기`를 눌러 첨부 달린 메시지가 오는지, `마지막 전송`이 `성공`인지 사용자가 확인합니다. 메시지 내용은 받아 보지 않습니다.
    - [ ] 집 도착·출발 전송: 연결된 Wi-Fi를 잠시 집 Wi-Fi로 등록하고 껐다 켜서, 각각 1분 안에 메시지가 오는지 확인합니다. 집에서라면 실제 도착·출발로 확인합니다.
    - [ ] 함께 확인할 것: `위치 항상 허용`을 누르면 바로 앱 정보 화면이 열리는지, 마지막 전송 시각 형식(`11-13 14:02`)이 괜찮은지
- [ ] 3단계 사용자 검토: `tools/usage-log/`의 네 파일. 집 PC에서 pull로 받아 봇에 붙이는 일은 사용자가 합니다.
- [ ] 브랜치 전체 검토
    - superpowers `subagent-driven-development` 스킬의 `references/final-review.md`를 따릅니다. 가장 성능 좋은 모델로 base `f35bd06`부터 HEAD까지 검토합니다.
    - 아래 "미룬 항목" 목록을 검토자에게 넘겨 머지 전에 고칠 것을 고르게 합니다.
    - 태스크 검토를 따로 거치지 않은 변경 두 가지(위치 권한 바로 설정 이동, 1초 미만 구간 제외)와 `tools/usage-log/` 이동을 반드시 포함합니다.
- [ ] 검토 결과를 한 번에 고치고 관련 테스트를 다시 실행합니다(유닛 테스트, S24 계측 테스트 `UsageLogScreenTest`).
- [ ] 스쿼시: `squash` 스킬로 `f35bd06` 이후를 성격별로 묶습니다. 지금의 구성을 기준으로 삼되 `tools/usage-log/`는 별도 묶음으로 둡니다. 이 인계 문서는 다른 환경으로 옮기려고 커밋했으므로, release에 들어가지 않게 스쿼시 전에 지웁니다.
- [ ] 실사용 앱 설치: 모든 확인이 끝난 빌드로 `ANDROID_SERIAL=R3CX1015TQT ./gradlew :app:installStable`
- [ ] release 머지는 사용자가 지시할 때 `merge` 스킬로 합니다. push도 명시적으로 지시받았을 때만 합니다.

## 꼭 지킬 것

- 개인 데이터
    - 알림 제목·본문, SSID, 웹후크 주소, 암호화 키를 로그에 남기지 않습니다. core `Log`는 실사용 앱에서도 출력됩니다.
    - 기록 DB를 adb로 꺼내지 않고, 최근 기록 화면을 캡처하지 않으며, 실제 데이터를 복호화하지 않습니다. 계측 테스트는 가짜 기록만 씁니다.
    - 사용자가 허락한 경우(오늘의 0초 구간 조사)에도 원문은 저장하지 않고 집계만 봤습니다.
- 기기
    - 갤럭시 S24(`R3CX1015TQT`)에서만 `ANDROID_SERIAL=R3CX1015TQT`로 작업합니다. S25(`R3CY109Y4JY`)와 Pixel(`58071FDCQ000YQ`)에는 어떤 명령도 보내지 않습니다.
    - 계측 테스트 전에 `dumpsys power`의 `lastUserActivityTime`이 20초 이상 지났고 잠금이 풀려 있는지 봅니다. 실사용 기기라 잠금 해제를 요청하지 않습니다.
    - 같은 기기를 쓰는 다른 JustApp 세션(`ListAgents`의 `justapp-*`)과는 세션끼리 순서를 정하고, 사용자에게 묻지 않습니다.
    - 다른 브랜치 빌드를 덮어 설치하면 이 브랜치에만 있는 알림 접근 서비스의 허용이 풀립니다.
- 코드
    - 기존 구현을 먼저 씁니다(JsonUtil의 한 줄용 설정과 `logInput = false`, PermissionHelper, InstalledAppResolver, DataUtil, SettingRows, ConfirmDialog). 모자라면 그 구현을 보완하고 테스트를 붙입니다.
    - 커밋은 `commit` 스킬대로 경로를 지정하고, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`를 붙입니다. `docs/`와 `.superpowers/`는 커밋하지 않습니다.
- PC 쪽: 집 PC는 이 저장소를 볼 수 있으므로 `tools/usage-log/`는 커밋해서 pull로 전달합니다.

## 계획과 달라진 결정

| 결정 | 이유 |
|---|---|
| 주기 전송과 즉시 전송을 고유 작업 둘로 나눔(`KEEP`과 `REPLACE`) | 계획대로 하나의 작업 사슬에 이어 붙이면, 앞 전송이 재시도를 기다리는 동안 집 도착·출발 전송이 무기한 밀림. 설계의 "네트워크가 있는데 1분 넘게 늦어지면 실패" 조건이 우선함. `REPLACE`가 보내는 도중 끊으면 같은 묶음이 다시 갈 수 있지만, PC가 기록 `id`로 걸러 냄 |
| 전송됨 표시와 손상 행 삭제를 900개씩 나눔 | Android 11 이하 SQLite의 변수 999개 제한 |
| 화면에 들어올 때 수집이 켜져 있으면 키를 만듦 | 키 생성이 추가되기 전부터 수집을 켜 둔 설치(1단계 개발 앱)는 키가 없어 전송이 조용히 실패함 |
| 업로더는 이번 수집이 실패해도 저장된 기록을 보냄. 주기 수집 작업은 수집 실패 때도 전송을 넣음 | 수집 실패가 알림·Wi-Fi 기록 전송까지 막지 않게 함 |
| 429는 처음 보낸 뒤 최대 3번 다시 보냄 | 계획 문구 "최대 3번까지 다시 보낸다" |
| 설치 ID를 읽지 못하면 보내지 않고 재시도 | 로컬 저장소 읽기 실패는 일시적이라고 봄 |
| 위치 항상 허용은 권한 창 없이 앱 정보 화면을 엶 | 사용자 요청. 권한 창으로 요청하면 두 번 눌러야 함 |
| 1초 미만 앱 사용 구간은 기록하지 않음 | 사용자 결정. 최근 앱 화면, 자동화 앱의 중간 화면, 계측 테스트 화면이 0초 구간을 대량으로 만듦. 실제로 쓴 앱이 0초로 잡히는 버그는 아니었음 |
| PC `notifications --new`는 `--app`·`--search`와 함께 쓸 수 없고, 출력한 마지막 알림까지만 읽음 처리 | 300줄에서 잘리거나 걸러진 알림을 놓치지 않게 함 |
| PC 파일은 `docs/`가 아니라 `tools/usage-log/`에 커밋 | 집 PC가 저장소를 볼 수 있음(설계 때는 볼 수 없다고 잘못 적음) |

## 전체 검토에서 판단할 미룬 항목

태스크 검토에서 가벼운 지적으로 분류해 미뤄 둔 것들입니다. 전체 검토자가 머지 전에 고칠 것을 고릅니다.

- Wi-Fi 감시(전체 검토에서 반드시 판단)
    - 서비스 시작 직후 수집 켜짐 상태를 읽기 전에 첫 연결이 오면, 저장된 옛 상태로 추정 표시 없는 기록이 생길 수 있음
    - 초기 연결의 첫 보고가 5초 제한 뒤에 오면 새 연결로 보고 가짜 집 도착이 생길 수 있음. 이제 즉시 전송도 한 번 더 나감
    - VPN 등으로 요청보다 넓게 읽은 연결 때문에 5초를 기다릴 수 있음
    - 보고·확인한 연결 목록이 감시 세션마다 나뉘지 않음. SSID가 늦게 보인 경우 저장된 상태를 보지 않음
    - 테스트가 없는 경로: 보고 안 된 초기 연결의 해제, 집 전환 알림 순서
    - 수집 켜짐은 이벤트 시각이 아니라 처리 시각에 읽음. 집에서 재부팅하면 도착 전송이 한 번 나감
    - 신호 경계에서 도착·출발이 반복되면 전환마다 메시지가 나갈 수 있음(`REPLACE`가 대기 중인 전송은 합침)
- 전송
    - 알림 서비스의 전송 예약 try/catch가 중복이고(Wi-Fi 감시가 이미 잡음) 주석 근거가 틀림
    - `setLastSendResult`의 결과를 쓰지 않고 버림
    - 2xx 직후 작업이 취소되면 같은 기록이 다시 감(PC가 걸러 냄)
    - 테스트가 없는 경로: 수집이 꺼져 있을 때 수집하지 않음, 일부 묶음만 성공, 여러 라운드의 순번, 전송됨 표시·순번 저장 실패, 주소만 있거나 키만 있는 경우와 앞뒤 공백, 키 생성 실패 때 상태 유지
    - 작업 예약 테스트가 네트워크 조건과 재시도 설정을 확인하지 않음
    - 웹후크: KDoc 요약 줄의 괄호, multipart의 파일 이름을 이스케이프하지 않음, `retry_after` 1.1초가 1101ms가 됨, 음수·NaN `retry_after`와 UTF-8 내용 테스트 없음
    - 암호화: 12바이트보다 짧은 첨부를 풀 때 다른 예외 종류가 남, 기록 하나가 한도를 넘는 경우와 나뉜 묶음의 meta 순번 테스트 없음, 분할 때 다시 압축·암호화하는 비용, KDoc의 FQCN
- 수집·저장
    - 기록 모델 KDoc 요약 줄의 괄호(`UsageLogRecord.kt`), JsonUtil의 `Class`·`Type` 오버로드에 `logInput` 없음
    - `InstalledAppResolver.resolve`가 앱 정보를 두 번 조회함
    - 알림 중복 거르기가 저장 성공 전에 기억함, 재시도가 `Throwable`을 잡음, 설정 읽기가 계속 실패하면 대기열이 무한히 커질 수 있음
    - 주기 수집 작업이 모든 예외에 재시도함, `ensureScheduled`가 예외를 던질 수 있음
    - 커서 저장 실패 경로 테스트 없음, import 순서
- 화면
    - 앱 이름 캐시가 화면 동안 갱신되지 않음
    - 웹후크 입력칸 입력과 오류 표시, 클립보드 민감 표시 경로, `ActionButton` 비활성 상태의 직접 테스트 없음
    - 저장 실패 토스트가 입력할 때마다 뜰 수 있음, 테스트 import 순서, 테스트 하나의 단언이 When 블록에 있음
    - 최근 기록 대기 함수의 2초 폴링이 느린 환경에서 흔들릴 수 있음
- PC 스크립트
    - `sql`에 쿼리 시간 제한이 없고, 패키지 이름·알림 키·epoch 열을 고를 수 있음(설명문에 경고 없음)
    - `notifications --new`는 시각 순으로 출력하지만 읽은 위치는 행 번호로 저장함. 순서가 뒤섞여 적재되면 건너뛰거나 반복할 수 있음
    - 날짜 범위를 준 `--new`는 범위 밖의 안 읽은 알림을 건너뛸 수 있고, 문서에 그 설명이 없음
    - 설명문에 스크립트 위치가 없음, 테스트의 표본 경로 설명에 저장소 폴더 이름이 들어 있음

## 확인 명령

- 2단계 관련 유닛 테스트: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.*" --tests "com.black.app.model.UsageLog*" --tests "com.black.app.ui.main.usagelog.*"` (오늘 145건 통과, 그 뒤 위치 이동과 1초 미만 제외 테스트를 추가)
- 화면 계측 테스트: `ANDROID_SERIAL=R3CX1015TQT ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.black.app.ui.main.usagelog.UsageLogScreenTest` (오늘 12건 통과, 건너뜀 0건)
- PC 스크립트 테스트
    1. Kotlin 표본 생성: `./gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.upload.UsageLogSampleExportTest"`
    2. `cryptography`를 설치한 Python으로 실행: `python3 -B -I -W error tools/usage-log/test_usage_log.py` (오늘 18건 통과)
