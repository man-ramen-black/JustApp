// Claude Code PostToolUse(Write|Edit) hook (Mac/Windows 공통)
// CLAUDE.md "변경한 파일 git add 스테이징" 규칙의 자동화. 저장소 안에서 쓰거나 고친 파일을 바로 스테이징
// 제외: docs/ 하위(스펙·플랜 등 커밋하지 않는 작업 산출물), 개인 설정(.claude/settings.local.json), 저장소 밖 파일
// gitignore 대상은 git add가 거부하므로 별도 처리 없음

import { execFileSync } from "node:child_process";
import path from "node:path";

const excludedPrefixes = ["docs/"];
const excludedFiles = [".claude/settings.local.json"];

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

  const filePath = payload?.tool_input?.file_path;
  const projectDir = process.env.CLAUDE_PROJECT_DIR || payload?.cwd;
  if (!filePath || !projectDir) {
    process.exit(0);
  }

  // 저장소 기준 상대 경로(구분자는 /로 통일)
  const relativePath = path.relative(projectDir, path.resolve(projectDir, filePath)).split(path.sep).join("/");
  const isOutsideProject = relativePath.startsWith("../") || path.isAbsolute(relativePath);
  const isExcluded =
    excludedPrefixes.some((prefix) => relativePath.startsWith(prefix)) || excludedFiles.includes(relativePath);
  if (isOutsideProject || isExcluded) {
    process.exit(0);
  }

  try {
    execFileSync("git", ["add", "--", relativePath], { cwd: projectDir, stdio: "ignore" });
  } catch {
    // gitignore 대상·git 저장소 아님 등은 스테이징하지 않고 통과
  }
  process.exit(0);
});
