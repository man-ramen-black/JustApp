# 상태 표시줄 (Clawd 마스코트 + claude-hud)

Claude Code 상태 표시줄 왼쪽에 Clawd 마스코트를 quadrant(2×2 사분면) 블록 문자로 그리고, 오른쪽에 [claude-hud](https://github.com/jarrodwatts/claude-hud) 정보를 붙여 보여 준다. 마스코트는 작업 상태(생각·도구 실행·서브에이전트·완료·권한 대기)에 따라 움직이고, 컨텍스트 사용률이 높을수록 몸이 빨개진다. HUD 출력에는 effort 배지, 둥근 진행 막대, 로그인 계정 배지를 덧붙인다.

상태 표시줄은 사용자 레벨(`~/.claude`) 설정이라 모든 프로젝트에 적용된다. 이 폴더는 그 설정을 다른 PC에 똑같이 옮기기 위한 원본과 설치기다.

## 구성

| 파일 | 역할 |
|---|---|
| `clawd-quadrant.mjs` | 마스코트 렌더 + HUD 병합(상태 표시줄 본체). `~/.claude/statusline/clawd-quadrant.mjs`와 같은 파일 |
| `install.mjs` | 사용자 레벨 설치기 |
| `mascot-config.json` | 마스코트 플러그인 설정(`~/.claude/plugins/claude-code-mascot-statusline/config.json`) |
| `claude-hud-config.json` | HUD 표시 항목 설정(`~/.claude/plugins/claude-hud/config.json`) |

| 의존 플러그인 | 역할 | 없으면 |
|---|---|---|
| `claude-code-mascot-statusline` | 상태 기록 훅(`hook-state-writer.js`)으로 작업 상태를 `~/.claude/plugins/claude-code-mascot-statusline/state/`에 기록 | 마스코트가 idle로 고정 |
| `claude-hud` | 오른쪽 HUD 열(모델·브랜치·컨텍스트·사용량) | 마스코트만 표시 |

## 새 PC 설치

1. 이 저장소를 Claude Code로 열고 신뢰를 허용한다. 프로젝트 설정(`.claude/settings.json`)이 마스코트·HUD 플러그인 설치를 안내한다.
2. 저장소 루트에서 설치기를 실행한다.
   ```
   node .claude/statusline/install.mjs
   ```
   - 렌더 스크립트 복사, `~/.claude/settings.json`의 `statusLine`·플러그인 등록(기존 파일은 `settings.json.bak-statusline`으로 백업), 플러그인 설정 반영, 마스코트 의존 패키지 설치(`npm install --omit=dev`)를 한 번에 한다.
   - 플러그인이 아직 없으면 설치할 `/plugin` 명령을 알려 준다. 설치한 뒤 설치기를 한 번 더 실행한다.
   - 여러 번 실행해도 결과가 같다.
3. Claude Code를 재시작한다.

`/claude-code-mascot-statusline:setup`은 실행하지 않는다. 이 명령은 `statusLine`을 플러그인 기본 렌더러로 덮고, 플러그인이 `hooks/hooks.json`으로 이미 등록하는 상태 기록 훅을 버전 번호가 들어간 경로로 한 번 더 등록한다. 실행했다면 설치기를 다시 실행해 `statusLine`을 되돌린다.

## 수정과 동기화

- 원본은 각 PC의 `~/.claude/statusline/clawd-quadrant.mjs`다. 상태 표시줄은 매 렌더마다 새 `node` 프로세스로 실행되므로 저장 즉시 반영된다.
- 고친 뒤에는 이 폴더로 복사해 커밋한다. 이 파일은 홈 사본과 그대로 비교·복사할 수 있도록 원문을 유지한다(주석 형식 규칙 적용 대상에서 제외).
- 상태·애니메이션 동작은 플러그인 `dist`가 아니라 `clawd-quadrant.mjs`가 결정한다. 플러그인 코드를 고쳐도 상태 표시줄에는 반영되지 않는다.
- HUD 표시 항목은 `claude-hud-config.json`을 고치고 설치기를 다시 실행한다.

## 미리 보기와 디버그

```
echo '{}' | CLAWD_NO_HUD=1 CLAWD_FORCE_STATE=done node ~/.claude/statusline/clawd-quadrant.mjs
```

| 환경 변수 | 효과 |
|---|---|
| `CLAWD_FORCE_STATE=thinking` | 상태 강제 지정(idle·thinking·tool_running·subagent_running·done 등) |
| `CLAWD_FORCE_PCT=70` | 컨텍스트 사용률 강제(높을수록 몸이 빨개짐) |
| `CLAWD_NO_HUD=1` | HUD 생략, 마스코트만 출력 |
| `CLAWD_NOW=<ms>` | 애니메이션 프레임 선택용 현재 시각 주입 |

- 계정 배지는 `~/.claude.json`의 `oauthAccount.emailAddress`를 읽는다. Vertex로 실행 중이면(`CLAUDE_CODE_USE_VERTEX`) 계정 대신 Vertex 배지를 그린다.
- 블록 문자 그림이 행 사이 틈으로 쪼개져 보이면 터미널 렌더러 문제다(Orca는 GPU acceleration을 `auto`로, `.claude/references/orca-environment.md` 참고).
