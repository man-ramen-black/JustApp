#!/usr/bin/env node
// 로컬 모드(local-mods 폴더 마켓플레이스) 사용자 레벨 설치기 (macOS·Windows 공통)
//
// 처리 항목
//   1) 마켓플레이스 매니페스트와 모드 폴더를 ~/.claude/local-mods/로 복사 (내용이 다른 기존 폴더는 백업)
//   2) local-mods 마켓플레이스 등록
//   3) 모드 설치, 버전이 바뀌었으면 갱신
//   4) 같은 상자를 그리는 원본 플러그인 비활성
//
// 실행: node .claude/local-mods/install.mjs (여러 번 실행해도 같은 결과)
// 마켓플레이스 등록·설치는 claude CLI(claude plugin …)로 하므로 claude 명령이 PATH에 있어야 함
// CLAUDE_CONFIG_DIR이 있으면 claude CLI와 같은 설정 폴더를 대상으로 삼음

import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const MARKETPLACE_NAME = "local-mods";
const MANIFEST_RELATIVE = path.join(".claude-plugin", "marketplace.json");
const CLAUDE_DIR = process.env.CLAUDE_CONFIG_DIR || path.join(homedir(), ".claude");
const LOCAL_MODS_DIR = path.join(CLAUDE_DIR, "local-mods");
const BACKUP_DIR = path.join(CLAUDE_DIR, "local-mods.bak");
const SETTINGS = path.join(CLAUDE_DIR, "settings.json");
const KNOWN_MARKETPLACES = path.join(CLAUDE_DIR, "plugins", "known_marketplaces.json");
const INSTALLED_PLUGINS = path.join(CLAUDE_DIR, "plugins", "installed_plugins.json");

// Claude Code가 모드를 불러올 때 만드는 파일(타입 선언·의존 패키지)은 비교·복사 대상에서 제외
const GENERATED = new Set([
  path.join(".claude-plugin", "types"),
  path.join(".claude", "types"),
  "node_modules",
  ".DS_Store",
]);

// 모드가 대체하는 원본 플러그인(둘 다 켜지면 상자가 두 개 그려짐)
const REPLACES = {
  "whats-agent-doing-ko": "whats-agent-doing@tzafrir",
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

const isGenerated = (relativePath) => GENERATED.has(relativePath) || GENERATED.has(path.basename(relativePath));

// 생성 파일을 뺀 상대 경로 목록
function listFiles(root, relative = "") {
  const files = [];
  for (const entry of readdirSync(path.join(root, relative), { withFileTypes: true })) {
    const entryRelative = path.join(relative, entry.name);
    if (isGenerated(entryRelative)) {
      continue;
    }
    if (entry.isDirectory()) {
      files.push(...listFiles(root, entryRelative));
    } else {
      files.push(entryRelative);
    }
  }
  return files.sort();
}

function sameTree(left, right) {
  if (!existsSync(left) || !existsSync(right)) {
    return false;
  }
  const leftFiles = listFiles(left);
  const rightFiles = listFiles(right);
  return (
    leftFiles.length === rightFiles.length &&
    leftFiles.every(
      (file, index) =>
        file === rightFiles[index] && readFileSync(path.join(left, file)).equals(readFileSync(path.join(right, file))),
    )
  );
}

// claude CLI 실행 (Windows는 claude.exe·claude.cmd를 모두 찾도록 셸 경유, 인자 공백 대비 따옴표)
function claude(args) {
  const windows = process.platform === "win32";
  const commandArgs = windows ? args.map((argument) => (/\s/.test(argument) ? `"${argument}"` : argument)) : args;
  return execFileSync("claude", commandArgs, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], shell: windows });
}

function runClaude(args, successMessage) {
  try {
    claude(args);
    ok(successMessage);
    return true;
  } catch (error) {
    const detail = `${error.stderr ?? ""}${error.stdout ?? ""}`.trim() || error.message;
    warn(`claude ${args.join(" ")} 실패: ${detail}`);
    return false;
  }
}

const sourceManifest = readJson(path.join(HERE, MANIFEST_RELATIVE), null);
if (!sourceManifest) {
  fail(`마켓플레이스 매니페스트 없음: ${path.join(HERE, MANIFEST_RELATIVE)}`);
}

console.log("\n\x1b[1m로컬 모드 설치\x1b[0m\n");

