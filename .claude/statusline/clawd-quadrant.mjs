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
//   CLAWD_FORCE_TRICK=jump     트릭(시작 화면 클릭 애니)을 상태와 무관하게 반복 재생(CLAWD_NOW로 프레임 지정)
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
// body(몸 오렌지)는 heat target — context% 높을 때 빨강으로 보간된다.
const PALETTE = {
  body: "#d77757", // 몸 오렌지 (HEAT TARGET)
  gold: "#f3c969", // 골드 (반짝/물음)
  blue: "#7db8ff", // 파랑 (땀/서브에이전트)
  red: "#ff5555", // 빨강 (permission 경고)
  dust: "#999999", // 트릭 먼지·그림자 (Claude Code 테마 inactive)
};

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
// 1. 스프라이트 — Claude Code 시작 화면(로고) Clawd의 글리프 표를 그대로 이식(2.1.312 번들의 포즈 렌더러 기준).
//   몸 9×3 셀 = 팔(ARMS)·눈(EYES)·발(FEET) 글리프 조합. 상태바는 왼쪽 여백 1칸과 오른쪽 점 마커 1칸을 더해 11×3 셀로 그린다.
//   셀은 { ch, fg, bg }이고 배경은 SAFE_BG로 칠한다. 감은 눈(▂)만 bg에 몸 색을 쓰는 '눈꺼풀' 셀이다.
//   시작 화면과 같은 몸을 쓰므로 상태 애니와 트릭(클릭 애니) 사이를 오가도 모양이 튀지 않는다.
// ──────────────────────────────────────────────────────────────────────────
const SPRITE_WIDTH = 9;
const SPRITE_HEIGHT = 3;
const BOX_WIDTH = SPRITE_WIDTH + 2;
const MARKER_COLUMN = BOX_WIDTH - 1;

// 팔: 0행 왼쪽(0~1열)·오른쪽(8열), 1행 왼쪽(0~1열)·오른쪽(7~8열) 글리프.
// down(손이 옆)·up(만세)은 원본 그대로, low(손을 아래로 내림)는 걷기·시무룩 자세용으로 같은 픽셀 규칙에 맞춰 추가했다.
const ARMS = {
  down: { row0Left: " ▐", row0Right: "", row1Left: "▝▜", row1Right: "█▀" },
  up: { row0Left: "▗▟", row0Right: "▄", row1Left: " ▜", row1Right: "█▘" },
  low: { row0Left: " ▐", row0Right: "", row1Left: "▗▟", row1Right: "█▄" },
};
// 눈: 0행 2~7열. 두리번은 눈 노치가 위쪽 대각선으로 옮겨 간다. lid 구간은 몸 색 위 어두운 ▂(감은 눈).
const EYES = {
  open: [{ glyphs: "▛███▛█" }],
  left: [{ glyphs: "▟███▟█" }],
  right: [{ glyphs: "█▟███▟" }],
  closed: [{ glyphs: "▂", lid: true }, { glyphs: "███" }, { glyphs: "▂", lid: true }, { glyphs: "█" }],
  wink: [{ glyphs: "▛███" }, { glyphs: "▂", lid: true }, { glyphs: "█" }],
};
// 발: 2행. left/right는 한쪽 발만 디딘 자세(발 구르기·걷기).
const FEET = {
  both: " ▝▝   ▝▝ ",
  left: " ▝▝      ",
  right: "      ▝▝ ",
};
const NAMED_POSES = {
  default: { eyes: "open", arms: "down", feet: "both" },
  "arms-up": { eyes: "open", arms: "up", feet: "both" },
  "look-left": { eyes: "left", arms: "down", feet: "both" },
  "look-right": { eyes: "right", arms: "down", feet: "both" },
};
// 몸 돌리기(turn·coin-hop) 각도별 3행 글리프. 오른쪽으로 돌아 등을 보인 뒤 왼쪽으로 돌아온다.
const FACINGS = {
  "right-12": [" ▐█▜██▛█ ", "▝▜██████▀", " ▝▝   ▝▝ "],
  "right-30": ["  █▛██▛▌ ", " ▝█████▛ ", "  ▘▘  ▘▘ "],
  "right-55": ["  ▐█▛█▜  ", "  ▐████  ", "  ▝▝ ▝▝  "],
  "right-75": ["   ██▛▌  ", "   ███▌  ", "   ▘  ▘  "],
  edge: ["   ▐██   ", "   ▐██   ", "   ▝ ▝   "],
  "back-105": ["   ███▌  ", "   ███▌  ", "   ▘  ▘  "],
  "back-125": ["  ▐████  ", "  ▐████  ", "  ▝▝ ▝▝  "],
  "back-150": ["  █████▌ ", " ▝█████▛ ", "  ▘▘  ▘▘ "],
  back: [" ▐██████ ", "▝▜██████▀", " ▝▝   ▝▝ "],
  "left-75": ["   ▛██▌  ", "   ███▌  ", "   ▘  ▘  "],
  "left-55": ["  ▐▜▛██  ", "  ▐████  ", "  ▝▝ ▝▝  "],
  "left-30": ["  ▛██▛█▌ ", " ▝█████▛ ", "  ▘▘  ▘▘ "],
  "left-12": [" ▐▛███▜█ ", "▝▜██████▀", " ▝▝   ▝▝ "],
};
// 트릭 바닥 효과: 웅크릴 때 양끝 먼지, 몸 돌리며 뛸 때 발 사이 그림자(원본 inactive 색).
const POOF = { dot: "·", wave: "~" };
const SHADOW = { wide: { glyphs: "▁▁▁", column: 3 }, narrow: { glyphs: "▁", column: 4 } };

