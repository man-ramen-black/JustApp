// Claude Code PreToolUse(Skill) hook (Mac/Windows 공통)
// superpowers:brainstorming 스킬 호출 시 진행 규칙을 컨텍스트로 주입

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

  // 브레인스토밍 스킬이 아니면 아무것도 주입하지 않음
  if (payload?.tool_input?.skill !== "superpowers:brainstorming") {
    process.exit(0);
  }

  const additionalContext = [
    "브레인스토밍 진행 규칙(아래 규칙이 스킬 기본 지침보다 우선):",
    "- 권장 항목이 있는 질문은 사용자에게 묻지 말고 권장 항목으로 바로 진행",
    '- 스킬에 "2~3개 제안" 지침이 있어도 무조건 제안하지 말 것. 권장안은 알아서 채택하고 정말 애매한 항목만 질문',
    "- 브레인스토밍으로 생성된 스펙·설계 문서는 커밋 금지",
  ].join("\n");

  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        additionalContext,
      },
    }),
  );
  process.exit(0);
});
