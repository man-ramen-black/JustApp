---
name: merge
description: 작업 브랜치를 release에 머지한다. 머지 방식(`--ff-only`, 리베이스 후 `--ff-only`, `--no-ff`)을 판정해 기본 체크아웃에서 머지하고 작업 워크트리·브랜치와 백업 브랜치를 정리한다. 사용자가 "머지", "머지해줘", "release에 넣어줘"라고 하거나 "마무리해줘"가 머지 단계에 이를 때 사용한다. 지시 없이 작업이 끝났다는 이유로 쓰지 않는다.
---

# 머지 스킬

## 원칙

- 커밋 정리는 머지 전에 `squash` 스킬로 끝낸다. 머지 단계에서 커밋을 합치지 않고 브랜치 히스토리를 그대로 올린다.
- 이번 세션에서 테스트 코드를 고쳤으면 실행·통과를 확인한 뒤 머지한다.
- `git push`는 하지 않는다. 머지 커밋 메시지는 CLAUDE.md의 git 기본 한 줄을 따른다.

## 1. 방식 판정

```
git merge-base --is-ancestor release <브랜치> && echo fast-forward-가능
git worktree list                 # 워크트리마다 체크아웃한 브랜치
git branch --no-merged release
git branch -a --contains <release..브랜치의 가장 오래된 커밋>   # 작업 브랜치·backup/* 밖에 나오면 재작성 금지
```

| 판정 | 리베이스 | 머지 |
|:---|:---|:---|
| fast-forward 가능(브랜치가 이미 release 끝 위) | 하지 않음 | `--ff-only` |
| 단독(다른 워크트리가 체크아웃한 release 미머지 브랜치가 없음) | `git rebase release` | `--ff-only` |
| 병행(그런 브랜치가 하나라도 있음) | 하지 않음 | `--no-ff` |

- 단독이어도 작업 브랜치의 커밋이 push됐거나(`origin/*`) 다른 브랜치에 들어 있으면 리베이스하지 않고 `--no-ff`로 머지한다. 리베이스하면 같은 내용이 두 해시로 남는다.
- 워크트리 없이 남은 미머지 브랜치(`backup/*` 등)는 release를 움직이지 않으므로 병행 판정에서 뺀다.
- 판정이 애매하면 병행으로 처리한다. 단독으로 잘못 보고 리베이스하면 남의 커밋이 범위에 들지만, `--no-ff`는 머지 커밋 하나가 느는 데서 그친다.
- 병행에서 선형을 지키려 하면 다른 세션이 release를 계속 전진시켜 리베이스·`--ff-only` 재시도가 끝나지 않는다.

## 2. 리베이스 (단독일 때만)

작업 브랜치 워크트리에서 `git rebase release`로 올린다.

- import·문서 행처럼 양쪽이 각자 추가한 충돌은 양쪽 유지로 해소한다. 같은 로직을 양쪽이 다르게 고쳤으면 `git rebase --abort` 후 보고한다.
- 해소했으면 컴파일로 확인한다(`:app:compileDebugKotlin`, 테스트 코드가 대상이면 `:app:compileDebugAndroidTestKotlin`). 한쪽이 지운 import를 다른 쪽 신규 코드가 쓰는 경우처럼 충돌 없이 자동 병합된 부분은 컴파일에서만 드러난다.
- 리베이스 뒤 `git log --oneline HEAD..release`가 비어 있지 않으면 한 번 더 올리고, 두 번 넘게 밀리면 `--no-ff`로 바꾼다.

## 3. 머지 (기본 체크아웃)

release는 기본 체크아웃(`git worktree list` 첫 줄)에 있다. 워크트리 세션의 격리 가드가 기본 체크아웃을 대상으로 한 git 명령(`git -C`)을 거부하면 `ExitWorktree(keep)`로 나와 머지한다. 작업 워크트리는 4단계에서 지우므로 다시 들어가지 않는다.

```
git branch --show-current          # release인지 머지 직전에 다시 확인
git diff --cached --name-only      # --no-ff일 때만: staged 파일 확인
git merge --ff-only <브랜치>        # 또는 git merge --no-ff --no-edit <브랜치>
```

- 기본 체크아웃이 release가 아니면 바꾸지 않는다(다른 세션의 작업 트리). release로 임시 워크트리(`.claude/worktrees/merge-release`)를 만들어 거기서 머지하고 지운다.
- 기본 체크아웃은 세션 공용이라 다른 세션이 staged 해 둔 파일이 머지 커밋에 함께 실릴 수 있다. `--no-ff`에서 staged 파일이 있으면 내리지 말고 멈춰 보고한다. `--ff-only`는 커밋을 만들지 않으므로 확인하지 않는다.
- `--ff-only`가 거부되면 그 사이 release가 움직인 것이다. 다시 리베이스하지 않고 `--no-ff`로 바꾼다.
- `--no-ff` 충돌은 2단계와 같은 기준으로 해소하고 컴파일한 뒤 머지 커밋을 만든다. 커밋 직전 `git diff --cached --name-only`에 `git diff --name-only HEAD...<브랜치>` 밖의 파일이 있으면 해소하는 사이 다른 세션이 staged 한 것이므로 멈춰 보고한다.

## 4. 정리

- `git branch --merged release`에 작업 브랜치가 있는지 확인한다. 없으면 아무것도 지우지 않고 보고한다.
- 머지가 확인되면 같은 흐름에서 작업 워크트리와 브랜치를 지운다. 워크트리에 남아 커밋으로 검토받는 것은 머지 전 상태에만 해당하므로, 사용자가 남기라고 했을 때만 남긴다.
  - 워크트리에 `docs/` 밖의 미커밋 변경이 있으면 지우지 않고 보고한다.
  - 커밋하지 않는 `docs/superpowers/` 문서는 기본 체크아웃의 같은 경로로 먼저 옮긴다. `remove --force`가 함께 지운다.
  - `git worktree remove --force <워크트리>` 후 `git branch -d <브랜치>`
- 이번 작업의 `backup/*` 브랜치도 지운다. `git branch --list 'backup/*'` 전량을 나열해 이번 작업의 것인지 가른다.

## 5. 보고

- 판정 결과와 근거(병행이면 다른 워크트리의 브랜치명), 쓴 머지 방식
- release 최신 커밋과 이번에 올라간 커밋
- 지운 브랜치, 남긴 브랜치·워크트리
- push하지 않았다는 사실