// 원본 Mo(eyes, arms, feet)와 같은 자세 객체.
const posture = (eyes, arms = "down", feet = "both") => ({ eyes, arms, feet });

const createGrid = (width) => Array.from({ length: SPRITE_HEIGHT }, () => new Array(width).fill(null));

// 공백은 빈 셀(배경)로 두고 나머지 글리프만 칠한다. 그리드 밖은 잘라낸다.
function paintGlyphs(grid, row, column, glyphs, color, lid = false) {
  [...glyphs].forEach((ch, index) => {
    const x = column + index;
    if (ch === " " || !grid[row] || x < 0 || x >= grid[row].length) return;
    grid[row][x] = lid ? { ch, fg: SAFE_BG, bg: color } : { ch, fg: color, bg: SAFE_BG };
  });
}

// 자세 하나를 9×3 몸 그리드로 그린다. pose = 이름 | { eyes, arms, feet } | { facing }.
function drawPose(pose, bodyColor) {
  const grid = createGrid(SPRITE_WIDTH);
  if (pose.facing) {
    FACINGS[pose.facing].forEach((glyphs, row) => paintGlyphs(grid, row, 0, glyphs, bodyColor));
    return grid;
  }
  const { eyes, arms, feet } = typeof pose === "string" ? NAMED_POSES[pose] : pose;
  const arm = ARMS[arms];
  paintGlyphs(grid, 0, 0, arm.row0Left, bodyColor);
  let column = 2;
  for (const span of EYES[eyes]) {
    paintGlyphs(grid, 0, column, span.glyphs, bodyColor, span.lid);
    column += [...span.glyphs].length;
  }
  paintGlyphs(grid, 0, 8, arm.row0Right, bodyColor);
  paintGlyphs(grid, 1, 0, arm.row1Left, bodyColor);
  paintGlyphs(grid, 1, 2, "█████", bodyColor);
  paintGlyphs(grid, 1, 7, arm.row1Right, bodyColor);
  paintGlyphs(grid, 2, 0, FEET[feet], bodyColor);
  return grid;
}

