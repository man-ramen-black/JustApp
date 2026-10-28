#!/usr/bin/env node
// clawd-quadrant.mjs — Clawd 마스코트를 quadrant(2×2) 블록 문자로 직접 렌더하고 claude-hud와 가로 병합한다.
//
// 배경: 마스코트 플러그인은 half-block(▀▄█)만 지원하므로 quadrant 렌더 불가 → 플러그인을 우회해 여기서 직접 렌더한다.
// 그래서 플러그인이 하던 상태 감지·애니 프레임 선택·heat 색 보간 로직을 이 파일에 이식했다(상수/로직 출처는 각 섹션 주석 참고).
//
// 디버그 env:
//   CLAWD_FORCE_STATE=thinking  상태를 강제 지정(state 파일 무시)
//   CLAWD_NOW=<ms>              애니 프레임 선택용 현재시각(ms) 주입
//   CLAWD_FORCE_PCT=70          heat 보간용 context 사용률(%) 강제
//   CLAWD_NO_HUD=1             HUD 병합 생략(마스코트만 출력)
import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync, statSync, openSync, readSync, closeSync, fstatSync } from "node:fs";
import { homedir } from "node:os";
import path from "node:path";
import crypto from "node:crypto";

const HOME = homedir();
const SAFE_BG = "#14191e"; // config.json safeBackground — quadrant 셀 배경으로 직접 칠한다.
const STATE_DIR = path.join(HOME, ".claude/plugins/claude-code-mascot-statusline/state");
const HUD = path.join(HOME, ".claude/plugins/marketplaces/claude-hud/dist/index.js");
// 로그인 계정 출처. statusline 입력 JSON에는 계정 정보가 없다(실측 덤프: session_id/model/workspace/
// version/output_style/cost/context_window/effort/fast_mode/thinking/rate_limits뿐)이라 이 파일에서 직접 읽는다.
const CLAUDE_CONFIG = path.join(HOME, ".claude.json");

// ── 팔레트 (pack.yaml clawd 기준) ──
// 인덱스 2(몸 오렌지)는 heat target — context% 높을 때 빨강으로 보간된다.
const PALETTE = [
  SAFE_BG,   // 0 투명(배경으로 채워짐)
  "#1a1a1a", // 1 검정 눈
  "#d77757", // 2 몸 오렌지 (HEAT TARGET)
  "#f3c969", // 3 골드 (반짝/물음)
  "#7db8ff", // 4 파랑 (땀/서브에이전트)
  "#ff5555", // 5 빨강 (permission 경고)
  SAFE_BG,   // 6 배경 채움
  "#f4fbff", // 7 흰 (생각 점)
];

// ── 타이밍 (pack.yaml clawd timing) ──
const FRAME_PERIOD = {
  idle: 1500, // 대기 두리번: 한 방향당 응시를 조금 더 길게(천천히)
  thinking: 320,
  tool_running: 240,
  subagent_running: 240,
};
const DEFAULT_FRAME_PERIOD = 1000;

// ── hold 만료 (pack.yaml timing). 이 상태들만 hold하고, 나머지는 0(즉시 표시) ──
const HOLD_MS = {
  done: 10000, // 완료 후 골드 반짝(다리 춤) 10초 → 이후 idle 두리번(대기)
  tool_success: 1000,
  tool_failure: 1500,
  auth_success: 2000,
};
// thinking이 이 시간 이상 갱신이 없으면 '작업 종료 후 대기'로 보고 idle 처리한다.
// (SubagentStop 훅이 turn 종료 시 done을 덮어써 thinking이 남는 race를 방지)
// 45000: transcript.js의 THINKING_STALE_MS(45s)와 정렬 — transcript에서 도출한 thinking은
//   긴 추론 블록(다음 transcript 엔트리까지 10초 이상 공백) 동안에도 idle로 조기 강등되지 않아야 한다.
const THINKING_STALE_MS = 45000;

// ── transcript 파싱 (transcript.js 이식) ──
// 훅 모델은 '모델이 추론 중'을 알리는 이벤트가 없어 thinking이 거의 안 잡힌다.
// 그래서 원본 renderer.ts처럼 transcript의 assistant thinking 블록·툴 흐름을 직접 읽어 상태를 도출하고,
// 훅 state와 최신값(lastUpdatedAt) 기준으로 병합한다.
const TRANSCRIPT_TAIL_BYTES = 256 * 1024;
// 툴 결과 직후 성공/실패 '반짝'을 유지하는 시간. 이후 조용한 구간은 '모델이 다음 동작 추론 중 = thinking'으로 본다.
const RESULT_FLASH_MS = 1500;
// 완료(done) 애니 유지 창: turn 종료 직후 idle_prompt 알림이 Stop→done을 idle로 덮어도, 이 시간 안이면 done을 유지한다.
const DONE_VISIBLE_MS = 10000;

// ── heat (renderer.ts applyContextHeatPalette 이식) ──
const HEAT_THRESHOLD = 60; // 이하면 보간 안 함
const HEAT_MAX = 85; // 이상은 최대값으로 saturate
const HEAT_TARGET = [0xff, 0x44, 0x44]; // #ff4444
const HEAT_PALETTE_INDEX = 2; // 몸 오렌지

