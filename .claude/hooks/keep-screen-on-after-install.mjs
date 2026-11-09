// Claude Code PostToolUse(Bash) hook (Mac/Windows 공통)
// 실사용 debug 앱(com.black.app)의 설치(gradlew install*·adb install)와 그 앱 대상 am instrument는 앱 프로세스를 종료해
// 화면 켜짐 유지 오버레이 창도 함께 제거.
// 메모리 규칙("설치·계측이 끝날 때마다 다시 켜기")만으로는 세션이 빠뜨려 기기가 꺼지고 잠기므로, 명령이 끝나면 대상 기기에 다시 켬
// debug 빌드 KeepScreenOnReceiver의 ACTIVATE broadcast 사용(이미 켜져 있으면 그대로, Receiver 없는 빌드는 data 없는 결과)
// 계측 테스트 전용 빌드(uitest, com.black.app.uitest)의 설치·계측은 다른 패키지라 실사용 앱을 종료하지 않아 대상 제외
// 건너뜀: 백그라운드 실행(끝난 시점을 알 수 없고 계측 중 broadcast는 KeepScreenOnTileUiTest를 깸),
// 화면 꺼짐(꺼지면 해제되는 KeepScreenOnGlobal 설계 유지), 다른 세션의 JustApp 계측(두 패키지 모두) 진행 중

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const packageName = "com.black.app";
// 계측 테스트 전용 빌드 패키지(app/build.gradle.kts의 uitest applicationIdSuffix)
const uitestPackageName = `${packageName}.uitest`;
const activateAction = "com.black.app.action.ACTIVATE_KEEP_SCREEN_ON";
const adbTimeoutMillis = 10000;