// 프레임 = { pose, offset, poof, shadow, sparkle, sideMark } → 11×3 셀.
//   offset: 몸을 아래(+)·위(-)로 옮기고 3행 밖은 잘라낸다(웅크림·숨기·떨어지기, 원본 marginTop+overflow hidden).
//   poof는 offset>0일 때만 바닥 양끝에, shadow는 발 사이에 덧그린다(원본 absolute 오버레이).
//   sparkle(상단 양쪽 골드 반짝)·sideMark(오른쪽 점 마커)는 상태바 상태 표시.
function composeFrame(frame, palette) {
  const sprite = drawPose(frame.pose ?? "default", palette.body);
  const box = createGrid(BOX_WIDTH);
  const offset = frame.offset ?? 0;
  for (let row = 0; row < SPRITE_HEIGHT; row += 1) {
    const targetRow = row + offset;
    if (targetRow < 0 || targetRow >= SPRITE_HEIGHT) continue;
    for (let column = 0; column < SPRITE_WIDTH; column += 1) box[targetRow][column + 1] = sprite[row][column];
  }
  if (frame.poof && offset > 0) {
    paintGlyphs(box, SPRITE_HEIGHT - 1, 1, POOF[frame.poof], palette.dust);
    paintGlyphs(box, SPRITE_HEIGHT - 1, SPRITE_WIDTH, POOF[frame.poof], palette.dust);
  }
  if (frame.shadow) {
    const { glyphs, column } = SHADOW[frame.shadow];
    paintGlyphs(box, SPRITE_HEIGHT - 1, column + 1, glyphs, palette.dust);
  }
  if (frame.sparkle) {
    // 손 바로 위 대칭 위치(몸 중심 기준 양쪽 끝 픽셀).
    paintGlyphs(box, 0, 1, "▝", palette.gold);
    paintGlyphs(box, 0, SPRITE_WIDTH, "▝", palette.gold);
  }
  if (frame.sideMark) {
    for (const row of frame.sideMark.rows) paintGlyphs(box, row, MARKER_COLUMN, "▖", palette[frame.sideMark.color]);
  }
  return box;
}

function renderCells(grid) {
  return grid.map((row) =>
    row.map((cell) => (cell ? `${fg(cell.fg)}${bg(cell.bg)}${cell.ch}${RESET}` : `${bg(SAFE_BG)} ${RESET}`)).join(""),
  );
}

// 상태별 프레임 배열.
function buildFrames(state) {
  switch (state) {
    case "idle":
      // 대기: 눈만 두리번(좌→정→우→정). 사이사이 트릭은 idleTrickFrame이 끼운다.
      return [{ pose: "look-left" }, { pose: "default" }, { pose: "look-right" }, { pose: "default" }];
    case "tool_running":
      // 걷기: 발 번갈아 디디기 + 팔 올림↔내림 스윙
      return [{ pose: posture("open", "up", "left") }, { pose: posture("open", "low", "right") }];
    case "subagent_running": {
      // 걷기 + 팔 스윙 + 우측 파랑 점(서브에이전트 구분)
      const sideMark = { rows: [0, 1], color: "blue" };
      return [
        { pose: posture("open", "up", "left"), sideMark },
        { pose: posture("open", "low", "right"), sideMark },
      ];
    }
    case "thinking":
      // 생각 중: 팔 까딱(옆↔만세)
      return [{ pose: "default" }, { pose: "arms-up" }];
    case "tool_success":
    case "auth_success":
      // 성공: 골드 반짝
      return [{ pose: "default", sparkle: true }];
    case "done":
      // 완료 축하: 발 번갈아 구르며 골드 반짝 점멸 (오래 유지)
      return [
        { pose: posture("open", "down", "left"), sparkle: true },
        { pose: posture("open", "down", "right") },
      ];
    case "tool_failure":
      // 실패: 우측 파랑 땀방울 + 팔 내림(시무룩)
      return [{ pose: posture("open", "low"), sideMark: { rows: [0, 1], color: "blue" } }];
    case "question": {
      // 질문: 팔 흔들기(만세→옆→내림→옆) + 우측 골드 점
      const sideMark = { rows: [0, 1], color: "gold" };
      return ["up", "down", "low", "down"].map((arms) => ({ pose: posture("open", arms), sideMark }));
    }
    case "permission":
      // 권한 요청: 팔 올린 채 우측 빨강 점 점멸(주목)
      return [{ pose: "arms-up", sideMark: { rows: [0, 1, 2], color: "red" } }, { pose: "arms-up" }];
    default:
      return [{ pose: "default" }];
  }
}