// quadrant 매핑: 인덱스 = TL*8 + TR*4 + BL*2 + BR (채워짐=1)
const QUAD = [" ", "▗", "▖", "▄", "▝", "▐", "▞", "▟", "▘", "▚", "▌", "▙", "▀", "▜", "▛", "█"];

// ── ANSI 색 ──
const hexToRgb = (hex) => [
  Number.parseInt(hex.slice(1, 3), 16),
  Number.parseInt(hex.slice(3, 5), 16),
  Number.parseInt(hex.slice(5, 7), 16),
];
const fg = (hex) => {
  const [r, g, b] = hexToRgb(hex);
  return `\x1b[38;2;${r};${g};${b}m`;
};
const bg = (hex) => {
  const [r, g, b] = hexToRgb(hex);
  return `\x1b[48;2;${r};${g};${b}m`;
};
const RESET = "\x1b[0m";

// ──────────────────────────────────────────────────────────────────────────
// 1. quadrant 렌더
// 2×2 픽셀 1셀 = 문자 1개. 한 셀은 fg+bg 2색만 가지므로 셀 단위로 2색 양자화한다.
//   - SAFE_BG가 셀에 있으면 그걸 bg로, 나머지 최빈 색을 fg로 둔다(몸이 배경 위).
//   - SAFE_BG가 없으면(눈처럼 몸 위 악센트) 최빈 색을 bg, 최소빈 색을 fg로 둔다.
//   - bg가 아닌 픽셀은 모두 채움(1) 비트로 본다(3색 이상이면 근사 — 아트로 회피).
// ──────────────────────────────────────────────────────────────────────────
function renderCell(colors) {
  const distinct = [...new Set(colors)];
  if (distinct.length === 1) {
    const c = distinct[0];
    if (c === SAFE_BG) return `${bg(SAFE_BG)} ${RESET}`;
    return `${fg(c)}${bg(SAFE_BG)}█${RESET}`;
  }
  const freq = {};
  for (const c of colors) freq[c] = (freq[c] ?? 0) + 1;
  let bgColor;
  let fgColor;
  if (distinct.includes(SAFE_BG)) {
    bgColor = SAFE_BG;
    const nonBg = colors.filter((c) => c !== SAFE_BG);
    fgColor = nonBg.sort((a, b) => freq[b] - freq[a])[0];
  } else {
    const sorted = [...distinct].sort((a, b) => freq[b] - freq[a]);
    bgColor = sorted[0]; // 최빈(몸)
    fgColor = sorted[sorted.length - 1]; // 최소빈(악센트=눈)
  }
  let bits = 0;
  for (const c of colors) bits = (bits << 1) | (c === bgColor ? 0 : 1); // TL,TR,BL,BR 순
  return `${fg(fgColor)}${bg(bgColor)}${QUAD[bits]}${RESET}`;
}

function renderQuadrant(grid, palette) {
  const height = grid.length;
  const lines = [];
  for (let y = 0; y < height; y += 2) {
    const width = grid[y].length;
    let line = "";
    for (let x = 0; x < width; x += 2) {
      const colors = [
        palette[grid[y]?.[x] ?? 0] ?? SAFE_BG,
        palette[grid[y]?.[x + 1] ?? 0] ?? SAFE_BG,
        palette[grid[y + 1]?.[x] ?? 0] ?? SAFE_BG,
        palette[grid[y + 1]?.[x + 1] ?? 0] ?? SAFE_BG,
      ];
      line += renderCell(colors);
    }
    lines.push(line);
  }
  return lines;
}

// ──────────────────────────────────────────────────────────────────────────
// 2. 스프라이트 — 공식 Clawd quadrant 아트를 픽셀로 역산. 18×6 픽셀(quadrant 9×3 문자).
//   목표 아트(idle):  ▐▛███▜▌ / ▝▜█████▛▘ / ▘▘ ▝▝
//   각 quadrant 문자(TL*8+TR*4+BL*2+BR)를 비트로 디코딩해 아래 그리드를 복원했다.
//   다리(4행)와 머리 옆 효과 마커는 상태별로 합성한다.
// ──────────────────────────────────────────────────────────────────────────
const O = 2; // 몸 오렌지
const _ = 0; // 투명
// 몸통(rows 0~3) — 목표 아트의 ▐▛███▜▌ / ▝▜█████▛▘ 부분을 픽셀로 복원. 다리는 setLegs로 추가.
// 눈 = ▛(우하단 BR=px(1,5))·▜(좌하단 BL=px(1,12))의 비어있는 노치. 투명이라 어두운 음각으로 렌더된다.
const BASE_BODY = [
  [_, _, _, O, O, O, O, O, O, O, O, O, O, O, O, _, _, _], // 0  ' ▐▛███▜▌'
  [_, _, _, O, O, _, O, O, O, O, O, O, _, O, O, _, _, _], // 1  (col5·col12 = 눈)
  [_, O, O, O, O, O, O, O, O, O, O, O, O, O, O, O, O, _], // 2  '▝▜█████▛▘'
  [_, _, _, O, O, O, O, O, O, O, O, O, O, O, O, _, _, _], // 3
  [_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], // 4 (다리)
  [_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], // 5
];

