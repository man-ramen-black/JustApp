#!/usr/bin/env bash
# 프로젝트 규칙이 실리지 않은 외부 리뷰어 하나 실행
#
# --safe-mode가 CLAUDE.md·스킬·플러그인·훅·MCP를 전부 비활성화하므로 cwd가 프로젝트여도
# 프로젝트 규칙이 프롬프트에 실리지 않음(실측 대조: safe-mode는 RULES=NONE/SKILLS=0,
# 일반 실행은 RULES=# 규칙). 이 플래그가 이 스킬의 유일한 격리 수단이라
# 호출부에서 claude를 직접 부르지 말고 항상 이 스크립트를 경유
#
# 도구는 읽기 전용(Read,Grep,Glob)만 제공. 규칙을 모르는 리뷰어가 코드를 고치면 규칙
# 위반을 새로 만들고, Bash를 주면 bypassPermissions와 합쳐져 쓰기 경로가 열림
#
# 사용법:
#   bash spawn_reviewer.sh --lens <skeptic|newhire|minimalist|premise> \
#                          --bundle <자료 묶음 디렉터리> \
#                          --out <산출 md 경로> \
#                          [--repo <검토 대상 저장소>] [--model <모델>]
#                          [--lens-file <렌즈 정의 md>]
#
# --repo 생략 시 호출한 셸의 작업 디렉터리에서 저장소 탐색
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LENS_FILE="$SCRIPT_DIR/../references/reviewer-lenses.md"
LENS=""
BUNDLE=""
OUT=""
MODEL=""
REPO=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --lens) LENS="$2"; shift 2 ;;
        --bundle) BUNDLE="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        --model) MODEL="$2"; shift 2 ;;
        --repo) REPO="$2"; shift 2 ;;
        --lens-file) LENS_FILE="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [[ -z "$LENS" || -z "$BUNDLE" || -z "$OUT" ]]; then
    echo "usage: spawn_reviewer.sh --lens <name> --bundle <dir> --out <file> [--model <model>]" >&2
    exit 2
fi

if [[ ! -d "$BUNDLE" ]]; then
    echo "bundle directory not found: $BUNDLE" >&2
    exit 2
fi

# 렌즈 정의 추출: <!-- lens:<이름> --> 마커부터 다음 마커 직전까지
extract_lens() {
    awk -v name="$1" '
        $0 ~ "^<!-- lens:" name " -->$" { capture = 1; next }
        capture && /^<!-- lens:/ { exit }
        capture { print }
    ' "$LENS_FILE"
}

COMMON="$(extract_lens common)"
LENS_BODY="$(extract_lens "$LENS")"

if [[ -z "${LENS_BODY// /}" ]]; then
    echo "lens not found in $LENS_FILE: $LENS" >&2
    exit 2
fi

BUNDLE_ABS="$(cd "$BUNDLE" && pwd)"

# 검토 대상 저장소는 호출한 셸의 작업 디렉터리 기준. 워크트리에서 부르면 그 워크트리가 잡혀
# 리뷰어가 워크트리의 코드를 읽음. 다른 경로는 --repo로 지정
if [[ -z "$REPO" ]]; then
    REPO="$(git rev-parse --show-toplevel 2>/dev/null || true)"
fi
if [[ -z "$REPO" ]]; then
    echo "git 저장소 밖에서 호출됨. --repo <경로>로 검토 대상 저장소 지정 필요" >&2
    exit 2
fi
REPO_ROOT="$(cd "$REPO" && pwd)"

PROMPT="$COMMON
$LENS_BODY

## The material

The bundle directory is \`$BUNDLE_ABS\`. Read \`$BUNDLE_ABS/intent.md\` first — it states
the intent and the target paths. If \`$BUNDLE_ABS/target.diff\` exists, that diff is the
change under review. The working tree is at \`$REPO_ROOT\` and you can read any file in it."

mkdir -p "$(dirname "$OUT")"

CLAUDE_ARGS=(
    -p
    --safe-mode
    --tools "Read,Grep,Glob"
    --permission-mode bypassPermissions
    --add-dir "$BUNDLE_ABS"
)
if [[ -n "$MODEL" ]]; then
    CLAUDE_ARGS+=(--model "$MODEL")
fi

echo "[outside-review] lens=$LENS cwd=$REPO_ROOT flags=${CLAUDE_ARGS[*]}" >&2

# settings.json 권한 규칙 경고가 stdout 앞머리에 섞여 나와(실측) 제거. 남겨 두면 산출물 첫 줄이
# 경고로 시작해 종합 단계에서 지적으로 오독됨
# 프롬프트는 표준 입력으로 전달. --add-dir가 가변 인자라(`<directories...>`) 뒤에 붙인 프롬프트
# 인자를 삼켜 "Input must be provided" 오류로 빈 산출이 나옴(실측)
cd "$REPO_ROOT"
printf '%s' "$PROMPT" | claude "${CLAUDE_ARGS[@]}" 2>/dev/null \
    | grep -v '^Permission allow rule' > "$OUT" || true

if [[ ! -s "$OUT" ]]; then
    echo "[outside-review] FAILED lens=$LENS — 산출이 비었음. 판정문에 실패로 기록 필요" >&2
    exit 1
fi

echo "[outside-review] done lens=$LENS out=$OUT ($(wc -l < "$OUT") lines)" >&2
