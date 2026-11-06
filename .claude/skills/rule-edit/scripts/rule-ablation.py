#!/usr/bin/env python3
"""규칙 문장 빼기 비교 도구. 규칙 문서에서 문장을 뺀 사본·여러 문서 묶음을 만들고, 같은 질의를 변형마다 돌려 판단이 갈리는지 보는 용도

`claude -p --safe-mode`는 저장소 CLAUDE.md·스킬·훅 없이 `--append-system-prompt-file`로 넘긴 사본만 실음. 그래서 매 세션
주입되는 CLAUDE.md에도 문장 하나를 뺀 비교군 구성 가능. 서브에이전트는 CLAUDE.md를 그대로 물려받아 비교군이 되지 못함.
도구를 끄는 것(`--tools ""`)은 응답이 저장소의 실제 규칙 파일을 읽어 비교군이 오염되는 것을 막는 장치이고, 저장소 밖 임시
디렉터리에서 돌리는 것(그래서 변형 경로는 절대 경로로 변환)은 저장소 안에서는 도구를 꺼도 모델이 "저장소를 찾아보겠다"는
쪽으로 기울어 답이 흐려지는 것을 피하는 장치
한계: git 저장소 밖 실행이라 저장소·브랜치 맥락에서 발동하는 규칙은 실제 세션과 다르게 판단될 수 있음

기본값은 실제 작업 모델(opus)로 변형마다 4회. 다른 모델로 재면 그 모델에만 필요한 규칙이 남고, 2회씩은 효과 없는 규칙도
1/16 확률로 "있는 쪽만 기대대로" 갈려 우연과 구분 불가(4회씩이면 1/256)
질의 파일에는 `기대:`로 시작하는 줄이 하나 이상 필요. 어떤 답이면 갈린 것인지를 결과를 보기 전에 적어 두지 않으면 해석이
남기는 쪽으로 기울기 때문. 그 줄은 모델에 보내지 않고 결과 머리에만 실음

사용:
    rule-ablation.py drop <문서> <시작-끝> [...] --out <사본>
    rule-ablation.py bundle <파일> [...] [--note <문장>] --out <묶음>
    rule-ablation.py run --variant <이름>=<파일> [...] --query <질의 파일> [--repeat 4] [--model opus] [--out <결과.md>] [--dry-run]
"""

import argparse
import functools
import os
import shlex
import subprocess
import sys
import tempfile
from pathlib import Path

# 한 회차 상한. 응답이 멈춘 회차가 나머지 비교를 막지 않도록 끊고 그 회차는 실패로 기록
RUN_TIMEOUT_SECONDS = 600
FAILURE_MARK = "[실행 실패"
# 결과 마크다운에서 응답을 감싸는 울타리. 응답 안의 ``` 코드 블록·## 머리와 섞이지 않게 백틱 넷 사용
ANSWER_FENCE = "````"
EXPECTATION_PREFIX = "기대:"


def split_expectations(text):
    """(기대 줄 목록, 모델에 보낼 질의). 기대 줄은 갈림 기준이라 모델이 보면 답이 그쪽으로 끌림"""
    expectations, prompt = [], []
    for line in text.split("\n"):
        (expectations if line.strip().startswith(EXPECTATION_PREFIX) else prompt).append(line)
    return [line.strip() for line in expectations], "\n".join(prompt).strip()


def failed_variants(results):
    """실패한 회차가 하나라도 있는 변형. 유효 회차가 `--repeat`보다 적으면 우연과 구분하는 근거(4회씩 1/256)가 서지 않음"""
    outcomes = {}
    for name, _attempt, output in results:
        outcomes.setdefault(name, []).append(output.startswith(FAILURE_MARK))
    return [name for name, failed in outcomes.items() if any(failed)]


def drop_lines(text, ranges):
    lines = text.split("\n")
    last = len(lines) - 1 if text.endswith("\n") else len(lines)  # 끝 줄바꿈 뒤 빈 원소는 문서의 줄이 아님
    removed = set()
    for start, end in ranges:
        if start < 1 or end < start or end > last:
            raise ValueError(f"줄 범위가 문서 밖: {start}-{end} (문서 {last}줄)")
        removed.update(range(start, end + 1))
    return "\n".join(line for number, line in enumerate(lines, start=1) if number not in removed)


def parse_range(value):
    start, dash, end = value.partition("-")
    if not start.isdigit() or (dash and not end.isdigit()) or int(start) < 1 or int(end or start) < int(start):
        raise argparse.ArgumentTypeError(f"줄 범위는 <시작> 또는 <시작>-<끝>(1 이상, 시작 ≤ 끝) 꼴이어야 함: {value!r}")
    return int(start), int(end or start)


def parse_variant(value):
    name, separator, path = value.partition("=")
    if not (name and separator and path):
        raise argparse.ArgumentTypeError(f"--variant는 <이름>=<파일> 꼴이어야 함: {value!r}")
    return name, os.path.abspath(path)