const clone = (grid) => grid.map((row) => [...row]);
const setPixel = (grid, y, x, value) => {
  if (grid[y] && x >= 0 && x < grid[y].length) grid[y][x] = value;
};
// 오른쪽 마커 공간 확보용: 모든 행을 width까지 투명으로 채운다.
const padTo = (grid, width) =>
  grid.map((row) => (row.length >= width ? row : [...row, ...new Array(width - row.length).fill(0)]));

// 다리: 4행에 발 4개. phase "a"=목표(▘▘ ▝▝), "b"=좌우 반전(걷기 애니용).
function setLegs(grid, phase) {
  const cols = phase === "b" ? [5, 7, 10, 12] : [4, 6, 11, 13];
  for (const c of cols) setPixel(grid, 4, c, O);
}

// 두리번: 양쪽 눈이 함께 시선 방향으로 1px씩 이동(눈=row1의 어두운 노치 1개씩).
//  중앙=▐▛███▜▌(목표) / 오른쪽=▐█▜██▛▌ / 왼쪽=▐▜██▛█▌.
//  왼눈 노치 col4(좌)·5(중)·6(우), 오른눈 col11(좌)·12(중)·13(우).
function setEyes(grid, dir) {
  for (const c of [4, 5, 6, 11, 12, 13]) setPixel(grid, 1, c, O); // 눈 영역 채우고
  const gaps = { left: [4, 11], center: [5, 12], right: [6, 13] };
  const [leftEye, rightEye] = gaps[dir] ?? gaps.center;
  setPixel(grid, 1, leftEye, _);
  setPixel(grid, 1, rightEye, _);
}

// 팔 3단계(모든 상태 공통):
//  "center"=기본 ▝▜█████▛▘ (손이 옆) / "up"=올림 ▗▐▛███▜▌▖·▜█████▛ (손 윗줄, 아래 비움) / "down"=내림 ▗▜█████▛▖ (손 아랫줄).
function setArms(grid, pose) {
  if (pose === "up") {
    setPixel(grid, 1, 1, O);   // 윗줄 ▗ (왼팔 올림)
    setPixel(grid, 1, 16, O);  // 윗줄 ▖ (오른팔 올림)
    setPixel(grid, 2, 1, _);   // 옆 손 ▝ 치움
    setPixel(grid, 2, 16, _);  // 옆 손 ▘ 치움
  } else if (pose === "down") {
    setPixel(grid, 2, 1, _);   // 옆 손 ▝ 치움
    setPixel(grid, 2, 16, _);  // 옆 손 ▘ 치움
    setPixel(grid, 3, 1, O);   // 아랫줄 ▗ (왼팔 내림)
    setPixel(grid, 3, 16, O);  // 아랫줄 ▖ (오른팔 내림)
  }
  // center = BASE_BODY (손 ▝…▘)
}

// 상단 모서리 골드 반짝(성공/완료). 18px 내 ▝ … ▘ 로 렌더.
function setTopSparkle(grid, colorIndex) {
  setPixel(grid, 0, 1, colorIndex);
  setPixel(grid, 0, 16, colorIndex);
}

// 단일 프레임 합성기. 본체(px0~17)를 그린 뒤 좌우 여백을 1칸씩 덧대고 우측 점 마커를 그린다.
function makeFrame({ legs = "a", eyes = "center", arms = "center", sparkle = null, sideMark = null } = {}) {
  let grid = clone(BASE_BODY);
  setLegs(grid, legs);
  setEyes(grid, eyes);
  setArms(grid, arms);
  if (sparkle !== null) setTopSparkle(grid, sparkle);
  grid = grid.map((row) => [_, _, ...row, _, _]); // 좌우 여백 각 1칸
  if (sideMark) for (const r of sideMark.rows) setPixel(grid, r, 20, sideMark.color); // 우측 여백(px20)에 점
  return grid;
}

// 상태별 프레임 배열.
function buildFrames(state) {
  switch (state) {
    case "idle":
      // 대기: 눈만 두리번(좌→정→우→정). 몸·팔·다리는 목표 정지 자세 유지.
      return [
        makeFrame({ eyes: "left" }),
        makeFrame({ eyes: "center" }),
        makeFrame({ eyes: "right" }),
        makeFrame({ eyes: "center" }),
      ];
    case "tool_running":
      // 걷기: 발 좌우 + 팔 올림↔내림 스윙
      return [makeFrame({ legs: "a", arms: "up" }), makeFrame({ legs: "b", arms: "down" })];
    case "subagent_running":
      // 걷기 + 팔 스윙 + 우측 파랑 점(서브에이전트 구분)
      return [
        makeFrame({ legs: "a", arms: "up", sideMark: { rows: [1, 3], color: 4 } }),
        makeFrame({ legs: "b", arms: "down", sideMark: { rows: [1, 3], color: 4 } }),
      ];
    case "thinking":
      // 생각 중: 팔 까딱(중앙↔올림)
      return [makeFrame({ arms: "center" }), makeFrame({ arms: "up" })];
    case "tool_success":
    case "auth_success":
      // 성공: 골드 반짝
      return [makeFrame({ sparkle: 3 })];
    case "done":
      // 완료 축하: 다리 좌우로 움직이며 골드 반짝 점멸 (오래 유지)
      return [makeFrame({ sparkle: 3, legs: "a" }), makeFrame({ legs: "b" })];
    case "tool_failure":
      // 실패: 우측 파랑 땀방울 + 팔 내림(시무룩)
      return [makeFrame({ sideMark: { rows: [1, 3], color: 4 }, arms: "down" })];
    case "question":
      // 질문: 팔 흔들기(올림→중앙→내림→중앙) + 우측 골드 점 (이전 ? 심볼 대체)
      return [
        makeFrame({ arms: "up", sideMark: { rows: [1, 3], color: 3 } }),
        makeFrame({ arms: "center", sideMark: { rows: [1, 3], color: 3 } }),
        makeFrame({ arms: "down", sideMark: { rows: [1, 3], color: 3 } }),
        makeFrame({ arms: "center", sideMark: { rows: [1, 3], color: 3 } }),
      ];
    case "permission":
      // 권한 요청: 팔 올린 채 우측 빨강 점 점멸(주목) (이전 ! 심볼 대체)
      return [
        makeFrame({ arms: "up", sideMark: { rows: [1, 3, 5], color: 5 } }),
        makeFrame({ arms: "up" }),
      ];
    default:
      return [makeFrame()];
  }
}

