#!/usr/bin/env python3
"""응답 언어 확인 훅. 사용자에게 보이는 응답 본문이 영어 위주이면 한국어로 바로잡게 하는 용도

한국어 응답 규칙이 CLAUDE.md·출력 스타일·언어 설정에 모두 있어도, 긴 작업 중 도구 호출 사이의 안내와
최종 보고가 영어로 나가는 일이 반복됨(한 세션에서 최종 보고 2건·중간 안내 다수, 사용자 교정 2회).
문구 추가로는 막히지 않아 위반 시점에 판정을 둔 구조

- Stop: 마지막 도구 결과 뒤의 텍스트(최종 응답)가 영어 위주면 종료 차단(decision=block) 후 재작성 요청
- PostToolUse: 그 도구 호출 바로 앞의 텍스트(중간 안내)가 영어 위주면 다음 응답 전에 알림 추가
  (additionalContext). 이미 화면에 나간 안내라 차단은 하지 않음

판정은 글자가 아닌 단어 단위. 한글이 섞인 단어(`NoticeDispatcher와`)는 한국어 단어, 영문만 있는 단어는
영어 단어. 글자 단위로 세면 백틱 없이 쓴 식별자가 많은 한국어 문장이 영어로 잡힘. 코드 블록·인라인
코드·URL·경로는 세기 전에 제거

사용자 레벨(~/.claude/hooks/guard-korean-reply.py)에 같은 훅이 있는 기계에서는 그쪽이 돌므로 이 사본은
중복 판정을 피하려고 건너뜀

    python3 guard-korean-reply.py --measure <transcript.jsonl>   # 텍스트 묶음별 단어 수·판정(임계값 보정용)
"""
import json
import os
import re
import sys
import time

# 영어 위주 판정 기준: 영어 단어가 하한 이상이고 한국어 단어가 영어 단어의 이 비율 미만
# 하한 보정(한 세션 트랜스크립트 43묶음): 영어로 나간 응답은 최종 보고 330·130단어, 중간 안내
# 7~40단어, 한국어 응답의 영어 단어는 최대 4~5개. 중간 안내는 짧아 하한을 낮게 설정
FINAL_MIN_LATIN_WORDS = 15
INTERIM_MIN_LATIN_WORDS = 6
MAX_HANGUL_RATIO = 0.5

FENCED = re.compile(r"```.*?```", re.S)
INLINE = re.compile(r"`[^`\n]*`")
URL = re.compile(r"https?://\S+")
PATH = re.compile(r"\S*[/\\]\S+")
HANGUL = re.compile(r"[가-힣]")
LATIN = re.compile(r"[A-Za-z]")

# 응답 레코드가 트랜스크립트에 아직 기록되지 않았을 수 있어 짧게 다시 읽는 횟수·간격
READ_RETRIES = 6
READ_INTERVAL_SEC = 0.5

# 트랜스크립트 끝에서 읽는 양. 판정 대상은 마지막 몇 레코드라 충분하며, 끝에서 못 찾으면 판정 없이 통과
TAIL_BYTES = 2_000_000

USER_LEVEL_COPY = os.path.expanduser("~/.claude/hooks/guard-korean-reply.py")


def load_records(path, tail_bytes=None):
    """트랜스크립트 레코드 목록. [tail_bytes]가 있으면 끝에서 그만큼만 읽음
    (판정 대상은 늘 끝에 있고, 긴 세션의 트랜스크립트는 수십 MB까지 커져 전체 읽기는 매 호출 지연)"""
    records = []
    with open(path, "rb") as raw:
        if tail_bytes is not None:
            raw.seek(0, 2)
            size = raw.tell()
            if size > tail_bytes:
                raw.seek(size - tail_bytes)
                raw.readline()  # 잘린 첫 줄 버림
            else:
                raw.seek(0)
        data = raw.read().decode("utf-8", errors="replace")
    for line in data.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return records


def content_blocks(record):
    content = (record.get("message") or {}).get("content")
    if isinstance(content, str):
        return [{"type": "text", "text": content}]
    return [block for block in content if isinstance(block, dict)] if isinstance(content, list) else []


def texts_of(record):
    if record.get("type") != "assistant":
        return []
    return [block.get("text", "") for block in content_blocks(record) if block.get("type") == "text"]


def has_tool_use(record, tool_use_id=None):
    if record.get("type") != "assistant":
        return False
    return any(block.get("type") == "tool_use" and (tool_use_id is None or block.get("id") == tool_use_id)
               for block in content_blocks(record))


def final_reply(records):
    """마지막 사용자·도구 결과 레코드 뒤의 어시스턴트 텍스트"""
    texts = []
    for record in reversed(records):
        if record.get("type") == "user":
            break
        texts = texts_of(record) + texts
    return "\n".join(text for text in texts if text.strip())