// 1. 모드 폴더와 매니페스트 복사 (다른 PC에서만 쓰던 모드 항목은 매니페스트에 남김)
const plugins = sourceManifest.plugins.map((plugin) => ({
  ...plugin,
  sourceDir: path.join(HERE, plugin.source),
  targetDir: path.join(LOCAL_MODS_DIR, plugin.source),
}));
for (const plugin of plugins) {
  if (sameTree(plugin.sourceDir, plugin.targetDir)) {
    ok(`모드 폴더 최신: ${plugin.targetDir}`);
    continue;
  }
  if (existsSync(plugin.targetDir)) {
    const backup = path.join(BACKUP_DIR, plugin.name);
    rmSync(backup, { recursive: true, force: true });
    mkdirSync(BACKUP_DIR, { recursive: true });
    renameSync(plugin.targetDir, backup);
    ok(`내용이 다른 기존 모드 폴더 백업: ${backup}`);
  }
  cpSync(plugin.sourceDir, plugin.targetDir, {
    recursive: true,
    filter: (source) => !isGenerated(path.relative(plugin.sourceDir, source)),
  });
  ok(`모드 폴더 복사: ${plugin.targetDir}`);
}

const targetManifestPath = path.join(LOCAL_MODS_DIR, MANIFEST_RELATIVE);
const targetManifest = readJson(targetManifestPath, { plugins: [] });
const sourceNames = new Set(sourceManifest.plugins.map((plugin) => plugin.name));
writeJson(targetManifestPath, {
  ...sourceManifest,
  plugins: [...sourceManifest.plugins, ...(targetManifest.plugins ?? []).filter((plugin) => !sourceNames.has(plugin.name))],
});
ok(`마켓플레이스 매니페스트 반영: ${targetManifestPath}`);

// 2. 마켓플레이스 등록 (폴더 마켓플레이스는 매니페스트를 바로 읽으므로 등록 후 갱신 불필요)
console.log("\n\x1b[1m마켓플레이스·모드 설치\x1b[0m");
const pending = [];
const known = readJson(KNOWN_MARKETPLACES, {})[MARKETPLACE_NAME];
if (!known) {
  if (!runClaude(["plugin", "marketplace", "add", LOCAL_MODS_DIR, "--scope", "user"], `${MARKETPLACE_NAME} 마켓플레이스 등록`)) {
    pending.push(`/plugin marketplace add ${LOCAL_MODS_DIR}`);
  }
} else if (path.resolve(known.installLocation ?? "") !== path.resolve(LOCAL_MODS_DIR)) {
  warn(`${MARKETPLACE_NAME} 마켓플레이스가 다른 폴더를 가리킴: ${known.installLocation}`);
  pending.push(`/plugin marketplace remove ${MARKETPLACE_NAME}`, `/plugin marketplace add ${LOCAL_MODS_DIR}`);
} else {
  ok(`${MARKETPLACE_NAME} 마켓플레이스 등록됨`);
}

// 3. 설치 또는 갱신 (설치 기록의 버전과 plugin.json 버전 비교)
const installed = readJson(INSTALLED_PLUGINS, {}).plugins ?? {};
for (const plugin of plugins) {
  const pluginId = `${plugin.name}@${MARKETPLACE_NAME}`;
  const version = readJson(path.join(plugin.targetDir, ".claude-plugin", "plugin.json"), {}).version;
  const installedVersion = installed[pluginId]?.find((entry) => entry.scope === "user")?.version;
  if (!installedVersion) {
    if (!runClaude(["plugin", "install", pluginId, "--scope", "user"], `${pluginId} ${version} 설치`)) {
      pending.push(`/plugin install ${pluginId}`);
    }
  } else if (installedVersion !== version) {
    if (!runClaude(["plugin", "update", pluginId, "--scope", "user"], `${pluginId} ${installedVersion} → ${version} 갱신`)) {
      pending.push(`/plugin update ${pluginId}`);
    }
  } else {
    ok(`${pluginId} ${version} 설치됨`);
  }
}

// 4. 원본 플러그인 비활성
const enabledPlugins = readJson(SETTINGS, {}).enabledPlugins ?? {};
for (const plugin of plugins) {
  const original = REPLACES[plugin.name];
  if (original && enabledPlugins[original] === true) {
    if (!runClaude(["plugin", "disable", original, "--scope", "user"], `원본 플러그인 비활성: ${original}`)) {
      pending.push(`/plugin disable ${original}`);
    }
  }
}

console.log("\n\x1b[1m마무리\x1b[0m");
if (pending.length) {
  console.log("  • 실패한 단계는 Claude Code 안에서 아래를 실행한 뒤 이 설치기를 한 번 더 실행:");
  for (const command of pending) {
    console.log(`      ${command}`);
  }
}
console.log("  • Claude Code를 재시작하거나 실행 중인 세션에서 /reload-plugins를 실행하면 적용\n");