// ──────────────────────────────────────────────────────────────────────────
// 3. 상태 감지 (state.ts: SHA1(sessionId).json, fallback 최신 mtime)
// ──────────────────────────────────────────────────────────────────────────
function sha1(value) {
  return crypto.createHash("sha1").update(value).digest("hex");
}

function readSessionState(sessionId) {
  if (sessionId) {
    try {
      const direct = JSON.parse(readFileSync(path.join(STATE_DIR, `${sha1(sessionId)}.json`), "utf8"));
      if (direct && direct.version === 1) return direct;
    } catch {
      // 직접 매칭 실패 → fallback
    }
  }
  try {
    const files = readdirSync(STATE_DIR).filter((f) => f.endsWith(".json"));
    let newest = null;
    let newestTime = 0;
    for (const file of files) {
      const mtime = statSync(path.join(STATE_DIR, file)).mtimeMs;
      if (mtime > newestTime) {
        newestTime = mtime;
        newest = file;
      }
    }
    if (newest) {
      const state = JSON.parse(readFileSync(path.join(STATE_DIR, newest), "utf8"));
      if (state && state.version === 1) return state;
    }
  } catch {
    // state 디렉토리 없음 등 → idle 처리
  }
  return null;
}

// ──────────────────────────────────────────────────────────────────────────
// 3.5 transcript 상태 도출 (transcript.js deriveSessionStateFromTranscript 이식, sync 변환)
//   훅 state만으로는 thinking이 거의 안 잡히므로 transcript를 직접 읽어 보강한다.
//   transcript는 권한/질문 상태를 담지 않으므로 훅 state와 병합해 함께 쓴다.
// ──────────────────────────────────────────────────────────────────────────
function readTranscriptTailLines(transcriptPath) {
  try {
    const fd = openSync(transcriptPath, "r");
    try {
      const size = fstatSync(fd).size;
      const readSize = Math.min(TRANSCRIPT_TAIL_BYTES, size);
      const buffer = Buffer.alloc(readSize);
      readSync(fd, buffer, 0, readSize, Math.max(0, size - readSize));
      const raw = buffer.toString("utf8");
      // 앞쪽이 잘린 라인은 버린다(파일 크기가 tail보다 크면 첫 개행 전까지 제거).
      const normalized = size > readSize ? raw.slice(raw.indexOf("\n") + 1) : raw;
      return normalized.split("\n").filter(Boolean);
    } finally {
      closeSync(fd);
    }
  } catch {
    return [];
  }
}

const getContentItems = (content) => {
  if (Array.isArray(content)) return content.filter((item) => typeof item === "object" && item !== null);
  if (typeof content === "string") return [{ type: "text", text: content }];
  if (content && typeof content === "object") return [content];
  return [];
};
const firstTextContent = (content) => {
  for (const item of getContentItems(content)) {
    if (typeof item.text === "string") return item.text;
    if (typeof item.content === "string") return item.content;
  }
  return undefined;
};
const isSystemReminder = (content) => typeof content === "string" && content.startsWith("<system-reminder>");
const isToolResultEnvelope = (entry) => {
  const items = getContentItems(entry.message?.content);
  return items.length > 0 && items.every((item) => item.type === "tool_result");
};
const isSubagentTool = (toolName, input) =>
  toolName === "Task" || toolName === "call_omo_agent" || typeof input?.subagent_type === "string";
const findLastPendingIndex = (pendingTools, toolName) => {
  for (let index = pendingTools.length - 1; index >= 0; index -= 1) {
    if (pendingTools[index]?.name === toolName) return index;
  }
  return -1;
};
const detectToolResultError = (item) => {
  if (typeof item.content === "string") {
    const content = item.content.toLowerCase();
    if (content.includes("failed") || content.includes("error")) return true;
  }
  return false;
};
const normalizeTimestamp = (timestamp, nowMs) => {
  if (!timestamp) return new Date(nowMs).toISOString();
  const parsed = Date.parse(timestamp);
  return Number.isNaN(parsed) ? new Date(nowMs).toISOString() : new Date(parsed).toISOString();
};

