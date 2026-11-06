# What's Agent Doing (한국어)

[tzafrir/whats-agent-doing](https://github.com/tzafrir/whats-agent-doing) 0.4.3을 복사해 상자에 그리는 고정 문구를 한국어로 바꾸고,
백그라운드 셸·모니터 표시와 세션 요약 세 줄을 더한 로컬 사본이다. 플러그인 이름과 `$.state` 키는 `whats-agent-doing-ko`로 바꿨다.

- 바꾼 곳
    - `hooks/register.tsx`: 상태 문구(대기·생각 중·답변 작성 중·승인 대기 등)와 턴 종료 문구
    - `hooks/activity-of.ts`: 도구 호출 라벨(`app.ts 읽기`, `"x" 검색`, `에이전트 실행: …`)
        - 파일 라벨은 전체 경로에서 파일 이름을 먼저 뽑은 뒤 60자로 자른다(원본은 경로를 먼저 잘라 긴 경로가 `su… 읽기`처럼 보였다. 원본을 따라갈 때 이 순서를 되돌리지 않는다)
    - `hooks/text.ts`: 시간 표기(`3분 05초`), 영어 복수형 도우미 제거
    - `tests/register.test.tsx`: 바뀐 문구에 맞춘 기대값
- 더한 기능: 백그라운드 셸·모니터 표시(원본에 없음)
    - 도는 동안 상자에 작업마다 한 줄(`$ 셸 › 설명`, `◉ 모니터 › 설명 · 이벤트 N개`)을 그린다
    - 턴이 끝난 뒤에도 작업이 남아 있으면 머리줄이 `백그라운드 N개 완료 대기`가 되어, 끝난 것과 알림을 기다리는 것이 구분된다
    - 작업 알림이 연 턴은 알림 원문 대신 `셸 완료 알림 읽는 중` 같은 문구와 결과 줄(`✓ 셸: 설명`, `✗ 셸: 설명 (종료 코드 1)`)로 보인다
    - 코드는 `hooks/background.ts`(알림 해석)와 `hooks/register.tsx`의 `followTask`·`absorb`에 있다
    - 셸이 끝난 것은 알림과 `TaskStop`으로만 안다. 턴 끝에 엔진이 주는 진행 중 작업 목록(`classic.Stop`)은 `cc-plugin-sec-default`가 이 모드를 건너뛰고 넘겨서 받지 못한다
    - 서브에이전트가 띄운 셸은 그리지 않는다(알림이 그 서브에이전트에게 가서 끝을 알 수 없다)
- 더한 기능: 세션 요약 세 줄(원본에 없음, [catch-me-up](https://github.com/oliverow/catch-me-up)의 옆 창 대신)
    - 상자를 펼치면(`▾`) 동작 기록 위에 `목표`·`상황`·`다음` 세 줄을 그린다. 접힌 상자에는 그리지 않는다
    - Haiku가 지난 요약과 그 뒤에 더해진 대화, `git status --short`·`git log --oneline -n 3` 출력을 받아 요약을 다시 쓴다
    - 보내는 대화는 줄여서 보낸다
        - 메시지는 1,500자에서 자르고, 최근 6개만 4,000자까지 둔다
        - 도구 호출은 도구 이름과 입력 앞 120자만 보내고 실행 결과는 보내지 않는다. 실패한 호출에는 `(오류)`를 붙인다
        - 갱신 한 번은 지난 요약 뒤의 대화 가운데 최근 3만 자까지만 읽고, 그 앞은 생략했다고 적어 보낸다. 앞의 내용은 지난 요약의 메모가 이어 준다
        - 저장된 요약이 없는 세션의 첫 요약은 처음 사용자 요청 3개를 더 붙인다
        - 그래서 세션 길이나 접어 둔 시간과 상관없이 갱신마다 Haiku 호출은 한 번이다
    - 호출 시점
        - 상자를 펼칠 때 한 번 호출해, 접혀 있는 동안 쌓인 대화를 반영한다
        - 펼쳐 있는 동안 도구를 쓴 턴이 끝날 때마다 한 번씩 호출한다. 대화만 오간 턴은 다음 갱신 때 함께 반영한다
        - 접혀 있으면 호출하지 않는다. `claude -p`처럼 그리는 화면이 없는 실행에서도 호출하지 않는다
    - 요약은 세션마다 모드 저장소(`~/.claude/plugins/store/`)에 남아 재시작·`--resume` 뒤에도 이어지고, 최근 50개 세션만 둔다
    - `/clear`·`/resume`·`/branch` 뒤에는 `$.session.id()`로 세션이 바뀐 것을 알아채 그 세션의 요약을 읽는다. `classic.SessionStart`는 `cc-plugin-sec-default`가 이 모드를 건너뛰고 넘겨서 쓰지 않는다
    - 갱신이 실패하면 이전 세 줄을 그대로 두고 아래에 `요약 갱신 실패: <이유>`를 그린다
    - 코드는 `hooks/summary.ts`(대화 자르기·프롬프트·응답 해석)와 `hooks/register.tsx`의 `summarize`·`followSession`·`toggle`에 있다
- 그대로인 것: Bash 명령 설명과 에이전트 이름은 모델이 도구 호출에 적은 값을 그대로 보여 주므로, 그 언어는 이 모드가 정하지 않는다
- 불러오는 방법: 상위 폴더 `~/.claude/local-mods`가 폴더 마켓플레이스(`local-mods`)이고, `whats-agent-doing-ko@local-mods`로 설치되어 있다
    - 이 폴더를 고친 뒤 `/reload-plugins`를 실행하면 이 폴더에서 다시 읽어 반영된다(실행 결과에 `re-read from its folder`라고 나온다)
    - 설치 기록(`~/.claude/plugins/installed_plugins.json`)의 설치 경로는 캐시 사본(`~/.claude/plugins/cache/local-mods/whats-agent-doing-ko/<버전>/`)이므로, 버전을 올렸으면 `command claude plugin update whats-agent-doing-ko@local-mods`로 캐시도 맞춰 둔다
- 원본 갱신을 따라가려면 새 버전의 `hooks/`를 이 폴더와 비교해 문구가 아닌 변경만 옮긴 뒤 `claude plugin validate`와 `claude plugin test`로 확인한다
