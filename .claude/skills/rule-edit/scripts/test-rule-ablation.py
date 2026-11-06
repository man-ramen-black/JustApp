#!/usr/bin/env python3
"""rule-ablation.py 회귀 하네스(의존성 없음, 전량 통과 시에만 exit 0)

  python3 .claude/skills/rule-edit/scripts/test-rule-ablation.py

`claude -p` 실행은 가짜 실행기·가짜 `subprocess.run`으로 바꿔 끼워 명령 구성·실행 인자·결과 배열만 확인
(실제 실행은 비용이 들고 응답이 매번 다름)
"""
import argparse
import contextlib
import importlib.util
import io
import os
import shutil
import subprocess
import sys
import tempfile

# 파일 이름이 kebab-case라 import 문 대신 경로로 적재(스킬 디렉터리에 __pycache__가 남지 않게 바이트코드 기록 끔)
sys.dont_write_bytecode = True
module_spec = importlib.util.spec_from_file_location(
    "rule_ablation", os.path.join(os.path.dirname(os.path.abspath(__file__)), "rule-ablation.py"))
target = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(target)

failures = []


def check(name, actual, expected):
    if actual != expected:
        failures.append(name)
        print(f"  실패 {name}: {actual!r} != {expected!r}")
    else:
        print(f"  통과 {name}")


def raises(function, *args):
    try:
        function(*args)
    except (ValueError, argparse.ArgumentTypeError):
        return "거부"
    except SystemExit as error:
        return f"종료 {error.code}"
    return "거부 안 함"


TEXT = "a\nb\nc\nd\ne"
check("한 범위", target.drop_lines(TEXT, [(2, 3)]), "a\nd\ne")
check("여러 범위", target.drop_lines(TEXT, [(1, 1), (4, 5)]), "b\nc")
check("겹치는 범위", target.drop_lines(TEXT, [(2, 3), (3, 4)]), "a\ne")
check("범위 밖은 거부", raises(target.drop_lines, TEXT, [(4, 9)]), "거부")
check("끝 줄바꿈 뒤 빈 원소는 거부", raises(target.drop_lines, TEXT + "\n", [(6, 6)]), "거부")
check("끝 줄바꿈 보존", target.drop_lines(TEXT + "\n", [(5, 5)]), "a\nb\nc\nd\n")
check("한 줄 범위 표기", target.parse_range("5"), (5, 5))
check("두 끝 범위 표기", target.parse_range("3-7"), (3, 7))
for malformed in ["3-", "x", "-3", "7-3", "0", "3-x"]:
    check(f"잘못된 범위 거부 {malformed!r}", raises(target.parse_range, malformed), "거부")

files = {"x.md": "X", "y.md": "Y"}
check("묶음 순서·머리·덧붙임",
      target.bundle(["x.md", "y.md"], "배정 문장", files.__getitem__),
      "<!-- x.md -->\nX\n\n<!-- y.md -->\nY\n\n배정 문장")

command = target.build_command("/tmp/v.md", "-질의", "sonnet")
check("명령 머리", command[:3], ["claude", "-p", "--safe-mode"])
check("도구 끔", command[command.index("--tools") + 1], "")
check("사본 지정", command[command.index("--append-system-prompt-file") + 1], "/tmp/v.md")
check("질의는 -- 뒤 맨 끝", command[-2:], ["--", "-질의"])

calls = []
results = target.run_variants([("full", "/f"), ("minus", "/m")], "q", 2, "sonnet",
                              lambda argv: calls.append(argv) or f"답{len(calls)}")
check("변형×반복 순서", [(name, attempt) for name, attempt, _ in results],
      [("full", 1), ("full", 2), ("minus", 1), ("minus", 2)])
check("실행 횟수", len(calls), 4)
rendered = target.render(results, "q", ["기대: full만 A"])
check("결과 절 머리", "## minus #2" in rendered and "## full #1" in rendered, True)
check("기대 줄은 결과 머리에", "- 기대: full만 A" in rendered, True)
check("기대 줄은 질의에서 뺌", target.split_expectations("기대: A\n질문\n 기대: B"), (["기대: A", "기대: B"], "질문"))
check("한 변형 전부 실패면 그 변형", target.failed_variants(
    [("full", 1, "답"), ("minus", 1, f"{target.FAILURE_MARK}: x]"), ("minus", 2, f"{target.FAILURE_MARK}: y]")]),
    ["minus"])
check("일부 회차 실패도 그 변형", target.failed_variants([("full", 1, "답"), ("full", 2, f"{target.FAILURE_MARK}: x]")]), ["full"])
check("실패 없으면 없음", target.failed_variants([("full", 1, "답"), ("minus", 1, "답")]), [])
check("응답 울타리", "## full #1\n\n````\n답1\n````" in rendered, True)