function findLastPromptIndex(entries) {
  for (let index = entries.length - 1; index >= 0; index -= 1) {
    const entry = entries[index];
    if (
      entry?.type === "user" &&
      !isToolResultEnvelope(entry) &&
      !isSystemReminder(entry.content ?? firstTextContent(entry.message?.content))
    ) {
      return index;
    }
  }
  return -1;
}

function deriveStateFromTranscript(transcriptPath, nowMs) {
  if (!transcriptPath) return null;
  const lines = readTranscriptTailLines(transcriptPath);
  if (lines.length === 0) return null;
  const entries = lines
    .map((line) => {
      try {
        return JSON.parse(line);
      } catch {
        return null;
      }
    })
    .filter(Boolean);
  if (entries.length === 0) return null;

  const lastPromptIndex = findLastPromptIndex(entries);
  const relevantEntries = lastPromptIndex >= 0 ? entries.slice(lastPromptIndex) : entries;
  const nowIso = new Date(nowMs).toISOString();
  let currentState = "idle";
  let lastStateChangedAt = nowIso;
  let lastUpdatedAt = nowIso;
  let activeSubagentCount = 0;
  let lastToolName = null;
  let pendingTools = [];
  let sawPrompt = false;
  let lastMeaningfulType = null;
  const turnSequenceNumber = lastPromptIndex >= 0 ? 1 : 0;

  for (const entry of relevantEntries) {
    const timestamp = normalizeTimestamp(entry.timestamp, nowMs);
    // timestamp 없는 메타 엔트리(file-history-snapshot/mode/ai-title/last-prompt/permission-mode 등)는
    // normalizeTimestamp가 nowMs를 돌려줘 lastUpdatedAt을 '현재시각'으로 오염시킨다.
    // 그러면 selectMostRecentState에서 transcript state가 항상 최신으로 판정돼 훅 state(thinking 등)를 부당하게 이긴다
    // (turn 완료 직후 새 prompt 시 thinking 대신 done→idle이 노출되는 버그). 실제 timestamp가 있는 엔트리만 반영한다.
    if (entry.timestamp) {
      lastUpdatedAt = timestamp;
    }
    if (entry.type === "progress") {
      if (entry.data?.hookEvent === "Stop") {
        currentState = "done";
        lastStateChangedAt = timestamp;
        lastMeaningfulType = "assistant_text";
      }
      continue;
    }
    if (entry.type === "user") {
      const contentItems = getContentItems(entry.message?.content);
      const toolResults = contentItems.filter((item) => item.type === "tool_result");
      if (toolResults.length > 0) {
        for (const result of toolResults) {
          const toolName = lastToolName ?? "tool";
          const pendingIndex = findLastPendingIndex(pendingTools, toolName);
          const pending = pendingIndex >= 0 ? pendingTools.splice(pendingIndex, 1)[0] : null;
          if (pending?.isSubagent) activeSubagentCount = Math.max(0, activeSubagentCount - 1);
          const isError = detectToolResultError(result);
          if (activeSubagentCount > 0) {
            currentState = "subagent_running";
          } else {
            currentState = isError ? "tool_failure" : "tool_success";
          }
          lastStateChangedAt = timestamp;
          lastMeaningfulType = "tool_result";
        }
        continue;
      }
      if (!isSystemReminder(entry.content ?? firstTextContent(contentItems))) {
        sawPrompt = true;
        currentState = "thinking";
        lastStateChangedAt = timestamp;
        activeSubagentCount = 0;
        lastToolName = null;
        pendingTools = [];
        lastMeaningfulType = "user";
      }
      continue;
    }
    if (entry.type === "assistant") {
      for (const item of getContentItems(entry.message?.content)) {
        if (item.type === "tool_use") {
          const toolName = item.name ?? "tool";
          const isSubagent = isSubagentTool(toolName, item.input);
          lastToolName = toolName;
          pendingTools.push({ name: toolName, isSubagent });
          if (isSubagent) {
            activeSubagentCount += 1;
            currentState = "subagent_running";
          } else {
            currentState = "tool_running";
          }
          lastStateChangedAt = timestamp;
          lastMeaningfulType = "tool_use";
          continue;
        }
        if (item.type === "thinking") {
          currentState = "thinking";
          lastStateChangedAt = timestamp;
          lastMeaningfulType = "assistant_thinking";
          continue;
        }
        // [NM patch] Claude Code는 [text]를 [thinking]·[tool_use]와 별개 엔트리로 쓰고, 툴 호출 직전에도 텍스트가 나온다.
        // 텍스트=완료(done)로 보면 툴 호출 사이마다 done이 깜빡이고 thinking이 묻힌다(증상: done 깜빡임 / thinking 미노출).
        // 텍스트 구간은 '모델이 응답 작성 중 = thinking'으로 본다. 진짜 완료(done)는 Stop 훅(persisted) 경로에서만 만든다.
        if (item.type === "text" && sawPrompt && pendingTools.length === 0 && activeSubagentCount === 0) {
          currentState = "thinking";
          lastStateChangedAt = timestamp;
          lastMeaningfulType = "assistant_text";
        }
      }
      continue;
    }
  }

  // tail 보정: 미완료 툴/서브에이전트가 남았거나, 마지막 의미 엔트리 종류·경과시간으로 최종 상태를 정한다.
  if (pendingTools.some((tool) => !tool.isSubagent)) {
    currentState = "tool_running";
  } else if (activeSubagentCount > 0) {
    currentState = "subagent_running";
  } else if (sawPrompt) {
    const ageMs = nowMs - Date.parse(lastUpdatedAt);
    if (lastMeaningfulType === "user" || lastMeaningfulType === "assistant_thinking") {
      currentState = ageMs < THINKING_STALE_MS ? "thinking" : "idle";
    } else if (lastMeaningfulType === "tool_result") {
      // 툴 결과 직후 짧게 성공/실패 반짝(currentState 유지) → 이후 모델이 다음 동작을 추론하는 구간은 thinking → 오래 조용하면 idle.
      if (ageMs >= RESULT_FLASH_MS) {
        currentState = ageMs < THINKING_STALE_MS ? "thinking" : "idle";
      }
    } else if (lastMeaningfulType === "assistant_text") {
      // 텍스트 구간은 작업 중(thinking). done은 Stop 훅 경로(persisted)에서만.
      currentState = ageMs < THINKING_STALE_MS ? "thinking" : "idle";
    }
  }

  return {
    version: 1,
    currentState,
    lastStateChangedAt,
    lastUpdatedAt,
    activeSubagentCount,
    turnSequenceNumber,
  };
}