def text_before_tool_use(records, tool_use_id):
    """그 도구 호출 바로 앞의 어시스턴트 텍스트. 사이에 다른 도구 호출이 있으면(병렬 호출의 두 번째부터)
    빈 값이라 같은 안내에 알림이 거듭 붙지 않음"""
    index = next((i for i in range(len(records) - 1, -1, -1) if has_tool_use(records[i], tool_use_id)), None)
    if index is None:
        return None  # 그 호출 레코드가 아직 기록 전이라 재시도 대상
    texts = []
    for record in reversed(records[:index]):
        if record.get("type") == "user" or has_tool_use(record):
            break
        texts = texts_of(record) + texts
    # 같은 레코드 안에서 도구 호출보다 앞에 놓인 텍스트도 그 안내에 포함
    for block in content_blocks(records[index]):
        if block.get("type") == "tool_use":
            break
        if block.get("type") == "text":
            texts.append(block.get("text", ""))
    return "\n".join(text for text in texts if text.strip())


def word_counts(text):
    body = PATH.sub(" ", URL.sub(" ", INLINE.sub(" ", FENCED.sub(" ", text))))
    hangul_words = latin_words = 0
    for token in body.split():
        if HANGUL.search(token):
            hangul_words += 1
        elif LATIN.search(token):
            latin_words += 1
    return hangul_words, latin_words


def english_dominant(text, min_latin_words):
    hangul_words, latin_words = word_counts(text)
    dominant = latin_words >= min_latin_words and hangul_words < latin_words * MAX_HANGUL_RATIO
    return dominant, hangul_words, latin_words


def measure(path):
    group = []
    for record in load_records(path) + [{"type": "user"}]:
        if record.get("type") == "user" or has_tool_use(record):
            if record.get("type") != "user":
                group.extend(texts_of(record))
            text = "\n".join(t for t in group if t.strip())
            if text:
                interim = record.get("type") != "user"
                dominant, hangul_words, latin_words = english_dominant(
                    text, INTERIM_MIN_LATIN_WORDS if interim else FINAL_MIN_LATIN_WORDS)
                head = text.strip().splitlines()[0][:60]
                kind = "interim" if interim else "final  "
                print(f"{'FLAG' if dominant else 'pass'} {kind} ko={hangul_words:4d} en={latin_words:4d} | {head}")
            group = []
            continue
        group.extend(texts_of(record))


def read_with_retry(path, extract):
    """[extract]가 None(판정 대상 레코드가 아직 기록 전)을 돌려줄 때만 재시도. 빈 문자열은 대상은 있으나
    텍스트가 없다는 뜻이라 즉시 종료(안내 없이 부르는 도구 호출이 대부분이라 대기하면 호출마다 지연 발생)"""
    for _ in range(READ_RETRIES):
        try:
            text = extract(load_records(path, TAIL_BYTES))
        except OSError:
            return ""
        if text is not None:
            return text
        time.sleep(READ_INTERVAL_SEC)
    return ""


def is_user_level_copy_active():
    """이 파일이 사용자 레벨 사본이 아니면서 사용자 레벨 사본이 존재하는지 여부"""
    if not os.path.isfile(USER_LEVEL_COPY):
        return False
    return os.path.realpath(__file__) != os.path.realpath(USER_LEVEL_COPY)


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "--measure":
        measure(sys.argv[2])
        return 0
    if is_user_level_copy_active():
        return 0
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0
    path = payload.get("transcript_path")
    if not path:
        return 0
    event = payload.get("hook_event_name")
    if event == "Stop":
        # 이 훅이 막아 다시 쓴 응답을 또 막으면 끝나지 않으므로 두 번째 종료는 통과
        if payload.get("stop_hook_active"):
            return 0
        # 최종 응답이 비어 보이면 아직 기록 전이라 재시도(턴마다 한 번이라 지연 부담 없음)
        text = read_with_retry(path, lambda records: final_reply(records) or None)
        dominant, hangul_words, latin_words = english_dominant(text, FINAL_MIN_LATIN_WORDS)
        if text and dominant:
            reason = (
                f"마지막 응답 본문이 영어로 작성됐다(코드·경로 제외 한국어 단어 {hangul_words}개, 영어 단어 "
                f"{latin_words}개). 사용자에게 보이는 응답은 한국어로 쓴다(CLAUDE.md 언어 규칙). 같은 내용을 "
                "한국어로 다시 써서 응답을 마무리하라. 코드 식별자·경로·명령은 그대로 둔다."
            )
            print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))
        return 0
    if event == "PostToolUse":
        tool_use_id = payload.get("tool_use_id")
        text = read_with_retry(path, lambda records: text_before_tool_use(records, tool_use_id))
        dominant, hangul_words, latin_words = english_dominant(text, INTERIM_MIN_LATIN_WORDS)
        if text and dominant:
            context = (
                f"방금 도구 호출 앞의 안내가 영어였다(영어 단어 {latin_words}개). 사용자에게 보이는 텍스트는 "
                "중간 안내까지 모두 한국어로 쓴다(CLAUDE.md 언어 규칙). 다음 안내부터 한국어로 쓴다."
            )
            print(json.dumps({"hookSpecificOutput": {"hookEventName": "PostToolUse",
                                                     "additionalContext": context}}, ensure_ascii=False))
        return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