repository = os.path.realpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", ".."))
neutral = os.path.realpath(target.neutral_directory().name)
check("실행 위치는 저장소 밖", os.path.commonpath([neutral, repository]) != repository and os.path.isdir(neutral), True)
check("실행 위치는 한 번만 만듦", target.neutral_directory(), target.neutral_directory())

original_run = subprocess.run
captured = {}


def fake_run(argv, **kwargs):
    captured.update(kwargs)
    return subprocess.CompletedProcess(argv, 0, stdout="", stderr="")


subprocess.run = fake_run
check("빈 응답은 실패", target.subprocess_runner(["claude"]).startswith(target.FAILURE_MARK), True)
check("실행기는 중립 위치에서 실행", captured.get("cwd"), target.neutral_directory().name)
check("실행기는 표준 입력 끊음", captured.get("stdin"), subprocess.DEVNULL)


def missing_command(argv, **kwargs):
    raise FileNotFoundError(argv[0])


subprocess.run = missing_command
check("명령 없음은 실패 문자열", target.subprocess_runner(["claude"]).startswith(target.FAILURE_MARK), True)
subprocess.run = original_run

workspace = tempfile.mkdtemp(prefix="rule-ablation-check-")
source = os.path.join(workspace, "doc.md")
open(source, "w", encoding="utf-8").write(TEXT)
out = os.path.join(workspace, "out.md")
check("drop 종료 코드", target.main(["drop", source, "2-3", "--out", out]), 0)
check("drop 산출", open(out, encoding="utf-8").read(), "a\nd\ne")
with contextlib.redirect_stderr(io.StringIO()):
    check("drop 잘못된 범위는 argparse 오류", raises(target.main, ["drop", source, "3-", "--out", out]), "종료 2")
    check("drop 문서 밖 범위는 argparse 오류", raises(target.main, ["drop", source, "4-9", "--out", out]), "종료 2")
query = os.path.join(workspace, "q.md")
open(query, "w", encoding="utf-8").write("기대: a만 X\n질의")
no_expectation = os.path.join(workspace, "q0.md")
open(no_expectation, "w", encoding="utf-8").write("질의")
with contextlib.redirect_stderr(io.StringIO()):
    check("기대 줄 없는 질의는 argparse 오류",
          raises(target.main, ["run", "--variant", f"a={source}", "--query", no_expectation, "--dry-run"]), "종료 2")
dry_run_output = io.StringIO()
with contextlib.redirect_stdout(dry_run_output):
    dry_run_code = target.main(["run", "--variant", f"a={source}", "--query", query, "--repeat", "1", "--dry-run"])
check("dry-run은 실행하지 않음", dry_run_code, 0)
check("dry-run은 저장소 밖 위치를 앞에 붙임", dry_run_output.getvalue().startswith('cd "$(mktemp -d)" && claude'), True)
check("기본 모델은 opus", "--model opus" in dry_run_output.getvalue(), True)
check("모델에는 기대 줄을 보내지 않음", "a만 X" in dry_run_output.getvalue(), False)
with contextlib.redirect_stderr(io.StringIO()):
    check("= 없는 변형은 argparse 오류",
          raises(target.main, ["run", "--variant", source, "--query", query]), "종료 2")

original_runner = target.subprocess_runner
runner_calls = []
target.subprocess_runner = lambda argv: runner_calls.append(argv) or f"{target.FAILURE_MARK}: 가짜]"
result_path = os.path.join(workspace, "result.md")
run_argv = ["run", "--variant", f"a={os.path.relpath(source)}", "--query", query, "--repeat", "1", "--out", result_path]
with contextlib.redirect_stderr(io.StringIO()):
    check("전부 실패면 exit 1", target.main(run_argv), 1)
check("상대 변형 경로는 절대 경로로",
      runner_calls[0][runner_calls[0].index("--append-system-prompt-file") + 1], os.path.abspath(source))
target.subprocess_runner = lambda argv: "답"
check("모든 회차가 돌면 exit 0", target.main(run_argv), 0)
answers = iter(["답", f"{target.FAILURE_MARK}: 시간 초과]"])
target.subprocess_runner = lambda argv: next(answers)
with contextlib.redirect_stderr(io.StringIO()):
    check("한 회차라도 실패면 exit 1", target.main(run_argv[:-4] + ["--repeat", "2", "--out", result_path]), 1)
check("실패한 회차가 있으면 결과 머리에 무효 표시", "판정 무효: 실패한 회차가 있는 변형 a" in open(result_path, encoding="utf-8").read(), True)
target.subprocess_runner = original_runner
shutil.rmtree(workspace)

print(f"\n실패 {len(failures)}건" if failures else "\n전량 통과")
sys.exit(1 if failures else 0)
