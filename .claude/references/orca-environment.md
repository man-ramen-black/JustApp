# Orca 환경 참고

Orca(https://github.com/stablyai/orca, 문서 https://www.onorca.dev/docs)는 여러 코딩 에이전트(Claude Code·Codex 등)를 격리된 git 워크트리에서 병렬로 돌리는 Electron 데스크톱 앱(ADE)이다. JustApp 브랜치 작업은 기본 체크아웃 아래 `.claude/worktrees/<주제>` 워크트리에서 하며(아래 워크트리 절), `git worktree list` 첫 줄이 기본 체크아웃이다.

- Orca 탭·워크트리·내장 브라우저 조작은 `orca-cli` 스킬을 쓴다. 이 스킬과 `computer-use`·`orchestration`은 Orca가 `~/.agents/skills/`에 설치하므로 저장소에 두지 않는다.
- Claude Code 자체 기능(`ListAgents`·`SendMessage`·백그라운드 Bash)으로 되는 일에는 Orca를 쓰지 않는다.

## 워크트리

- 기본 체크아웃(`~/AndroidStudioProjects/JustApp`, Orca 사이드바의 JustApp 기본 항목)은 `release`에 두고 브랜치를 바꾸지 않는다. Orca 저장소 기준 브랜치(worktreeBaseRef)도 `release`이며, 세션은 여기서 시작한다.
- 새 브랜치는 `git worktree add -b <브랜치> .claude/worktrees/<주제> release` 후 `EnterWorktree(path)`로 세션을 옮긴다. `EnterWorktree(name)`은 origin 기본 브랜치(`master`)에서 분기하므로 쓰지 않는다. 기존 브랜치는 `git -C <기본 체크아웃> worktree add .claude/worktrees/<주제> <브랜치>` 후 같은 방식으로 옮긴다.
- `orca worktree create`로 최상위 워크트리를 만들지 않는다. 브랜치명의 `/`가 `-`로 바뀌고 사이드바 항목이 계속 늘어난다. `.claude/worktrees` 아래는 외부 워크트리 숨김(`externalWorktreeVisibility: hide`)이라 사이드바에 늘어나지 않는다.
- "새 세션에서 워크트리 만들어서 진행"은 위처럼 워크트리를 만들고 커밋하지 않는 `docs/superpowers/`·`local.properties`를 복사한 뒤, `orca agent launch --agent claude --worktree id:<JustApp repoId>::<기본 체크아웃>`으로 기본 체크아웃에 세션을 띄우고 프롬프트 첫 단계에 `EnterWorktree(path)`를 넣는다. 숨김 외부 워크트리라 Orca에서 그 경로를 직접 대상으로 잡지 않는다.
- 워크트리를 지울 때는 그 안의 커밋하지 않은 `docs/superpowers/` 문서를 먼저 옮긴다. `git worktree remove --force`가 함께 지운다.
- 기본 체크아웃에서 시작해 `EnterWorktree`로 옮긴 세션에는 시작할 때 읽은 release의 `CLAUDE.md`와 워크트리의 `CLAUDE.md`가 함께 남고, 스킬도 기본 체크아웃 것과 워크트리 것(`.claude/worktrees/<주제>:<스킬>`)이 두 벌로 보인다. 워크트리 파일을 고칠 때는 워크트리 스킬을 쓴다. 워크트리에서 바로 시작한 세션은 워크트리 것만 읽는다. 그래서 옮겨 갈 브랜치가 release보다 뒤처져 `CLAUDE.md`·`.claude/`가 옛 버전이면 release를 먼저 병합해 두 문서를 같게 맞춘다.

## 단축키

- 설정 파일은 `~/.orca/keybindings.json`이며 `platforms.darwin` 아래에 `"<command.id>": ["Mod+Alt+ArrowRight"]` 형식으로 쓴다. **override는 앱 기본값을 병합하지 않고 완전히 대체**하므로, 유지할 기본 바인딩도 함께 나열한다. 파일 변경은 핫리로드되며 안 되면 Orca를 재시작한다.
- 앱 기본 키맵과 command id는 `/Applications/Orca.app/Contents/Resources/app.asar`를 `grep -a`로 조회한다(`{id:"...",defaultBindings:...}` 형태).
- 터미널에 포커스가 있을 때는 명령 정의에 `allowInTerminal:true`(또는 `scope:"terminal"`)가 있는 단축키만 Orca로 전달된다. 이 플래그는 keybindings.json으로 바꿀 수 없으므로, 터미널에서 쓰려면 그 플래그가 있는 명령을 원하는 키로 재배치한다.
    - 예: 전 타입 탭 순환(`tab.next/previousAllTypes`)은 터미널에서 동작하지 않는다. 터미널 탭 순환은 `tab.previous/nextTerminal`(기본 Ctrl+PageUp/Down), md와 터미널 왕복은 `tab.previousRecent`(Ctrl+Tab)를 쓴다.
    - 예: `Cmd+Option+←/→`가 탭 이동에 먹지 않으면 기본으로 그 키를 쓰는 `worktree.history.back/forward`를 다른 키(`Mod+Ctrl+ArrowLeft/Right`)로 옮기고 `tab.previous/nextTerminal`을 그 자리에 둔다.
- Settings → Shortcuts UI는 서로 다른 scope(`tabs`·`global`) 사이의 충돌을 잡지 못할 수 있다.

## 복사

- Claude Code 패널의 텍스트 복사는 Orca가 아니라 Claude Code의 `/copy`를 쓴다(`/copy N`은 N번째 이전 응답). Claude Code가 마우스 선택을 직접 그리므로 Orca의 복사 경로는 빈 선택을 보고 아무것도 하지 않는다.
- 드래그 복사는 `~/.claude.json`의 `copyOnSelect: true`(기본값)에 의존한다. `false`면 드래그해도 클립보드에 들어가지 않는다. 값은 `/config`의 "Copy on select"로 확인한다.
- 일반 터미널 탭의 복사는 `Cmd+Shift+C`, 붙여넣기는 `Cmd+V`다. 터미널에서 `Cmd+C`는 아무 동작도 하지 않아 이전 클립보드 내용이 붙여넣어진다. 바꾸려면 keybindings.json에 `"terminal.copySelection": ["Mod+Shift+C","Mod+C"]`를 넣거나 Settings → Terminal Pane → Copy on Select를 켠다.

## 터미널 렌더링

- 터미널은 xterm.js다. 상태 표시줄의 블록 문자 그림이 행 사이 틈으로 쪼개져 보이면 `terminalGpuAcceleration`이 `off`(DOM 렌더러)인 것이 원인이다. 블록 문자를 셀 크기에 맞춰 그리는 기능은 WebGL·Canvas 렌더러에서만 동작하므로 GPU acceleration을 `auto`로 되돌린다.
- 터미널 설정 파일(`~/Library/Application Support/orca/profiles/local-default/orca-data.json`의 `terminal*` 키)을 직접 고칠 때는 Orca를 완전히 종료한 뒤 고친다. 실행 중에는 메모리 값으로 덮어쓴다.
