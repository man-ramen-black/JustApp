#!/usr/bin/env node
// 상태 표시줄(Clawd 마스코트 quadrant 렌더 + claude-hud) 사용자 레벨 설치기 (macOS·Windows 공통)
//
// 처리 항목
//   1) clawd-quadrant.mjs를 ~/.claude/statusline/으로 복사
//   2) ~/.claude/settings.json의 statusLine 설정, 마스코트·HUD 플러그인과 마켓플레이스 등록 (기존 파일은 백업)
//   3) 마스코트·HUD 플러그인 설정(config.json) 반영
//   4) 마스코트 플러그인 의존 패키지 설치(node_modules가 없을 때만)
//   5) 플러그인 설치 상태 점검과 안내
//
// 실행: node .claude/statusline/install.mjs (여러 번 실행해도 같은 결과)
// clawd-quadrant.mjs가 홈 디렉터리 기준 ~/.claude 경로를 고정으로 쓰므로 CLAUDE_CONFIG_DIR은 따르지 않음

import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CLAUDE_DIR = path.join(homedir(), ".claude");
const SETTINGS = path.join(CLAUDE_DIR, "settings.json");
const STATUSLINE_DIR = path.join(CLAUDE_DIR, "statusline");
const SCRIPT_DEST = path.join(STATUSLINE_DIR, "clawd-quadrant.mjs");

const MASCOT = {
  pluginId: "claude-code-mascot-statusline@claude-code-mascot-statusline",
  marketplace: "claude-code-mascot-statusline",
  repo: "TeXmeijin/claude-code-mascot-statusline",
  configSource: path.join(HERE, "mascot-config.json"),
  configDest: path.join(CLAUDE_DIR, "plugins", "claude-code-mascot-statusline", "config.json"),
  cacheRoot: path.join(CLAUDE_DIR, "plugins", "cache", "claude-code-mascot-statusline", "claude-code-mascot-statusline"),
};
const HUD = {
  pluginId: "claude-hud@claude-hud",
  marketplace: "claude-hud",
  repo: "jarrodwatts/claude-hud",
  configSource: path.join(HERE, "claude-hud-config.json"),
  configDest: path.join(CLAUDE_DIR, "plugins", "claude-hud", "config.json"),
  entry: path.join(CLAUDE_DIR, "plugins", "marketplaces", "claude-hud", "dist", "index.js"),
};

const ok = (message) => console.log(`  \x1b[32m✓\x1b[0m ${message}`);
const warn = (message) => console.log(`  \x1b[33m!\x1b[0m ${message}`);

function fail(message) {
  console.error(`\n\x1b[31m✗ ${message}\x1b[0m`);
  process.exit(1);
}

function readJson(filePath, fallback) {
  if (!existsSync(filePath)) {
    return fallback;
  }
  try {
    return JSON.parse(readFileSync(filePath, "utf8"));
  } catch {
    fail(`JSON 파싱 실패, 파일이 손상되지 않았는지 확인 후 다시 실행: ${filePath}`);
  }
}

