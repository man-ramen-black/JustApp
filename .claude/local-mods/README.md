# 로컬 모드 (whats-agent-doing-ko)

프롬프트 위 상자에 Claude가 지금 하는 일, 도는 백그라운드 작업, 지난 동작을 한국어로 보여 주는 모드(`whats-agent-doing-ko`)의 사본과 설치기다. [tzafrir/whats-agent-doing](https://github.com/tzafrir/whats-agent-doing) 0.4.3을 복사해 고친 것이며, 바꾼 곳과 더한 기능(백그라운드 셸·모니터 표시, 세션 요약 세 줄)은 `whats-agent-doing-ko/README.md`에 있다.

모드는 사용자 레벨(`~/.claude`) 플러그인이라 모든 프로젝트에 적용된다. 이 폴더는 각 PC의 `~/.claude/local-mods`(폴더 마켓플레이스 `local-mods`)를 다른 PC에 똑같이 옮기기 위한 사본이다.

## 구성

| 파일 | 역할 |
|---|---|
| `.claude-plugin/marketplace.json` | 폴더 마켓플레이스 매니페스트. `~/.claude/local-mods/.claude-plugin/marketplace.json`과 같은 파일 |
| `whats-agent-doing-ko/` | 모드 본체. `~/.claude/local-mods/whats-agent-doing-ko/`와 같은 폴더 |
| `install.mjs` | 사용자 레벨 설치기 |

## 새 PC 설치

1. Claude Code를 최신으로 올린다. 모드(함수 훅과 `AbovePrompt` 상자)는 최근 버전에서만 동작하며, 2.1.319에서 확인했다.
2. 저장소 루트에서 설치기를 실행한다.
   ```
   node .claude/local-mods/install.mjs
   ```
   - 모드 폴더와 매니페스트 복사, `local-mods` 마켓플레이스 등록, 모드 설치(버전이 바뀌었으면 갱신), 원본 `whats-agent-doing@tzafrir` 비활성을 한 번에 한다.
   - 등록과 설치는 `claude plugin` 명령으로 하므로 `claude`가 PATH에 있어야 한다. 실패한 단계가 있으면 Claude Code 안에서 실행할 `/plugin` 명령을 알려 준다.
   - 기존 모드 폴더가 저장소 사본과 다르면 `~/.claude/local-mods.bak/<모드 이름>`으로 옮긴 뒤 복사한다. 백업은 직전 것 하나만 남는다.
   - 여러 번 실행해도 결과가 같다.
3. Claude Code를 재시작하거나, 실행 중인 세션에서 `/reload-plugins`를 실행한다.

폴더 마켓플레이스는 절대 경로로 등록되어 PC마다 경로가 다르므로, 프로젝트 설정(`.claude/settings.json`)에는 등록하지 않는다.

## 수정과 동기화

- 원본은 각 PC의 `~/.claude/local-mods/`다. 고친 뒤 `/reload-plugins`로 확인하고, 이 폴더로 복사해 커밋한다(macOS 기준).
  ```
  rsync -a --delete --exclude /install.mjs --exclude /README.md --exclude node_modules --exclude .claude-plugin/types --exclude .DS_Store ~/.claude/local-mods/ .claude/local-mods/
  ```
- 모드 파일은 홈 사본과 그대로 비교하고 복사할 수 있도록 원문을 유지한다(주석 형식, 이름 규칙 적용 대상에서 제외).
- 다른 PC에서 고친 내용은 그 PC에서 먼저 이 폴더로 옮겨 커밋한 뒤 다른 PC에 설치한다. 설치기는 저장소 사본으로 덮어쓰고, 덮어쓴 폴더는 백업 하나만 남긴다.
- 모드는 폴더에서 바로 읽히므로 폴더만 바뀌어도 반영된다. `plugin.json`의 `version`을 올려 커밋했으면, 설치기가 다른 PC의 설치 기록과 캐시(`~/.claude/plugins/cache/local-mods/`)도 `claude plugin update`로 맞춘다.
- 커밋하기 전에 매니페스트 검증과 모드 테스트를 돌린다.
  ```
  claude plugin validate .claude/local-mods
  claude plugin test .claude/local-mods/whats-agent-doing-ko
  ```
- 새 모드를 더할 때는 이 폴더에 모드 폴더를 두고 `marketplace.json`의 `plugins`에 항목을 추가한다. 설치기는 매니페스트에 적힌 모드를 모두 설치하며, 원본 플러그인을 대체하는 모드는 `install.mjs`의 `REPLACES`에 원본 id를 적는다.
- 세션 요약은 모드 저장소(`~/.claude/plugins/store/`)에 PC마다 따로 쌓이며, 이 폴더로 옮기지 않는다.