// ──────────────────────────────────────────────────────────────────────────
// 2. 트릭 — 시작 화면 Clawd를 클릭하면 재생되는 애니메이션(2.1.312 번들의 시퀀스 표 이식, 원본 이름 유지).
//   원본은 60ms 프레임이지만 상태바는 refreshInterval 최소 1초(실측: 세션마다 1000ms 간격, 위상 고정)라
//   같은 자세가 이어지는 프레임을 하나로 합쳐 TRICK_FRAME_MS마다 한 자세씩 넘긴다.
//   상태바는 클릭을 받을 수 없어 idle 중 IDLE_TRICK_INTERVAL_MS마다 하나를 무작위로 재생한다.
//   후보는 원본 클릭 후보와 같은 11개(화면 밖에서 걸어 들어오는 skip·waddle·peek은 첫 등장 연출 전용이라 제외).
// ──────────────────────────────────────────────────────────────────────────
const TRICK_FRAME_MS = 1000;
const IDLE_TRICK_INTERVAL_MS = 30000;

const repeat = (pose, count, options = {}) => Array.from({ length: count }, () => ({ pose, ...options }));
// 웅크리며 먼지(·→~) — 원본 jr
const hop = () => [
  { pose: "default", offset: 1, poof: "dot" },
  { pose: "default", offset: 1, poof: "wave" },
];
const turnFrames = (facings, shadow) => facings.map((facing) => ({ pose: { facing }, shadow }));
const rest = { pose: "default" };
// 눈 깜빡 후 기본 자세 — 원본 Ys
const blink = [...repeat(posture("closed"), 1), rest];

const TRICK_SEQUENCES = {
  jump: [...hop(), ...repeat("arms-up", 3), ...repeat("default", 1), ...hop(), ...repeat("arms-up", 3), ...repeat("default", 1)],
  look: [...repeat("look-right", 5), ...repeat("look-left", 5), ...repeat("default", 1)],
  spin: [
    ...repeat("look-left", 2), ...repeat("look-right", 2), ...repeat("look-left", 2),
    ...repeat("arms-up", 3), ...repeat("default", 1),
  ],
  peekaboo: [
    ...repeat("default", 1, { offset: SPRITE_HEIGHT }), ...repeat("default", 3, { offset: 2 }),
    ...repeat("look-right", 3, { offset: 2 }), ...repeat("look-left", 3, { offset: 2 }),
    ...repeat("default", 2, { offset: 2 }), ...repeat("default", 1, { offset: 1 }),
    ...repeat("arms-up", 4), ...repeat("default", 2), ...blink,
  ],
  drop: [
    ...repeat("arms-up", 1, { offset: -SPRITE_HEIGHT }), ...repeat("arms-up", 2, { offset: -2 }),
    ...repeat("arms-up", 2, { offset: -1 }), ...repeat("arms-up", 1), ...hop(), ...repeat("default", 3), ...blink,
  ],
  wink: [...repeat(posture("wink"), 5), rest],
  boop: [
    ...repeat(posture("closed"), 1, { offset: 1, poof: "dot" }), ...repeat(posture("closed"), 2, { offset: 1, poof: "wave" }),
    ...repeat("look-left", 4), ...repeat("default", 2), ...blink,
  ],
  tap: [
    ...repeat(posture("open", "down", "left"), 2), ...repeat(posture("open", "down", "right"), 2),
    ...repeat(posture("open", "down", "left"), 2), ...repeat(posture("open", "down", "right"), 2),
    ...repeat(posture("open", "down", "left"), 1), ...repeat(posture("open", "down", "right"), 1),
    ...repeat("arms-up", 3), rest,
  ],
  sneeze: [
    ...repeat(posture("closed", "up"), 4), ...repeat(posture("closed"), 2, { offset: 1, poof: "wave" }),
    ...repeat(posture("closed"), 2), ...repeat("default", 2), ...blink,
  ],
  turn: [
    ...turnFrames(["right-12", "right-30", "right-55", "right-75", "edge"]),
    ...turnFrames(["back-105", "back-125", "back-150", "back", "back"]),
    ...turnFrames(["back-150", "back-125", "back-105", "edge"]),
    ...turnFrames(["left-75", "left-55", "left-30", "left-12"]),
    rest,
  ],
  "coin-hop": [
    ...repeat("default", 2, { offset: 1 }), ...repeat("arms-up", 1, { shadow: "wide" }),
    ...turnFrames(["right-55", "edge", "back-125", "back"], "narrow"),
    ...turnFrames(["back-125", "edge", "left-55"], "narrow"),
    ...repeat("arms-up", 1, { shadow: "wide" }), ...hop(), rest,
  ],
};

