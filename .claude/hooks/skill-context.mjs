// Claude Code PreToolUse(Skill) hook (Mac/Windows 공통)
// super-flow 단계 스킬(brainstorming·writing-plans·subagent-driven-development) 호출 시 그 단계의 진행 규칙을 컨텍스트로 주입
// 긴 플로우에서 초기 규칙이 컨텍스트 압축으로 희석되는 것을 막기 위해 단계마다 다시 주입
// git 마무리 스킬(finishing-a-development-branch·using-git-worktrees)은 메뉴 제시·확인 대기·origin 기본 브랜치 분기가
// 저장소 규칙(CLAUDE.md git 절)과 반대라, 호출 시점에 저장소 규칙을 주입

const contextBySkill = {
  "superpowers:brainstorming": [
    "브레인스토밍 진행 규칙(아래 규칙이 스킬 기본 지침보다 우선):",
    "- 권장 항목이 있는 질문은 사용자에게 묻지 말고 권장 항목으로 바로 진행",
    '- 스킬에 "2~3개 제안" 지침이 있어도 무조건 제안하지 말 것. 권장안은 알아서 채택하고 정말 애매한 항목만 질문',
    "- 질문이 둘 이상이면 AskUserQuestion 한 번에 묶어서 질문",
    '- 설계 결정이 정해지면 "스펙 문서를 작성할까요?"처럼 작성 여부를 되묻지 말고 같은 턴에 스펙 문서를 작성. 검토는 작성된 본문으로 받음',
    "- 스펙 문서에 시작 HEAD(git rev-parse HEAD)를 기록",
    "- 브레인스토밍으로 생성된 스펙·설계 문서는 커밋 금지",
    "- 스펙 문서 작성 후 다음 단계로 자동 진행하지 말고, 사용자가 스펙 문서를 검토·승인한 뒤에 진행",
  ],
  "superpowers:writing-plans": [
    "writing-plans 진행 규칙(아래 규칙이 스킬 기본 지침보다 우선):",
    "- 대상 플랜 문서(docs/superpowers/plans/)가 이미 있으면 통째로 다시 쓰지 말고 읽어서 검토·보강",
    "- 스펙 문서의 시작 HEAD를 플랜 문서에 옮겨 적음",
    '- 플랜 문서를 쓴 뒤 "계획 검토"·"실행 방식 선택"을 묻지 말고 같은 턴에 subagent-driven-development로 구현 시작(사용자 검토는 스펙 문서에서만 받음)',
    '- 각 태스크에 "적용 스킬"과 그 SKILL.md 경로를 명시. 스킬명은 외워 쓰지 말고 사용 가능한 스킬 목록에서 description으로 확인 (예: JVM 유닛 테스트 unittest, Fragment 계측 테스트 ui-test, Compose 화면 compose, 커밋 commit)',
    "- 플랜 문서는 git add·커밋 금지",
  ],
  "superpowers:subagent-driven-development": [
    "구현 단계 진행 규칙(아래 규칙이 스킬 기본 지침보다 우선):",
    '- 태스크를 서브에이전트에 맡길 때 플랜에 명시된 "적용 스킬"의 SKILL.md 경로를 프롬프트에 포함. 명시가 없어도 description이 태스크와 맞는 스킬이 있으면 포함',
    "- 테스트는 작성에 그치지 않고 실제로 실행해 통과를 확인한 뒤 완료로 보고",
    "- 커밋은 commit 스킬을 따름",
    "- 스펙·플랜 문서(docs/superpowers/)는 git add·커밋 금지",
  ],
  "superpowers:finishing-a-development-branch": [
    "브랜치 마무리 규칙(아래 규칙이 스킬 기본 지침보다 우선, 출처 CLAUDE.md git 절):",
    '- 옵션 메뉴를 제시하지 말 것. 사용자가 "머지"·"마무리해줘"라고 하지 않았으면 release에 병합하지 말고 워크트리에 머문 채 브랜치·커밋·검증 결과만 보고',
    '- "머지"·"마무리해줘"를 받았으면 묻지 말고 release 병합 후 같은 흐름에서 작업 워크트리와 브랜치까지 삭제',
    "- 워크트리 삭제 전 커밋하지 않은 docs/superpowers/를 옮기고, git status에 그 밖의 미커밋 변경이 없을 때만 --force로 삭제(있으면 멈추고 보고)",
    "- git push는 명시적 지시가 있을 때만",
  ],
  "superpowers:using-git-worktrees": [
    "워크트리 규칙(아래 규칙이 스킬 기본 지침보다 우선, 출처 CLAUDE.md git 절):",
    "- 워크트리는 release에서 분기해 .claude/worktrees/<주제>에 생성: git worktree add -b <브랜치> .claude/worktrees/<주제> release 후 EnterWorktree(path)",
    "- EnterWorktree(name)은 origin 기본 브랜치(master)에서 분기하므로 쓰지 않음. orca worktree create(최상위 워크트리)도 쓰지 않음",
  ],
};

let raw = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  raw += chunk;
});
process.stdin.on("end", () => {
  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    process.exit(0);
  }

  // 대상 단계 스킬이 아니면 아무것도 주입하지 않음
  const lines = contextBySkill[payload?.tool_input?.skill];
  if (!lines) {
    process.exit(0);
  }

  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        additionalContext: lines.join("\n"),
      },
    }),
  );
  process.exit(0);
});