// 훅 state(권한/질문 등 transcript 미수록 상태 보유)와 transcript state를 lastUpdatedAt 최신값으로 병합.
function selectMostRecentState(persistedState, transcriptState) {
  if (!persistedState) return transcriptState;
  if (!transcriptState) return persistedState;
  return Date.parse(transcriptState.lastUpdatedAt) >= Date.parse(persistedState.lastUpdatedAt)
    ? transcriptState
    : persistedState;
}

// renderer.ts resolveEffectiveState 이식: hold 만료 시 강등 + 대기(stale thinking) 감지.
function resolveEffectiveState(state, nowMs) {
  if (!state) return "idle";
  // 대기 감지: thinking이 일정 시간 갱신 없으면 작업이 끝나고 대기 중 → idle 두리번.
  const sinceUpdate = nowMs - Date.parse(state.lastUpdatedAt);
  if (state.currentState === "thinking" && sinceUpdate > THINKING_STALE_MS) return "idle";
  const hold = HOLD_MS[state.currentState] ?? 0;
  if (!hold) return state.currentState;
  const elapsed = nowMs - Date.parse(state.lastStateChangedAt);
  if (elapsed < hold) return state.currentState;
  if (state.activeSubagentCount > 0) return "subagent_running";
  if (["done", "tool_success", "tool_failure", "auth_success"].includes(state.currentState)) return "idle";
  if (["question", "permission"].includes(state.currentState)) return "thinking";
  return "idle";
}

// ──────────────────────────────────────────────────────────────────────────
// 4. heat (renderer.ts applyContextHeatPalette 이식)
// ──────────────────────────────────────────────────────────────────────────
function applyHeat(palette, usedPercentage) {
  if (usedPercentage === undefined || usedPercentage <= HEAT_THRESHOLD) return palette;
  const t = Math.min(1, (usedPercentage - HEAT_THRESHOLD) / (HEAT_MAX - HEAT_THRESHOLD));
  const [r, g, b] = hexToRgb(palette[HEAT_PALETTE_INDEX]);
  const nr = Math.round(r + (HEAT_TARGET[0] - r) * t);
  const ng = Math.round(g + (HEAT_TARGET[1] - g) * t);
  const nb = Math.round(b + (HEAT_TARGET[2] - b) * t);
  const result = [...palette];
  result[HEAT_PALETTE_INDEX] = `#${nr.toString(16).padStart(2, "0")}${ng.toString(16).padStart(2, "0")}${nb.toString(16).padStart(2, "0")}`;
  return result;
}

