// Claude Code PreToolUse(Bash) hook (Mac/Windows 공통)
// 기기에 설치·제거하는 Gradle 작업은 연결된 기기 전부가 대상이라, ANDROID_SERIAL 없이 실행하면 사용자가 쓰는 다른 기기에도 설치됨.
// CLAUDE.md 규칙만으로는 빠뜨린 세션이 있어(2026-10-28 S25 설치) 실행 전에 거부하고 다시 실행하게 함

// 기기 대상 Gradle 작업: install*·uninstall*·connected*AndroidTest
const gradleDevicePattern = /\bgradlew(?:\.bat)?\b[^;&|\n]*\b(?:install[A-Z]\w*|uninstall[A-Z]\w*|connected\w*AndroidTest)\b/;
// 같은 명령 안의 ANDROID_SERIAL 지정(앞에 붙인 대입·export·set 모두), 빈 값은 제외
const serialPattern = /\bANDROID_SERIAL=(?=[^\s;&|])/;

/** 기기 대상 Gradle 작업인데 대상 기기를 지정하지 않은 명령인지 */
export function lacksSerial(command, environment = process.env) {
  if (!gradleDevicePattern.test(command)) return false;
  if (serialPattern.test(command)) return false;
  return !environment.ANDROID_SERIAL;
}

// 직접 실행(훅 호출)일 때만 stdin 처리, import(검증 스크립트)일 때는 함수만 노출
if (process.argv[1]?.endsWith("require-android-serial.mjs")) {
  let raw = "";
  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (chunk) => {
    raw += chunk;
  });
  process.stdin.on("end", () => {
    try {
      const command = JSON.parse(raw)?.tool_input?.command ?? "";
      if (lacksSerial(command)) {
        process.stdout.write(
          JSON.stringify({
            hookSpecificOutput: {
              hookEventName: "PreToolUse",
              permissionDecision: "deny",
              permissionDecisionReason:
                "기기에 설치·제거하는 Gradle 작업은 연결된 기기 전부가 대상이라 ANDROID_SERIAL 없이 실행할 수 없음. " +
                "`adb devices -l`로 대상 기기 시리얼을 확인하고 `ANDROID_SERIAL=<시리얼> ./gradlew …`로 다시 실행(CLAUDE.md git·테스트 절).",
            },
          })
        );
      }
    } catch {
      // 훅 실패가 도구 실행을 막지 않도록 통과
    }
    process.exit(0);
  });
}