function writeJson(filePath, value) {
  mkdirSync(path.dirname(filePath), { recursive: true });
  writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

// 객체는 키 단위로 재귀 병합, 그 외 값은 저장소 값으로 교체(사용자가 따로 둔 키는 보존)
function deepMerge(base, override) {
  const result = { ...base };
  for (const [key, value] of Object.entries(override)) {
    const isObject = value && typeof value === "object" && !Array.isArray(value);
    result[key] = isObject ? deepMerge(base?.[key] ?? {}, value) : value;
  }
  return result;
}

// 1.2.10 > 1.2.9처럼 숫자 단위로 비교한 최신 버전 디렉터리
function latestVersionDir(root) {
  if (!existsSync(root)) {
    return null;
  }
  const versions = readdirSync(root, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort((left, right) => {
      const leftParts = left.split(".").map(Number);
      const rightParts = right.split(".").map(Number);
      for (let index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
        const difference = (leftParts[index] || 0) - (rightParts[index] || 0);
        if (difference !== 0) {
          return difference;
        }
      }
      return 0;
    });
  return versions.length ? path.join(root, versions[versions.length - 1]) : null;
}

console.log("\n\x1b[1m상태 표시줄 설치\x1b[0m\n");

// 1. 렌더 스크립트 복사
mkdirSync(STATUSLINE_DIR, { recursive: true });
copyFileSync(path.join(HERE, "clawd-quadrant.mjs"), SCRIPT_DEST);
ok(`렌더 스크립트 설치: ${SCRIPT_DEST}`);

// 2. settings.json 갱신 (경로 구분자는 Windows 대비 /로 통일, 공백 대비 따옴표)
const settings = readJson(SETTINGS, {});
if (existsSync(SETTINGS)) {
  copyFileSync(SETTINGS, `${SETTINGS}.bak-statusline`);
  ok(`기존 settings.json 백업: ${SETTINGS}.bak-statusline`);
}
settings.statusLine = {
  type: "command",
  command: `node "${SCRIPT_DEST.split(path.sep).join("/")}"`,
  refreshInterval: 1,
};
settings.enabledPlugins = settings.enabledPlugins ?? {};
settings.extraKnownMarketplaces = settings.extraKnownMarketplaces ?? {};
for (const plugin of [MASCOT, HUD]) {
  settings.enabledPlugins[plugin.pluginId] = true;
  settings.extraKnownMarketplaces[plugin.marketplace] =
    settings.extraKnownMarketplaces[plugin.marketplace] ?? { source: { source: "github", repo: plugin.repo } };
}
writeJson(SETTINGS, settings);
ok("statusLine 설정, 마스코트·HUD 플러그인 등록");

// 3. 플러그인 설정 반영
for (const plugin of [MASCOT, HUD]) {
  writeJson(plugin.configDest, deepMerge(readJson(plugin.configDest, {}), readJson(plugin.configSource, {})));
  ok(`플러그인 설정 반영: ${plugin.configDest}`);
}

// 4·5. 의존 패키지와 설치 상태 점검
console.log("\n\x1b[1m플러그인 점검\x1b[0m");
const pendingPlugins = [];

const mascotRoot = latestVersionDir(MASCOT.cacheRoot);
if (!mascotRoot) {
  warn("마스코트 플러그인 미설치: 상태(애니메이션)가 idle로 고정됨");
  pendingPlugins.push(MASCOT);
} else {
  ok(`마스코트 플러그인 설치됨: ${mascotRoot}`);
  // 상태 기록 훅(hook-state-writer.js)이 yaml 등 외부 패키지를 쓰는데 마켓플레이스 사본에는 node_modules가 없음
  if (existsSync(path.join(mascotRoot, "node_modules"))) {
    ok("마스코트 의존 패키지 설치됨");
  } else {
    try {
      const npm = process.platform === "win32" ? "npm.cmd" : "npm";
      execFileSync(npm, ["install", "--omit=dev"], { cwd: mascotRoot, stdio: "inherit", shell: process.platform === "win32" });
      ok("마스코트 의존 패키지 설치 완료");
    } catch {
      warn(`마스코트 의존 패키지 설치 실패: ${mascotRoot}에서 npm install --omit=dev 직접 실행 필요`);
    }
  }
  if (existsSync(path.join(mascotRoot, "hooks", "hooks.json"))) {
    ok("마스코트 상태 기록 훅은 플러그인 hooks.json으로 자동 등록");
  } else {
    warn("플러그인에 hooks/hooks.json이 없음: Claude Code에서 /claude-code-mascot-statusline:setup 실행 후 이 설치기를 다시 실행");
  }
}

if (existsSync(HUD.entry)) {
  ok("claude-hud 플러그인 설치됨");
} else {
  warn("claude-hud 미설치: HUD 없이 마스코트만 표시");
  pendingPlugins.push(HUD);
}

console.log("\n\x1b[1m마무리\x1b[0m");
if (pendingPlugins.length) {
  console.log("  • Claude Code를 실행하면 등록한 플러그인 설치를 안내함. 안내가 없으면 Claude Code 안에서 아래를 실행:");
  for (const plugin of pendingPlugins) {
    console.log(`      /plugin marketplace add https://github.com/${plugin.repo}.git`);
    console.log(`      /plugin install ${plugin.pluginId}`);
  }
  console.log("  • 플러그인 설치 후 이 설치기를 한 번 더 실행해 의존 패키지 설치까지 마무리");
}
console.log("  • Claude Code를 재시작하거나 새 세션을 열면 상태 표시줄 적용");
console.log("  • 미리 보기: echo '{}' | CLAWD_NO_HUD=1 CLAWD_FORCE_STATE=done node ~/.claude/statusline/clawd-quadrant.mjs\n");