// ──────────────────────────────────────────────────────────────────────────
// 5. HUD 병합 (robo-hud.mjs 로직 재사용)
// ──────────────────────────────────────────────────────────────────────────
const stripAnsi = (s) => s.replace(/\x1b\[[0-9;]*m/g, "");
const visWidth = (s) => [...stripAnsi(s)].length;

function runHud(input) {
  try {
    return execFileSync(process.execPath, [HUD], {
      input,
      encoding: "utf8",
      stdio: ["pipe", "pipe", "ignore"],
    }).replace(/\n+$/, "");
  } catch (error) {
    return error.stdout ? error.stdout.toString() : "";
  }
}

function mergeWithHud(spriteLines, hud) {
  const hudLines = hud ? hud.split("\n") : [];
  if (hudLines.length === 0) return spriteLines.join("\n");
  const leftWidth = Math.max(...spriteLines.map(visWidth));
  const sep = ` \x1b[2m│\x1b[0m `;
  const rows = Math.max(spriteLines.length, hudLines.length);
  const merged = [];
  for (let i = 0; i < rows; i += 1) {
    const left = spriteLines[i] ?? "";
    const pad = " ".repeat(Math.max(0, leftWidth - visWidth(left)));
    const right = hudLines[i] ?? "";
    const leftCell = (left ? `${left}${RESET}` : "") + pad;
    merged.push(`${leftCell}${sep}${right}`.replace(/[ \t]+$/, ""));
  }
  return merged.join("\n");
}

// ──────────────────────────────────────────────────────────────────────────
// 6. HUD 후처리 (플러그인 dist를 건드리지 않고 최종 출력만 다듬는다 — 플러그인 업데이트에도 유지)
//   (1) effort 배지: HUD가 그리지 않는 statusline 입력 effort.level을 상단(모델) 줄에 붙인다.
//   (2) progress bar 미화: HUD의 █/░ 10칸 반올림 바를 8분할 부분 블록으로 재렌더해 부드럽게.
//   (3) 셋째 줄 재구성: 첫 줄의 경과 시간을 떼어 계정 배지와 함께 셋째 줄로 모은다.
// ──────────────────────────────────────────────────────────────────────────
const DIM = "\x1b[2m";
// effort 레벨별 색(낮음=흐림 → 높을수록 따뜻한 색으로 강도 표현).
const EFFORT_STYLE = {
  low: "\x1b[2m",
  medium: "\x1b[36m",
  high: "\x1b[32m",
  xhigh: "\x1b[33m",
  max: "\x1b[91m",
};
function effortTag(data) {
  const level = data?.effort?.level;
  if (!level) return null;
  const color = EFFORT_STYLE[level] ?? "\x1b[36m";
  return `${color}⚡${level}${RESET}`;
}

const VERTEX_COLOR = "\x1b[94m";
// Vertex 세션은 구독 계정 자격증명을 쓰지 않는데도 ~/.claude.json의 oauthAccount는 그대로 남아 있어,
// 이메일만 그리면 구독 계정으로 도는 세션과 구분되지 않는다. 그래서 계정보다 먼저 판정한다.
// env는 settings.json의 env가 claude 프로세스에 실려 상태바 자식 프로세스까지 상속된다(실측 2026-09-02:
// 상태바 프로세스에서 CLAUDE_CODE_USE_VERTEX=1/ANTHROPIC_VERTEX_PROJECT_ID 관측).
function vertexTag() {
  const flag = process.env.CLAUDE_CODE_USE_VERTEX;
  if (!flag || ["0", "false"].includes(flag.toLowerCase())) return null;
  const project = process.env.ANTHROPIC_VERTEX_PROJECT_ID;
  return `${VERTEX_COLOR}☁️ vertex${project ? ` ${project}` : ""}${RESET}`;
}

// 로그인 계정(이메일) 배지. ~/.claude.json이 갱신 중이라 파싱에 실패하면 그 프레임만 배지를 생략한다.
function accountTag() {
  const vertex = vertexTag();
  if (vertex) return vertex;
  try {
    const email = JSON.parse(readFileSync(CLAUDE_CONFIG, "utf8"))?.oauthAccount?.emailAddress;
    if (!email) return null;
    return `${DIM}👤 ${email}${RESET}`;
  } catch {
    return null;
  }
}

// 둥근 사각 세그먼트: 채움 ▰ / 빈칸 ▱ (반올림 기준 정수 칸).
const BAR_FILLED = "▰";
const BAR_EMPTY = "▱";
// HUD의 coloredBar/quotaBar 출력 형태: <색>█…█<DIM>░…░<RESET>. 색과 폭은 보존하고 글리프만 교체.
// compact(가로) 레이아웃은 한 줄에 Context/Usage/Weekly 바가 함께 있으므로 g 플래그로 모두 교체한다.
const BAR_RE = /(\x1b\[[0-9;]*m)(█*)(?:\x1b\[2m(░*))?\x1b\[0m/g;
function prettifyBar(line) {
  return line.replace(BAR_RE, (full, leadColor, fullBlocks, emptyBlocks, offset, source) => {
    const width = fullBlocks.length + (emptyBlocks ?? "").length;
    if (width === 0) return full;
    // 이 바 뒤에 처음 나오는 NN%를 해당 바의 비율로 사용(한 줄 다중 바 대응).
    const after = source.slice(offset + full.length);
    const pctMatch = after.match(/(\d+)%/);
    const percent = pctMatch ? Number(pctMatch[1]) : Math.round((fullBlocks.length / width) * 100);
    const clamped = Math.max(0, Math.min(100, percent));
    const filledCount = Math.min(width, Math.round((clamped / 100) * width));
    const emptyCount = width - filledCount;
    return `${leadColor}${BAR_FILLED.repeat(filledCount)}${DIM}${BAR_EMPTY.repeat(emptyCount)}${RESET}`;
  });
}

// 모델은 첫 세그먼트다. 첫 구분자(' │ ' 또는 ' | ') 앞에 effort를 끼워 모델 바로 옆에 붙인다.
// (compact 레이아웃은 line 0에 model │ project │ duration이 한 줄로 들어와, 끝에 붙이면 duration 뒤로 밀린다)
function insertEffortNextToModel(line, tag) {
  let sepIndex = -1;
  for (const sep of [" │ ", " | "]) {
    const index = line.indexOf(sep);
    if (index >= 0 && (sepIndex < 0 || index < sepIndex)) sepIndex = index;
  }
  if (sepIndex < 0) return `${line} ${tag}`;
  return `${line.slice(0, sepIndex)} ${tag}${line.slice(sepIndex)}`;
}

// HUD가 한 줄 안의 세그먼트를 잇는 구분자(project.ts의 parts.join으로 붙는 U+2502).
const HUD_SEPARATOR = " \u2502 ";
// 경과 시간 세그먼트(`⏱️  1m`)를 첫 줄에서 떼어 낸다. `⏱️`는 U+23F1+U+FE0F이라 앞 코드포인트만으로 찾는다.
// 반환값은 [세그먼트를 뺀 줄, 떼어 낸 세그먼트(없으면 null)].
function extractDurationSegment(line) {
  const segments = line.split(HUD_SEPARATOR);
  const index = segments.findIndex((segment) => segment.includes("\u23f1"));
  if (index < 0) return [line, null];
  const [duration] = segments.splice(index, 1);
  return [segments.join(HUD_SEPARATOR), duration];
}

function transformHud(hud, data) {
  if (!hud) return hud;
  const lines = hud.split("\n");
  const tag = effortTag(data);
  if (tag && lines.length > 0) lines[0] = insertEffortNextToModel(lines[0], tag);
  // 경과 시간과 계정은 둘 다 세션 내내 성격이 변하지 않는 정보라, 첫 줄에서 빼내 셋째 줄로 모은다.
  // 그래야 첫 줄에는 자주 바뀌는 모델·프로젝트·브랜치만 남아 읽는 순서가 짧아진다.
  // (토큰 누적량 줄은 claude-hud 설정의 display.showSessionTokens=false로 꺼져 셋째 줄이 비어 있다)
  let duration = null;
  if (lines.length > 0) [lines[0], duration] = extractDurationSegment(lines[0]);
  const footer = [duration, accountTag()].filter(Boolean);
  // HUD가 줄을 더 그리더라도 이 줄은 셋째 자리를 지키도록 인덱스 2에 끼운다.
  if (footer.length > 0) lines.splice(Math.min(2, lines.length), 0, footer.join(HUD_SEPARATOR));
  for (let index = 0; index < lines.length; index += 1) {
    if (/[█░]/.test(lines[index])) lines[index] = prettifyBar(lines[index]);
  }
  return lines.join("\n");
}

// ──────────────────────────────────────────────────────────────────────────
// main
// ──────────────────────────────────────────────────────────────────────────
function main() {
  let input = "";
  try {
    input = readFileSync(0, "utf8");
  } catch {
    input = "";
  }
  let data = {};
  try {
    data = JSON.parse(input);
  } catch {
    data = {};
  }

  const nowMs = process.env.CLAWD_NOW ? Number(process.env.CLAWD_NOW) : Date.now();

  // 상태 결정
  let effectiveState;
  if (process.env.CLAWD_FORCE_STATE) {
    effectiveState = process.env.CLAWD_FORCE_STATE;
  } else {
    // 훅 state + transcript 도출 state를 최신값 기준으로 병합(원본 renderer.ts와 동일).
    const persistedState = readSessionState(data.session_id);
    const transcriptState = deriveStateFromTranscript(data.transcript_path, nowMs);
    let merged = selectMostRecentState(persistedState, transcriptState);
    // Claude Code는 turn 종료 직후 idle 알림(idle_prompt)으로 훅 state를 idle로 만들어 Stop→done을 덮는다.
    // (1) idle_prompt로 막 idle이 됐고(hold 이내) transcript도 방금 턴이 끝난 상태(lastUpdatedAt 최신)면 완료(done) 애니를 유지한다.
    //     transcript.lastUpdatedAt 최신 조건으로, 한참 뒤 도착하는 idle_prompt에 done이 재점멸하는 것을 막는다.
    if (
      merged && merged.currentState === "idle" &&
      persistedState?.lastNotificationType === "idle_prompt" &&
      nowMs - Date.parse(persistedState.lastStateChangedAt) < HOLD_MS.done &&
      transcriptState && nowMs - Date.parse(transcriptState.lastUpdatedAt) < DONE_VISIBLE_MS
    ) {
      effectiveState = "done";
    } else {
      // (2) 툴 반짝 이후 '모델이 다음 동작을 추론 중'인 구간: 훅 state는 tool_success로 남아 idle로 강등되지만,
      //     transcript가 작업 중(thinking)을 가리키면 대기(idle) 대신 thinking을 우선한다(done/permission/question은 침범하지 않음).
      if (
        merged && transcriptState && transcriptState.currentState === "thinking" &&
        ["idle", "tool_success", "tool_failure"].includes(merged.currentState)
      ) {
        merged = transcriptState;
      }
      effectiveState = resolveEffectiveState(merged, nowMs);
    }
  }

  // heat 적용
  const usedPercentage = process.env.CLAWD_FORCE_PCT
    ? Number(process.env.CLAWD_FORCE_PCT)
    : data?.context_window?.used_percentage;
  const palette = applyHeat(PALETTE, usedPercentage);

  // 프레임 선택
  const frames = buildFrames(effectiveState);
  const period = FRAME_PERIOD[effectiveState] ?? DEFAULT_FRAME_PERIOD;
  const frameIndex = Math.floor(nowMs / period) % frames.length;
  const grid = frames[frameIndex];

  const spriteLines = renderQuadrant(grid, palette);

  if (process.env.CLAWD_NO_HUD) {
    process.stdout.write(spriteLines.map((l) => `${l}${RESET}`).join("\n"));
    return;
  }
  const hud = transformHud(runHud(input), data);
  process.stdout.write(mergeWithHud(spriteLines, hud));
}

main();