def bundle(paths, note, read):
    parts = [f"<!-- {path} -->\n{read(path)}" for path in paths]
    if note:
        parts.append(note)
    return "\n\n".join(parts)


def build_command(variant_path, query, model):
    # 질의가 `-`로 시작해도 옵션으로 읽히지 않도록 `--` 뒤에 배치
    return ["claude", "-p", "--safe-mode", "--tools", "", "--model", model,
            "--append-system-prompt-file", variant_path, "--", query]


def run_variants(variants, query, repeat, model, runner):
    results = []
    for name, path in variants:
        for attempt in range(1, repeat + 1):
            results.append((name, attempt, runner(build_command(path, query, model))))
    return results


@functools.lru_cache(maxsize=None)
def neutral_directory():
    # 객체째 캐시. 이름만 돌려주면 객체가 회수되며 디렉터리가 바로 지워지고, 객체는 인터프리터 종료 때 삭제
    return tempfile.TemporaryDirectory(prefix="rule-ablation-")


def subprocess_runner(argv):
    # 표준 입력 차단. 파이프로 들어온 입력은 claude가 질의와 함께 메시지로 읽음
    try:
        completed = subprocess.run(argv, capture_output=True, text=True, timeout=RUN_TIMEOUT_SECONDS,
                                   cwd=neutral_directory().name, stdin=subprocess.DEVNULL)
    except subprocess.TimeoutExpired:
        return f"{FAILURE_MARK}: 시간 초과 {RUN_TIMEOUT_SECONDS}초]"
    except FileNotFoundError:
        return f"{FAILURE_MARK}: {argv[0]} 명령 없음]"
    if completed.returncode != 0:
        return f"{FAILURE_MARK}: exit={completed.returncode}] {completed.stderr.strip()}"
    return completed.stdout.strip() or f"{FAILURE_MARK}: 빈 응답]"


def render(results, query, expectations=(), failed=()):
    lines = ["# 변형 비교", ""]
    if failed:
        # 결과 파일만 다시 읽는 쪽도 무효를 알도록 머리에 표시
        lines += [f"판정 무효: 실패한 회차가 있는 변형 {', '.join(failed)}(유효 회차가 --repeat보다 적음)", ""]
    lines += ["## 기대(질의에서 뺀 갈림 기준)", ""]
    lines += [f"- {line}" for line in expectations] or ["- (없음)"]
    lines += ["", "## 질의", "", ANSWER_FENCE, query, ANSWER_FENCE, ""]
    for name, attempt, output in results:
        lines += [f"## {name} #{attempt}", "", ANSWER_FENCE, output, ANSWER_FENCE, ""]
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="규칙 문장 빼기 비교")
    commands = parser.add_subparsers(dest="command", required=True)
    drop = commands.add_parser("drop")
    drop.add_argument("document")
    drop.add_argument("ranges", nargs="+", type=parse_range)
    drop.add_argument("--out", required=True)
    joined = commands.add_parser("bundle")
    joined.add_argument("paths", nargs="+")
    joined.add_argument("--note")
    joined.add_argument("--out", required=True)
    run = commands.add_parser("run")
    run.add_argument("--variant", action="append", required=True, type=parse_variant, help="이름=파일")
    run.add_argument("--query", required=True)
    run.add_argument("--repeat", type=int, default=4)
    run.add_argument("--model", default="opus")
    run.add_argument("--out")
    run.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    if args.command == "drop":
        try:
            dropped = drop_lines(Path(args.document).read_text(encoding="utf-8"), args.ranges)
        except ValueError as error:
            parser.error(str(error))
        Path(args.out).write_text(dropped, encoding="utf-8")
        return 0
    if args.command == "bundle":
        Path(args.out).write_text(bundle(args.paths, args.note, lambda path: Path(path).read_text(encoding="utf-8")), encoding="utf-8")
        return 0
    expectations, query = split_expectations(Path(args.query).read_text(encoding="utf-8"))
    if not expectations:
        parser.error(f"질의 파일에 `{EXPECTATION_PREFIX}` 줄이 없음. 어떤 답이면 갈린 것인지를 실행 전에 기록")
    if args.dry_run:
        for _, path in args.variant:
            print('cd "$(mktemp -d)" && ' + shlex.join(build_command(path, query, args.model)))
        return 0
    results = run_variants(args.variant, query, args.repeat, args.model, subprocess_runner)
    failed = failed_variants(results)
    rendered = render(results, query, expectations, failed)
    if args.out:
        Path(args.out).write_text(rendered, encoding="utf-8")
    else:
        print(rendered)
    if failed:
        print(f"실패한 회차가 있는 변형: {', '.join(failed)}. 유효 회차가 --repeat보다 적어 판정에 쓰지 않음", file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