// 실사용 앱 프로세스를 종료하는 명령: gradlew 설치·연결 테스트 작업, adb 설치·계측(Uitest 작업, uitest가 들어간 APK 경로·계측 대상은 제외)
const gradleDevicePattern = /\bgradlew(?:\.bat)?\b[^;&|\n]*\b(?:install(?!Uitest)[A-Z]\w*|connected(?!Uitest)\w*AndroidTest)\b/;
const adbDevicePattern = /\badb(?:\.exe)?\b[^;&|\n]*?[\s"'](?:install(?:-multiple)?|am\s+instrument|pm\s+install)\b(?![^;&|\n]*uitest)/;
// 셸 백그라운드 실행(끝의 단독 &), 2>&1·&& 제외
const shellBackgroundPattern = /[^&>]&\s*(?:$|[\n;])/;

/** 명령 안의 단순 변수 대입(S=R3CX…, ANDROID_SERIAL=…) 수집 */
export function collectVariables(command) {
  const variables = {};
  for (const match of command.matchAll(/(?:^|[\s;&|(])([A-Za-z_]\w*)=(["']?)([\w.:-]+)\2(?=[\s;&|)]|$)/g)) {
    variables[match[1]] = match[3];
  }
  return variables;
}

/** 명령이 대상으로 지정한 기기 시리얼, 지정이 없으면 빈 배열 */
export function resolveSerials(command) {
  const variables = collectVariables(command);
  const expand = (token) => {
    const unquoted = token.replace(/^["']|["']$/g, "");
    const variable = unquoted.match(/^\$\{?([A-Za-z_]\w*)\}?$/);
    return variable ? variables[variable[1]] : unquoted;
  };

  const serials = new Set();
  for (const match of command.matchAll(/\badb(?:\.exe)?\b[^;&|\n]*?\s-s\s+(\S+)/g)) {
    const serial = expand(match[1]);
    if (serial) serials.add(serial);
  }
  if (variables.ANDROID_SERIAL) serials.add(variables.ANDROID_SERIAL);
  return [...serials];
}

/** 기기 앱 프로세스를 종료하는 포그라운드 명령 여부 */
export function isDeviceCommand(command, runInBackground) {
  if (runInBackground || shellBackgroundPattern.test(command)) return false;
  return gradleDevicePattern.test(command) || adbDevicePattern.test(command);
}

/** 프로젝트 SDK의 adb, 없으면 PATH의 adb */
function findAdb(projectDir) {
  try {
    const properties = fs.readFileSync(path.join(projectDir, "local.properties"), "utf8");
    const sdkDirectory = properties.match(/^sdk\.dir=(.+)$/m)?.[1]?.trim().replace(/\\:/g, ":").replace(/\\\\/g, "\\");
    const adbPath = sdkDirectory && path.join(sdkDirectory, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb");
    if (adbPath && fs.existsSync(adbPath)) return adbPath;
  } catch {
    // local.properties 없음
  }
  return "adb";
}

function runAdb(adb, args) {
  return execFileSync(adb, args, { encoding: "utf8", timeout: adbTimeoutMillis, stdio: ["ignore", "pipe", "ignore"] });
}

function connectedSerials(adb) {
  return runAdb(adb, ["devices"])
    .split(/\r?\n/)
    .map((line) => line.match(/^(\S+)\tdevice$/)?.[1])
    .filter(Boolean);
}

/** 기기 하나에 켜짐 유지 요청 후 결과 문구 반환 */
function activateOn(adb, serial) {
  // 화면 상태·다른 세션 계측 여부를 한 번에 조회(계측이 없으면 grep 종료 코드 1이 adb로 전달되므로 true로 마무리)
  // uitest 계측 중에 켜면 실사용 앱의 켜짐 유지 창이 KeepScreenOnTileUiTest의 화면 유지 판정을 깨므로 두 패키지 모두 확인
  const state = runAdb(adb, [
    "-s",
    serial,
    "shell",
    `dumpsys power | grep mWakefulness=; dumpsys activity processes ${packageName} | grep 'Active instrumentation'; ` +
      `dumpsys activity processes ${uitestPackageName} | grep 'Active instrumentation'; true`,
  ]);
  if (!/mWakefulness=Awake/.test(state)) {
    return `${serial} 화면이 꺼져 있어 켜지 않음(꺼지면 해제되는 설계)`;
  }
  if (/Active instrumentation/.test(state)) {
    return `${serial} 다른 계측이 진행 중이라 켜지 않음(계측이 끝난 뒤 다시 켤 것)`;
  }

  const result = runAdb(adb, [
    "-s",
    serial,
    "shell",
    "am",
    "broadcast",
    "--include-stopped-packages",
    "-a",
    activateAction,
    "-p",
    packageName,
  ]);
  const data = result.match(/data="([^"]*)"/)?.[1];
  if (data === "active=true") return `${serial} 켜짐(active=true)`;
  if (data) return `${serial} 켜지 못함(${data}, 다른 앱 위에 표시 권한 확인)`;
  return `${serial} 응답 없음(KeepScreenOnReceiver가 없는 빌드이거나 미설치, 필요하면 타일로 켤 것)`;
}

function main(payload) {
  const command = payload?.tool_input?.command;
  if (typeof command !== "string" || !isDeviceCommand(command, payload?.tool_input?.run_in_background === true)) {
    return;
  }

  const adb = findAdb(process.env.CLAUDE_PROJECT_DIR || payload?.cwd || ".");
  let connected;
  try {
    connected = connectedSerials(adb);
  } catch {
    return;
  }

  // 지정 기기 중 연결된 것, 지정이 없으면 gradlew처럼 연결된 기기 전부가 대상
  const requested = resolveSerials(command);
  const targets = requested.length > 0 ? requested.filter((serial) => connected.includes(serial)) : connected;
  if (targets.length === 0) return;

  const lines = targets.map((serial) => {
    try {
      return activateOn(adb, serial);
    } catch {
      return `${serial} adb 실패로 확인하지 못함`;
    }
  });
  const context = `JustApp 화면 켜짐 유지 자동 재활성화(PostToolUse 훅): ${lines.join(", ")}`;
  process.stdout.write(
    JSON.stringify({ hookSpecificOutput: { hookEventName: "PostToolUse", additionalContext: context } })
  );
}

// 직접 실행(훅 호출)일 때만 stdin 처리, import(검증 스크립트)일 때는 함수만 노출
if (process.argv[1]?.endsWith("keep-screen-on-after-install.mjs")) {
  let raw = "";
  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (chunk) => {
    raw += chunk;
  });
  process.stdin.on("end", () => {
    try {
      main(JSON.parse(raw));
    } catch {
      // 훅 실패가 도구 결과를 막지 않도록 통과
    }
    process.exit(0);
  });
}
