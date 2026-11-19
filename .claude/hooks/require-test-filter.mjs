// Claude Code PreToolUse(Bash) hook (Mac/Windows 공통)
// 모듈 전체 유닛 테스트·전체 계측 테스트는 사용자가 요청했을 때만 돌리는 규칙(CLAUDE.md git·테스트 절)이
// 산문만으로는 지켜지지 않아(2026-11-20 주석 수정 단위를 합친 뒤 전체 테스트와 기기 계측 139건 재실행) 실행 전에 거부함.
// 요청받았으면 명령 끝에 `# full-test` 주석을 붙여 다시 실행

// 유닛 테스트 작업(`test`, `testDebugUnitTest`, `:app:testStableUnitTest` 등)
const unitTestTask = /^(?::[\w-]+)*:?test(?:[A-Z]\w*UnitTest)?$/;
// 계측 테스트 작업(`connectedDebugAndroidTest`, `:app:connectedAndroidTest` 등)
const connectedTask = /^(?::[\w-]+)*:?connected\w*AndroidTest$/;
// 계측 테스트 대상을 좁히는 러너 인자(클래스·패키지)
const instrumentationFilter = /^-Pandroid\.testInstrumentationRunnerArguments\.(?:class|package)=/;
// 사용자가 전체 실행을 요청했다는 표시
const fullTestMarker = /#\s*full-test\b/;

/** 명령을 셸 구분자 기준 조각으로 나눠 gradlew 뒤 인자 목록만 돌려줌 */
function gradleArgumentLists(command) {
  return command
    .split(/&&|\|\||;|\||\n/)
    .map((segment) => segment.replace(/#.*$/, "").trim().split(/\s+/).map((token) => token.replace(/^['"]|['"]$/g, "")))
    .map((tokens) => {
      const gradleIndex = tokens.findIndex((token) => /(?:^|\/)gradlew(?:\.bat)?$/.test(token));
      return gradleIndex < 0 ? null : tokens.slice(gradleIndex + 1);
    })
    .filter((tokens) => tokens !== null);
}

/**
 * 필터 없이 모듈 전체를 도는 테스트 작업 목록
 *
 * `--tests`는 바로 앞 테스트 작업에만 걸리므로 작업마다 따로 판정하고, 계측 러너 인자는 빌드 전체에 걸림.
 */
export function unfilteredTestTasks(command) {
  if (fullTestMarker.test(command)) {
    return [];
  }
  const unfiltered = [];
  for (const tokens of gradleArgumentLists(command)) {
    const connected = [];
    let hasInstrumentationFilter = false;
    let pendingUnitTest = null;
    for (let index = 0; index < tokens.length; index++) {
      const token = tokens[index];
      // 앞 유닛 테스트 작업의 필터
      if (token === "--tests" || token.startsWith("--tests=")) {
        pendingUnitTest = null;
        if (token === "--tests") {
          index++;
        }
        continue;
      }
      // 계측 러너 인자와 그 밖의 옵션
      if (token.startsWith("-")) {
        hasInstrumentationFilter ||= instrumentationFilter.test(token);
        continue;
      }
      // 다음 작업이 오면 앞 유닛 테스트 작업은 필터 없이 끝난 것
      if (pendingUnitTest) {
        unfiltered.push(pendingUnitTest);
      }
      pendingUnitTest = unitTestTask.test(token) ? token : null;
      if (connectedTask.test(token)) {
        connected.push(token);
      }
    }
    if (pendingUnitTest) {
      unfiltered.push(pendingUnitTest);
    }
    if (!hasInstrumentationFilter) {
      unfiltered.push(...connected);
    }
  }
  return unfiltered;
}

// 직접 실행(훅 호출)일 때만 stdin 처리, import(검증 스크립트)일 때는 함수만 노출
if (process.argv[1]?.endsWith("require-test-filter.mjs")) {
  let raw = "";
  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (chunk) => {
    raw += chunk;
  });
  process.stdin.on("end", () => {
    try {
      const command = JSON.parse(raw)?.tool_input?.command ?? "";
      const tasks = unfilteredTestTasks(command);
      if (tasks.length > 0) {
        process.stdout.write(
          JSON.stringify({
            hookSpecificOutput: {
              hookEventName: "PreToolUse",
              permissionDecision: "deny",
              permissionDecisionReason:
                `필터 없는 테스트 작업(${tasks.join(", ")})은 모듈 전체를 돌림. ` +
                "동작이 바뀐 클래스의 테스트만 `--tests '<클래스>'`(계측은 `-Pandroid.testInstrumentationRunnerArguments.class=<클래스>`)로 실행하고, " +
                "주석·상수만 바꿨으면 컴파일만 확인. 사용자가 전체 실행을 요청했으면 명령 끝에 `# full-test`를 붙여 다시 실행(CLAUDE.md git·테스트 절).",
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