const frameKey = (frame) =>
  JSON.stringify([frame.pose, frame.offset ?? 0, frame.poof ?? null, frame.shadow ?? null]);
const collapseRepeats = (frames) =>
  frames.filter((frame, index) => index === 0 || frameKey(frame) !== frameKey(frames[index - 1]));
const TRICKS = Object.fromEntries(
  Object.entries(TRICK_SEQUENCES).map(([name, frames]) => [name, collapseRepeats(frames)]),
);
const TRICK_NAMES = Object.keys(TRICKS);

// 시각 슬롯 번호로 정하는 결정적 난수. 매 렌더가 새 프로세스라 Math.random으로 뽑으면 렌더마다 트릭이 바뀐다.
function hashSlot(slot) {
  let hash = slot | 0;
  hash = Math.imul(hash ^ (hash >>> 16), 0x45d9f3b);
  hash = Math.imul(hash ^ (hash >>> 16), 0x45d9f3b);
  return (hash ^ (hash >>> 16)) >>> 0;
}

// idle 슬롯(IDLE_TRICK_INTERVAL_MS) 앞부분에 그 슬롯의 트릭을 재생한다. 트릭 구간이 끝났으면 null(평소 두리번).
// 슬롯은 절대 시각 기준이라 idle에 들어선 시점이 트릭 중간이면 중간 자세부터 보인다.
function idleTrickFrame(nowMs) {
  const slot = Math.floor(nowMs / IDLE_TRICK_INTERVAL_MS);
  const frames = TRICKS[TRICK_NAMES[hashSlot(slot) % TRICK_NAMES.length]];
  const index = Math.floor((nowMs - slot * IDLE_TRICK_INTERVAL_MS) / TRICK_FRAME_MS);
  return frames[index] ?? null;
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
  const [r, g, b] = hexToRgb(palette.body);
  const nr = Math.round(r + (HEAT_TARGET[0] - r) * t);
  const ng = Math.round(g + (HEAT_TARGET[1] - g) * t);
  const nb = Math.round(b + (HEAT_TARGET[2] - b) * t);
  return {
    ...palette,
    body: `#${nr.toString(16).padStart(2, "0")}${ng.toString(16).padStart(2, "0")}${nb.toString(16).padStart(2, "0")}`,
  };
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

  // 프레임 선택: 강제 트릭 > idle 트릭 구간 > 상태 애니
  const forcedTrickName = process.env.CLAWD_FORCE_TRICK;
  let frame;
  if (forcedTrickName && Object.hasOwn(TRICKS, forcedTrickName)) {
    const trick = TRICKS[forcedTrickName];
    frame = trick[Math.floor(nowMs / TRICK_FRAME_MS) % trick.length];
  } else {
    frame = effectiveState === "idle" ? idleTrickFrame(nowMs) : null;
  }
  if (!frame) {
    const frames = buildFrames(effectiveState);
    const period = FRAME_PERIOD[effectiveState] ?? DEFAULT_FRAME_PERIOD;
    frame = frames[Math.floor(nowMs / period) % frames.length];
  }

  const spriteLines = renderCells(composeFrame(frame, palette));

  if (process.env.CLAWD_NO_HUD) {
    process.stdout.write(spriteLines.map((l) => `${l}${RESET}`).join("\n"));
    return;
  }
  const hud = transformHud(runHud(input), data);
  process.stdout.write(mergeWithHud(spriteLines, hud));
}

main();
