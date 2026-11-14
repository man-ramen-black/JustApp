#!/usr/bin/env python3
"""기기 사용 기록 쌓기와 조회 스크립트

폰 앱이 디스코드 전용 채널로 보낸 암호화 첨부 파일(usage-log-N.bin)을 풀어 SQLite에 쌓고,
비서가 요청받았을 때 필요한 범위만 사람이 읽기 좋은 줄로 꺼내 주는 명령줄 도구.
이 파일 하나와 `cryptography` 패키지(Windows는 py -m pip install cryptography)만 있으면 동작함.
출력은 실행 환경과 관계없이 UTF-8.

첨부 파일 형식
    앞 12바이트가 nonce, 나머지가 AES-256-GCM 암호문(끝 16바이트가 인증 태그).
    복호화한 결과는 gzip으로 압축된 JSON Lines이며, 첫 줄은 묶음 정보(meta),
    이어지는 줄마다 기록 하나. 시각은 모두 epoch 밀리초.

환경 변수
    USAGE_LOG_KEY                     Base64로 인코딩한 32바이트 키 (ingest, decrypt에 필요)
    USAGE_LOG_DB                      SQLite 파일 위치 (기본 ~/.usage_log/usage_log.sqlite3)
    USAGE_LOG_HOME_DEBOUNCE_MINUTES   집 Wi-Fi 해제 뒤 이 시간 안에 다시 연결되면 떠난 것으로 보지 않음 (기본 3)
    USAGE_LOG_APP                     쌓을 묶음을 보낸 앱 패키지 (기본 com.black.app). 다른 앱의 묶음은 거부

명령
    ingest <첨부 파일>                          복호화해 DB에 쌓기. 출력은 "새 기록 N건, 중복 M건"뿐.
                                                형식 버전(meta v)이 1이 아니거나 보낸 앱이 USAGE_LOG_APP과 다르면 거부
    decrypt <첨부 파일>                         복호화한 평문 출력 (문제 확인용)
    summary <날짜>                              하루 합계와 마지막 기록 시각 (기간은 받지 않음)
    timeline <날짜> [시작 HH:MM] [끝 HH:MM]     시간대별 한 줄 (기간은 받지 않음, 시각은 00:00~24:00)
    notifications <날짜|날짜~날짜> [--app 이름] [--search 단어]
    notifications --new                         지난 --new 뒤에 쌓인 알림을 쌓인 순서로 300건까지 골라 시각 순으로
                                                출력. 날짜, --app, --search와 함께 쓸 수 없음. 남은 알림이 있으면
                                                끝에 "외 N줄"이 붙고 다음 --new가 이어서 돌려줌
    home <날짜|날짜~날짜>                       집에 있던 구간과 집 여부를 알 수 없는 구간, 마지막 기록 시각.
                                                추정 기록이 만든 경계 시각에는 "(추정)"
    apps <날짜|날짜~날짜> [--app 이름]          앱별 사용 시간 합계와 날짜별 추이
    sql "<SELECT 문>"                           읽기 전용 조회 (SELECT 하나만, 최대 200행, 5초 넘으면 중단).
                                                값 안의 줄바꿈은 ⏎, 탭은 공백으로 바꿔 열 구분(탭)을 지킴

날짜는 YYYY-MM-DD. 하루는 기록이 들어온 묶음이 가진 시간대 값(tzOffsetMinutes)으로 따진 현지 날짜.
출력 줄이 300줄을 넘으면 앞 300줄과 "외 N줄"만 출력함.

DB 테이블
    records        기록 한 건당 한 행. id가 기본키이며 같은 id는 한 번만 들어감.
                   t, start_t, end_t는 epoch 밀리초, local_t, local_start, local_end는 현지 시각으로 옮긴 같은 값,
                   tz_offset_minutes는 그 기록이 속한 묶음의 시간대 값.
    read_position  notifications --new가 마지막으로 출력한 알림 records의 rowid. 출력을 마친 뒤에만 옮김.
"""

import argparse
import base64
import binascii
import datetime
import gzip
import json
import math
import os
import pathlib
import re
import sqlite3
import sys
import time
import zlib

try:
    from cryptography.exceptions import InvalidTag
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:  # 키 없이 조회만 할 때는 없어도 됨
    AESGCM = None
    InvalidTag = None

# 이번 실행에서 연 DB 연결 (main이 끝날 때 한꺼번에 닫음)
OPEN_CONNECTIONS = []

MAX_OUTPUT_LINES = 300
MAX_SQL_ROWS = 200
SQL_TIME_LIMIT_SECONDS = 5.0
NONCE_BYTES = 12
KEY_BYTES = 32
SUPPORTED_FORMAT_VERSION = 1
DEFAULT_APP_PACKAGE = "com.black.app"
DEFAULT_DB_PATH = "~/.usage_log/usage_log.sqlite3"
DEFAULT_HOME_DEBOUNCE_MINUTES = 3.0

MINUTE_MS = 60_000
HOUR_MS = 60 * MINUTE_MS
DAY_MS = 24 * HOUR_MS

GAP_CAUSE_LABELS = {
    "collection_off": "수집을 끈 기간",
    "usage_events_expired": "시스템 보관 기간이 지나 앱 사용·화면 기록 없음",
    "unsent_deleted": "보내기 전에 지운 기록",
}
# 앱 사용·화면 기록만 잃고 Wi-Fi 기록은 남아 집 여부를 계속 알 수 있는 gap 원인
HOME_KNOWN_GAP_CAUSES = ("usage_events_expired",)

# 집 여부 상태 (출력에 그대로 쓰는 글자)
HOME = "집"
AWAY = "밖"
UNKNOWN = "알 수 없음"

SCHEMA = """
CREATE TABLE IF NOT EXISTS records (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    t INTEGER NOT NULL,
    local_t INTEGER NOT NULL,
    tz_offset_minutes INTEGER NOT NULL,
    seq INTEGER,
    package_name TEXT,
    app_name TEXT,
    start_t INTEGER,
    end_t INTEGER,
    local_start INTEGER,
    local_end INTEGER,
    notification_key TEXT,
    title TEXT,
    text TEXT,
    category TEXT,
    ssid TEXT,
    home INTEGER,
    inferred INTEGER,
    reason TEXT,
    cause TEXT
);
CREATE INDEX IF NOT EXISTS records_type_local_t ON records (type, local_t);
CREATE INDEX IF NOT EXISTS records_notification_key ON records (notification_key);
CREATE TABLE IF NOT EXISTS read_position (
    name TEXT PRIMARY KEY,
    last_rowid INTEGER NOT NULL
);
"""

INSERT_SQL = """
INSERT OR IGNORE INTO records (
    id, type, t, local_t, tz_offset_minutes, seq, package_name, app_name, start_t, end_t,
    local_start, local_end, notification_key, title, text, category, ssid, home, inferred, reason, cause
) VALUES (
    :id, :type, :t, :local_t, :tz_offset_minutes, :seq, :package_name, :app_name, :start_t, :end_t,
    :local_start, :local_end, :notification_key, :title, :text, :category, :ssid, :home, :inferred, :reason, :cause
)
"""


class UsageLogError(Exception):
    """사용자에게 짧게 보여 줄 오류 (메시지에 키·기록 내용을 넣지 않음)"""


# ---------------------------------------------------------------- 복호화

def load_key():
    """환경 변수 USAGE_LOG_KEY의 Base64 문자열을 32바이트 키로 변환"""
    text = os.environ.get("USAGE_LOG_KEY", "").strip()
    if not text:
        raise UsageLogError("USAGE_LOG_KEY 환경 변수에 Base64 키를 넣어 주세요.")
    try:
        key = base64.b64decode(text, validate=True)
    except (binascii.Error, ValueError):
        raise UsageLogError("USAGE_LOG_KEY가 올바른 Base64 문자열이 아닙니다.") from None
    if len(key) != KEY_BYTES:
        raise UsageLogError("USAGE_LOG_KEY가 32바이트 키가 아닙니다.")
    return key


def decrypt_attachment(path):
    """첨부 파일을 복호화하고 gunzip한 평문(UTF-8 문자열) 반환"""
    if AESGCM is None:
        raise UsageLogError("cryptography 패키지가 필요합니다. pip install cryptography")
    key = load_key()
    try:
        with open(path, "rb") as file:
            payload = file.read()
    except OSError:
        raise UsageLogError("첨부 파일을 읽을 수 없습니다.") from None
    if len(payload) <= NONCE_BYTES + 16:
        raise UsageLogError("첨부 파일이 너무 짧아 올바른 기록 파일이 아닙니다.")
    try:
        compressed = AESGCM(key).decrypt(payload[:NONCE_BYTES], payload[NONCE_BYTES:], None)
    except InvalidTag:
        raise UsageLogError("복호화에 실패했습니다. 키가 다르거나 파일이 손상되었습니다.") from None
    try:
        return gzip.decompress(compressed).decode("utf-8")
    except (OSError, EOFError, zlib.error, UnicodeDecodeError):
        raise UsageLogError("복호화는 됐지만 압축 해제에 실패했습니다.") from None


# ---------------------------------------------------------------- DB

def database_path():
    return os.path.expanduser(os.environ.get("USAGE_LOG_DB", DEFAULT_DB_PATH))


def open_writable_database():
    """쓰기용 연결 (폴더와 테이블이 없으면 만듦)"""
    path = database_path()
    folder = os.path.dirname(path)
    if folder:
        os.makedirs(folder, exist_ok=True)
    connection = sqlite3.connect(path)
    OPEN_CONNECTIONS.append(connection)
    connection.executescript(SCHEMA)
    return connection


def open_readonly_database():
    """읽기 전용 연결"""
    path = database_path()
    if not os.path.exists(path):
        raise UsageLogError("기록 DB가 아직 없습니다. 먼저 ingest로 기록을 쌓아 주세요.")
    # as_uri가 %, #, 공백, 한글을 모두 퍼센트 인코딩하므로 경로 글자가 URI 문법으로 읽히지 않음
    uri = pathlib.Path(path).resolve().as_uri() + "?mode=ro"
    connection = sqlite3.connect(uri, uri=True)
    OPEN_CONNECTIONS.append(connection)
    connection.row_factory = sqlite3.Row
    return connection


def to_int_or_none(value):
    return None if value is None else int(value)


def to_flag_or_none(value):
    return None if value is None else (1 if value else 0)


def build_row(record, meta):
    """JSON 기록 한 줄과 묶음 meta를 records 행으로 변환"""
    offset_ms = int(meta["tzOffsetMinutes"]) * MINUTE_MS
    start = to_int_or_none(record.get("start"))
    end = to_int_or_none(record.get("end"))
    record_time = to_int_or_none(record.get("t"))
    if record_time is None:
        record_time = start
    if record_time is None:
        raise UsageLogError("시각이 없는 기록이 있어 쌓을 수 없습니다.")
    return {
        "id": str(record["id"]),
        "type": str(record["type"]),
        "t": record_time,
        "local_t": record_time + offset_ms,
        "tz_offset_minutes": int(meta["tzOffsetMinutes"]),
        "seq": to_int_or_none(meta.get("seq")),
        "package_name": record.get("package"),
        "app_name": record.get("appName"),
        "start_t": start,
        "end_t": end,
        "local_start": None if start is None else start + offset_ms,
        "local_end": None if end is None else end + offset_ms,
        "notification_key": record.get("key"),
        "title": record.get("title"),
        "text": record.get("text"),
        "category": record.get("category"),
        "ssid": record.get("ssid"),
        "home": to_flag_or_none(record.get("home")),
        "inferred": to_flag_or_none(record.get("inferred")),
        "reason": record.get("reason"),
        "cause": record.get("cause"),
    }


def parse_plain_text(plain_text):
    """평문(JSON Lines)을 meta와 기록 목록으로 분리"""
    lines = [line for line in plain_text.split("\n") if line.strip()]
    try:
        parsed = [json.loads(line) for line in lines]
    except json.JSONDecodeError:
        raise UsageLogError("복호화한 내용이 JSON Lines 형식이 아닙니다.") from None
    if not parsed or parsed[0].get("type") != "meta" or "tzOffsetMinutes" not in parsed[0]:
        raise UsageLogError("첫 줄에 묶음 정보(meta)가 없습니다.")
    for record in parsed[1:]:
        if "id" not in record or "type" not in record:
            raise UsageLogError("id나 type이 없는 기록이 있습니다.")
    return parsed[0], parsed[1:]


def expected_app_package():
    return os.environ.get("USAGE_LOG_APP", "").strip() or DEFAULT_APP_PACKAGE


def check_meta(meta):
    """쌓을 수 있는 묶음인지 meta로 확인

    형식 버전이 다르면 필드 뜻이 달라졌을 수 있고, 다른 앱(개발 앱 등)의 묶음이 섞이면 실제 기록이 오염되므로 거부.
    """
    if meta.get("v") != SUPPORTED_FORMAT_VERSION:
        raise UsageLogError("지원하지 않는 기록 형식 버전(v=%s)이라 쌓지 않습니다." % meta.get("v"))
    expected = expected_app_package()
    if meta.get("app") != expected:
        raise UsageLogError("보낸 앱(%s)이 USAGE_LOG_APP(%s)과 달라 쌓지 않습니다." % (meta.get("app"), expected))


def command_ingest(arguments):
    meta, records = parse_plain_text(decrypt_attachment(arguments.file))
    check_meta(meta)
    rows = [build_row(record, meta) for record in records]
    connection = open_writable_database()
    try:
        inserted = 0
        with connection:
            for row in rows:
                inserted += connection.execute(INSERT_SQL, row).rowcount
    finally:
        connection.close()
    print("새 기록 %d건, 중복 %d건" % (inserted, len(rows) - inserted))


def command_decrypt(arguments):
    sys.stdout.write(decrypt_attachment(arguments.file))


# ---------------------------------------------------------------- 날짜·시각 계산

def parse_day(text):
    """YYYY-MM-DD를 현지 자정(epoch 밀리초로 표현한 현지 시각)으로 변환"""
    try:
        day = datetime.datetime.strptime(text, "%Y-%m-%d")
    except ValueError:
        raise UsageLogError("날짜는 YYYY-MM-DD 형식으로 적어 주세요.") from None
    return int(day.replace(tzinfo=datetime.timezone.utc).timestamp() * 1000)


def parse_day_range(text):
    """`날짜` 또는 `날짜~날짜`를 (시작, 끝 직전) 현지 시각으로 변환"""
    first, separator, last = text.partition("~")
    start = parse_day(first.strip())
    end = parse_day(last.strip()) if separator else start
    if end < start:
        raise UsageLogError("기간의 끝 날짜가 시작 날짜보다 앞입니다.")
    return start, end + DAY_MS


def parse_single_day(text):
    """날짜 하나만 받는 명령의 날짜를 (현지 자정, 다음 자정)으로 변환"""
    if "~" in text:
        raise UsageLogError("이 명령은 기간 없이 날짜 하나만 받습니다.")
    start = parse_day(text.strip())
    return start, start + DAY_MS


def parse_time_of_day(text):
    """HH:MM을 자정부터의 밀리초로 변환 (00:00부터 하루 끝을 뜻하는 24:00까지)"""
    matched = re.fullmatch(r"(\d{1,2}):(\d{2})", text.strip())
    if matched:
        hour, minute = int(matched.group(1)), int(matched.group(2))
        if (hour < 24 and minute < 60) or (hour, minute) == (24, 0):
            return hour * HOUR_MS + minute * MINUTE_MS
    raise UsageLogError("시각은 00:00부터 24:00 사이의 HH:MM 형식으로 적어 주세요.")


def to_datetime(local_ms):
    return datetime.datetime.fromtimestamp(local_ms / 1000, tz=datetime.timezone.utc)


def format_time(local_ms):
    return to_datetime(local_ms).strftime("%H:%M")


def format_month_day(local_ms):
    return to_datetime(local_ms).strftime("%m-%d")


def format_month_day_time(local_ms):
    return to_datetime(local_ms).strftime("%m-%d %H:%M")


def format_duration(milliseconds):
    minutes = int(milliseconds // MINUTE_MS)
    if minutes <= 0:
        return "1분 미만"
    hours, rest = divmod(minutes, 60)
    if hours and rest:
        return "%d시간 %d분" % (hours, rest)
    if hours:
        return "%d시간" % hours
    return "%d분" % rest


def overlap(start, end, window_start, window_end):
    """구간 [start, end)와 [window_start, window_end)의 겹친 길이"""
    return max(0, min(end, window_end) - max(start, window_start))


def emit(lines, hidden=0):
    """줄 목록 출력

    300줄을 넘으면 앞 300줄만 출력하고, 넘친 줄 수에 hidden(목록에 넣지 않은 줄 수)을 더해 `외 N줄`로 붙임.
    """
    for line in lines[:MAX_OUTPUT_LINES]:
        print(line)
    hidden += max(0, len(lines) - MAX_OUTPUT_LINES)
    if hidden:
        print("외 %d줄" % hidden)


def flatten(text):
    """줄바꿈(CRLF, CR, LF)을 ⏎ 표시로 바꾼 한 줄"""
    return text.replace("\r\n", "\n").replace("\r", "\n").replace("\n", " ⏎ ")


# ---------------------------------------------------------------- 공통 조회

def fetch_usage_intervals(connection, window_start, window_end, app_filter=None):
    """창과 겹치는 앱 사용 구간을 (앱 이름, 시작, 끝) 목록으로 반환 (현지 시각)"""
    rows = connection.execute(
        "SELECT app_name, local_start, local_end FROM records "
        "WHERE type = 'app_usage' AND local_end > ? AND local_start < ? ORDER BY local_start",
        (window_start, window_end),
    ).fetchall()
    needle = app_filter.lower() if app_filter else None
    return [
        (row["app_name"] or "(이름 없음)", row["local_start"], row["local_end"])
        for row in rows
        if needle is None or needle in (row["app_name"] or "").lower()
    ]


def total_usage_by_app(intervals, window_start, window_end):
    totals = {}
    for app_name, start, end in intervals:
        length = overlap(start, end, window_start, window_end)
        if length:
            totals[app_name] = totals.get(app_name, 0) + length
    return totals


def fetch_gaps(connection, window_start, window_end):
    """창과 겹치는 기록 없음 구간을 (시작, 끝, 원인 설명) 목록으로 반환"""
    rows = connection.execute(
        "SELECT local_start, local_end, cause FROM records "
        "WHERE type = 'gap' AND local_end > ? AND local_start < ? ORDER BY local_start",
        (window_start, window_end),
    ).fetchall()
    return [(row["local_start"], row["local_end"], GAP_CAUSE_LABELS.get(row["cause"], "원인 불명")) for row in rows]


def home_debounce_ms():
    text = os.environ.get("USAGE_LOG_HOME_DEBOUNCE_MINUTES")
    if text is None or not text.strip():
        return DEFAULT_HOME_DEBOUNCE_MINUTES * MINUTE_MS
    try:
        minutes = float(text)
    except ValueError:
        raise UsageLogError("USAGE_LOG_HOME_DEBOUNCE_MINUTES는 숫자여야 합니다.") from None
    if minutes < 0:
        raise UsageLogError("USAGE_LOG_HOME_DEBOUNCE_MINUTES는 0 이상이어야 합니다.")
    return minutes * MINUTE_MS


def new_segment(state, start, start_mark, note=None):
    return {"state": state, "start": start, "end": None, "start_mark": start_mark, "end_mark": None, "note": note}


def build_home_segments(connection):
    """Wi-Fi 기록과 gap으로 만든 집·밖·알 수 없음 구간 목록 (집 Wi-Fi 기록이 하나도 없으면 None)

    원소는 new_segment의 dict이며 시각은 현지 시각 밀리초. 목록은 시간 순으로 빈틈없이 이어지고,
    첫 구간 start와 마지막 구간 end는 None(첫 기록 전부터, 마지막 기록 뒤로 이어짐).
    mark는 그 경계를 만든 Wi-Fi 기록으로 "wifi"(실제), "inferred"(추정), None(gap 경계·첫 기록 전).
    알 수 없음은 첫 Wi-Fi 기록 전, SSID를 몰라 home이 null인 연결, Wi-Fi 기록이 빠진 gap 안,
    보내기 전에 지운 기록 범위 뒤의 첫 Wi-Fi 기록 전까지.
    해제 뒤 USAGE_LOG_HOME_DEBOUNCE_MINUTES 안에 다시 집에 연결되면 같은 집 구간으로 이어 붙임.
    """
    has_home = connection.execute(
        "SELECT 1 FROM records WHERE type IN ('wifi_connected', 'wifi_disconnected') AND home = 1 LIMIT 1"
    ).fetchone()
    if has_home is None:
        return None

    # (정렬용 UTC 시각, 같은 시각 안의 우선순위, 쌓인 순서, 현지 시각, 상태, 경계 표시, 알 수 없음 사유)
    events = []
    rows = connection.execute(
        "SELECT type, t, local_t, home, inferred FROM records "
        "WHERE type IN ('wifi_connected', 'wifi_disconnected') ORDER BY t, rowid"
    ).fetchall()
    for order, row in enumerate(rows):
        mark = "inferred" if row["inferred"] else "wifi"
        if row["type"] == "wifi_disconnected" or row["home"] == 0:
            events.append((row["t"], 1, order, row["local_t"], AWAY, mark, None))
        elif row["home"] == 1:
            events.append((row["t"], 1, order, row["local_t"], HOME, mark, None))
        else:
            events.append((row["t"], 1, order, row["local_t"], UNKNOWN, mark, "Wi-Fi 이름을 알 수 없음"))
    gaps = connection.execute(
        "SELECT end_t, local_start, local_end, cause FROM records WHERE type = 'gap' ORDER BY start_t, rowid"
    ).fetchall()
    for gap in gaps:
        if gap["cause"] == "unsent_deleted":
            # 지운 범위 안의 Wi-Fi 변화를 모르므로 범위가 끝난 뒤에도 다음 Wi-Fi 기록 전까지 알 수 없음
            events.append((gap["end_t"], 0, 0, gap["local_end"], UNKNOWN, None, "보내기 전에 지운 기록 뒤"))
    events.sort(key=lambda event: event[:3])

    segments = [new_segment(UNKNOWN, None, None, "첫 Wi-Fi 기록 전")]
    for _, _, _, local_time, state, mark, note in events:
        current = segments[-1]
        if (state, note) == (current["state"], current["note"]):
            continue
        current["end"], current["end_mark"] = local_time, mark
        segments.append(new_segment(state, local_time, mark, note))

    debounce = home_debounce_ms()
    merged = []
    for segment in segments:
        short_leave = (len(merged) >= 2 and merged[-1]["state"] == AWAY and merged[-2]["state"] == HOME
                       and merged[-1]["end"] - merged[-1]["start"] <= debounce)
        if segment["state"] == HOME and short_leave:
            merged.pop()
            merged[-1]["end"], merged[-1]["end_mark"] = segment["end"], segment["end_mark"]
        else:
            merged.append(segment)

    for gap in gaps:
        if gap["cause"] not in HOME_KNOWN_GAP_CAUSES:
            merged = carve_unknown(merged, gap["local_start"], gap["local_end"], GAP_CAUSE_LABELS.get(gap["cause"], "원인 불명"))
    return merged


def carve_unknown(segments, start, end, note):
    """구간 목록에서 [start, end)를 알 수 없음으로 바꾼 새 목록 (잘린 경계의 mark는 None)"""
    if end <= start:
        return segments
    unknown = new_segment(UNKNOWN, start, None, note)
    unknown["end"] = end
    result = []
    inserted = False
    for segment in segments:
        segment_start = -math.inf if segment["start"] is None else segment["start"]
        segment_end = math.inf if segment["end"] is None else segment["end"]
        if segment_end <= start:
            result.append(segment)
            continue
        if segment_start < start:
            result.append(dict(segment, end=start, end_mark=None))
        if not inserted:
            result.append(unknown)
            inserted = True
        if segment_start >= end:
            result.append(segment)
        elif segment_end > end:
            result.append(dict(segment, start=end, start_mark=None))
    if not inserted:
        result.append(unknown)
    return result


def home_state_lengths(segments, latest, window_start, window_end):
    """창 안의 집·밖·알 수 없음 길이 (마지막 기록 뒤는 아직 모르므로 세지 않음)"""
    window_end = min(window_end, latest)
    lengths = {HOME: 0, AWAY: 0, UNKNOWN: 0}
    for segment in segments:
        start = window_start if segment["start"] is None else segment["start"]
        end = window_end if segment["end"] is None else segment["end"]
        lengths[segment["state"]] += overlap(start, end, window_start, window_end)
    return lengths


def latest_local_time(connection):
    """기록이 알려 주는 가장 늦은 현지 시각 (구간 기록은 끝 시각까지, 기록이 없으면 None)"""
    return connection.execute(
        "SELECT MAX(MAX(local_t, COALESCE(local_end, local_t))) AS latest FROM records"
    ).fetchone()["latest"]


# ---------------------------------------------------------------- summary

def command_summary(arguments):
    window_start, window_end = parse_single_day(arguments.date)
    connection = open_readonly_database()
    lines = []
    day_label = to_datetime(window_start).strftime("%Y-%m-%d")

    intervals = fetch_usage_intervals(connection, window_start, window_end)
    gaps = fetch_gaps(connection, window_start, window_end)
    latest = latest_local_time(connection)
    counts = {
        record_type: connection.execute(
            "SELECT COUNT(*) FROM records WHERE type = ? AND local_t >= ? AND local_t < ?",
            (record_type, window_start, window_end),
        ).fetchone()[0]
        for record_type in ("screen_on", "unlock", "notification")
    }
    # 알림이나 Wi-Fi 기록만 있는 날도 요약하도록 종류와 관계없이 그날 기록이 있는지 확인
    day_records = connection.execute(
        "SELECT COUNT(*) FROM records WHERE local_t >= ? AND local_t < ?", (window_start, window_end)
    ).fetchone()[0]
    if not intervals and not gaps and not day_records:
        lines = ["%s 기록 없음" % day_label]
        if latest is not None:
            lines.append("마지막 기록 %s" % format_month_day_time(latest))
        emit(lines)
        return

    lines.append("%s 요약" % day_label)
    totals = total_usage_by_app(intervals, window_start, window_end)
    ranked = sorted(totals.items(), key=lambda item: -item[1])[:10]
    lines.append("앱별 사용 시간 상위 %d개" % len(ranked) if ranked else "앱 사용 기록 없음")
    lines.extend("  %s %s" % (app_name, format_duration(length)) for app_name, length in ranked)
    lines.append("화면 켠 횟수 %d회" % counts["screen_on"])
    lines.append("잠금 해제 횟수 %d회" % counts["unlock"])
    lines.append("받은 알림 %d건" % counts["notification"])
    clipped = [
        (max(start, window_start), min(end, window_end))
        for _, start, end in intervals
        if overlap(start, end, window_start, window_end)
    ]
    if clipped:
        lines.append(
            "첫 사용 %s, 마지막 사용 %s" % (format_time(min(s for s, _ in clipped)), format_time(max(e for _, e in clipped)))
        )
    segments = build_home_segments(connection)
    if segments is None:
        lines.append("집 정보 없음")
    else:
        lengths = home_state_lengths(segments, latest, window_start, window_end)
        if lengths[HOME] or lengths[AWAY]:
            lines.append("집에 있던 시간 %s" % (format_duration(lengths[HOME]) if lengths[HOME] else "없음"))
        if lengths[UNKNOWN]:
            lines.append("집 여부를 알 수 없는 시간 %s" % format_duration(lengths[UNKNOWN]))
    for start, end, cause in gaps:
        lines.append(
            "기록 없음 %s~%s (%s)" % (format_time(max(start, window_start)), format_time(min(end, window_end)), cause)
        )
    lines.append("마지막 기록 %s" % format_month_day_time(latest))
    emit(lines)


# ---------------------------------------------------------------- timeline

def command_timeline(arguments):
    day_start, _ = parse_single_day(arguments.date)
    window_start = day_start + (parse_time_of_day(arguments.start) if arguments.start else 0)
    window_end = day_start + (parse_time_of_day(arguments.end) if arguments.end else DAY_MS)
    if window_end <= window_start:
        raise UsageLogError("끝 시각이 시작 시각보다 늦어야 합니다.")
    connection = open_readonly_database()
    intervals = fetch_usage_intervals(connection, window_start, window_end)
    gaps = fetch_gaps(connection, window_start, window_end)
    segments = build_home_segments(connection)
    latest = latest_local_time(connection)

    rows = []
    cursor = window_start
    while cursor < window_end:
        next_boundary = min((cursor // HOUR_MS + 1) * HOUR_MS, window_end)
        if latest is not None and cursor >= latest:
            state = "마지막 기록 뒤"
        elif segments is not None:
            # 가장 긴 상태 (같으면 집, 밖, 알 수 없음 순)
            lengths = home_state_lengths(segments, latest, cursor, next_boundary)
            state = max((HOME, AWAY, UNKNOWN), key=lambda name: lengths[name])
        else:
            state = ""
        totals = total_usage_by_app(intervals, cursor, next_boundary)
        ranked = sorted(totals.items(), key=lambda item: -item[1])[:3]
        usage_text = ", ".join("%s %s" % (name, format_duration(length)) for name, length in ranked)
        gap_text = ", ".join(
            "기록 없음 %s~%s" % (format_time(max(start, cursor)), format_time(min(end, next_boundary)))
            for start, end, _ in gaps
            if overlap(start, end, cursor, next_boundary)
        )
        detail = ", ".join(part for part in (usage_text, gap_text) if part)
        rows.append([cursor, next_boundary, state, detail])
        cursor = next_boundary

    # 같은 상태에서 내용이 없는 시간대는 한 줄로 합침
    merged = []
    for row in rows:
        if merged and not row[3] and not merged[-1][3] and merged[-1][2] == row[2]:
            merged[-1][1] = row[1]
        else:
            merged.append(row)
    lines = ["%s 시간대별 기록" % to_datetime(day_start).strftime("%Y-%m-%d")]
    for start, end, state, detail in merged:
        lines.append(" ".join(part for part in ("%s~%s" % (format_time(start), format_time(end)), state, detail) if part))
    emit(lines)


# ---------------------------------------------------------------- notifications

def find_removal_label(connection, row):
    """알림 게시 뒤 같은 key의 첫 제거 기록이 있으면 `열어 봄`·`지움`, 없으면 빈 문자열

    앱이 같은 알림을 고쳐 여러 번 게시하면 제거 한 번이 앞 게시 모두를 닫으므로 다음 게시를 경계로 두지 않음.
    제거 뒤 다시 게시된 알림은 그 게시 뒤의 제거를 찾으므로, 앞 게시는 앞 제거로 끝남.
    """
    key = row["notification_key"]
    if key is None:
        return ""
    removal = connection.execute(
        "SELECT reason FROM records WHERE type = 'notification_removed' AND notification_key = ? "
        "AND package_name IS ? AND t >= ? ORDER BY t, rowid LIMIT 1",
        (key, row["package_name"], row["t"]),
    ).fetchone()
    if removal is None:
        return ""
    return {"click": "열어 봄", "dismiss": "지움"}.get(removal["reason"], "")


def format_notification(row, label, show_date):
    parts = [row["app_name"] or "(앱 이름 없음)"]
    title = flatten(row["title"] or "")
    text = flatten(row["text"] or "")
    if title and text:
        parts.append("%s: %s" % (title, text))
    elif title or text:
        parts.append(title or text)
    else:
        parts.append("(내용 없음)")
    time_text = format_month_day_time(row["local_t"]) if show_date else format_time(row["local_t"])
    line = "%s %s" % (time_text, " ".join(parts))
    return "%s · %s" % (line, label) if label else line


def command_notifications(arguments):
    if arguments.new:
        if arguments.date or arguments.app or arguments.search:
            raise UsageLogError("--new는 날짜, --app, --search와 함께 쓸 수 없습니다.")
        show_new_notifications()
        return
    if not arguments.date:
        raise UsageLogError("날짜를 적거나 --new를 붙여 주세요.")
    window_start, window_end = parse_day_range(arguments.date)
    show_date = window_end - window_start > DAY_MS
    connection = open_readonly_database()
    rows = connection.execute(
        "SELECT * FROM records WHERE type = 'notification' AND local_t >= ? AND local_t < ? ORDER BY t, rowid",
        (window_start, window_end),
    ).fetchall()
    app_needle = arguments.app.lower() if arguments.app else None
    search_needle = arguments.search.lower() if arguments.search else None
    lines = []
    for row in rows:
        if app_needle and app_needle not in (row["app_name"] or "").lower():
            continue
        if search_needle and search_needle not in ("%s\n%s" % (row["title"] or "", row["text"] or "")).lower():
            continue
        lines.append(format_notification(row, find_removal_label(connection, row), show_date))
    emit(lines or ["조건에 맞는 알림 없음"])


def show_new_notifications():
    """지난 --new 뒤에 쌓인 알림을 쌓인 순서(rowid)로 300건까지 골라 시각 순으로 출력

    고르는 순서와 읽은 위치를 모두 rowid로 맞춰, 시각이 뒤섞여 쌓여도 다음 --new가 건너뛰거나 반복하지 않음.
    읽은 위치는 출력을 마친 뒤에만 옮기므로 출력이 실패하면 다음 --new가 같은 알림을 다시 돌려줌.
    조회는 읽기 전용 연결로 해서, DB 쓰기가 막힌 환경에서도 출력까지는 됨.
    """
    connection = open_readonly_database()
    found = connection.execute("SELECT last_rowid FROM read_position WHERE name = 'notifications'").fetchone()
    last_read = found["last_rowid"] if found else 0
    rows = connection.execute(
        "SELECT rowid AS position, * FROM records WHERE type = 'notification' AND rowid > ? ORDER BY rowid LIMIT ?",
        (last_read, MAX_OUTPUT_LINES),
    ).fetchall()
    if not rows:
        emit(["새 알림 없음"])
        return
    last_position = rows[-1]["position"]
    remaining = connection.execute(
        "SELECT COUNT(*) FROM records WHERE type = 'notification' AND rowid > ?", (last_position,)
    ).fetchone()[0]
    ordered = sorted(rows, key=lambda row: (row["t"], row["position"]))
    emit([format_notification(row, find_removal_label(connection, row), True) for row in ordered], remaining)
    sys.stdout.flush()

    try:
        writable = open_writable_database()
        with writable:
            writable.execute(
                "INSERT OR REPLACE INTO read_position (name, last_rowid) VALUES ('notifications', ?)", (last_position,)
            )
    except sqlite3.Error:
        raise UsageLogError("알림은 출력했지만 읽은 위치를 저장하지 못해 다음 --new가 같은 알림을 다시 돌려줍니다.") from None


# ---------------------------------------------------------------- home

def boundary_text(local_ms, mark, with_date):
    """구간 경계 시각 (추정 기록이 만든 경계면 `(추정)`을 붙임)"""
    text = format_month_day_time(local_ms) if with_date else format_time(local_ms)
    return text + "(추정)" if mark == "inferred" else text


def count_text(total, inferred):
    return "%d번(추정 %d번)" % (total, inferred) if inferred else "%d번" % total


def describe_home_segment(segment, shown_start):
    """home 명령의 집 또는 알 수 없음 구간 한 줄"""
    start_text = boundary_text(shown_start, segment["start_mark"], True)
    end = segment["end"]
    if segment["state"] == HOME:
        if end is None:
            return "%s~ 집에 있음 (마지막 기록까지)" % start_text
        same_day = to_datetime(shown_start).date() == to_datetime(end).date()
        end_text = boundary_text(end, segment["end_mark"], not same_day)
        return "%s~%s 집 (%s)" % (start_text, end_text, format_duration(end - shown_start))
    if end is None:
        return "%s~ 알 수 없음 (%s, 마지막 기록까지)" % (start_text, segment["note"])
    same_day = to_datetime(shown_start).date() == to_datetime(end).date()
    end_text = boundary_text(end, segment["end_mark"], not same_day)
    return "%s~%s 알 수 없음 (%s)" % (start_text, end_text, segment["note"])


def command_home(arguments):
    window_start, window_end = parse_day_range(arguments.date)
    connection = open_readonly_database()
    segments = build_home_segments(connection)
    if segments is None:
        emit(["집 Wi-Fi 기록 없음"])
        return
    latest = latest_local_time(connection)
    lines = []
    arrivals = inferred_arrivals = leaves = inferred_leaves = 0
    for segment in segments:
        start, end = segment["start"], segment["end"]
        if segment["state"] == AWAY:
            continue
        # gap 경계(mark None)는 실제 도착·출발이 아니므로 세지 않음
        if segment["state"] == HOME and segment["start_mark"] and window_start <= start < window_end:
            arrivals += 1
            inferred_arrivals += segment["start_mark"] == "inferred"
        if segment["state"] == HOME and segment["end_mark"] and end is not None and window_start <= end < window_end:
            leaves += 1
            inferred_leaves += segment["end_mark"] == "inferred"
        shown_start = window_start if start is None else start
        resolved_end = max(latest, shown_start) if end is None else end
        if segment["state"] == UNKNOWN and resolved_end <= shown_start:
            continue
        starts_in_window = start is not None and window_start <= start < window_end
        if overlap(shown_start, resolved_end, window_start, window_end) <= 0 and not starts_in_window:
            continue
        lines.append(describe_home_segment(segment, shown_start))
    lines.insert(0, "집 도착 %s, 집 출발 %s" % (count_text(arrivals, inferred_arrivals), count_text(leaves, inferred_leaves)))
    now_text = {HOME: "집에 있음", AWAY: "집 밖", UNKNOWN: "집 여부를 알 수 없음"}[segments[-1]["state"]]
    lines.append("마지막 기록 %s 기준 지금은 %s" % (format_month_day_time(latest), now_text))
    emit(lines)


# ---------------------------------------------------------------- apps

def command_apps(arguments):
    window_start, window_end = parse_day_range(arguments.period)
    connection = open_readonly_database()
    intervals = fetch_usage_intervals(connection, window_start, window_end, arguments.app)
    per_app = {}
    day = window_start
    while day < window_end:
        for app_name, length in total_usage_by_app(intervals, day, day + DAY_MS).items():
            per_app.setdefault(app_name, {})[day] = length
        day += DAY_MS
    if not per_app:
        emit(["사용 기록 없음"])
        return
    lines = []
    for app_name, per_day in sorted(per_app.items(), key=lambda item: -sum(item[1].values())):
        trend = ", ".join("%s %s" % (format_month_day(date), format_duration(length)) for date, length in sorted(per_day.items()))
        lines.append("%s 합계 %s (%s)" % (app_name, format_duration(sum(per_day.values())), trend))
    emit(lines)


# ---------------------------------------------------------------- sql

def allow_select_only(action, *_):
    """SELECT 읽기와 함수 호출 외의 모든 동작을 막는 SQLite 권한 검사"""
    allowed = (sqlite3.SQLITE_SELECT, sqlite3.SQLITE_READ, sqlite3.SQLITE_FUNCTION, sqlite3.SQLITE_RECURSIVE)
    return sqlite3.SQLITE_OK if action in allowed else sqlite3.SQLITE_DENY


def sql_cell(text):
    """열 구분자인 탭과 줄바꿈이 섞이지 않은 한 칸"""
    return flatten(text).replace("\t", " ")


def command_sql(arguments):
    statement = arguments.statement.strip()
    if statement.endswith(";"):
        statement = statement[:-1].rstrip()
    if not re.match(r"select\b", statement, re.IGNORECASE):
        raise UsageLogError("SELECT 문 하나만 실행할 수 있습니다.")
    connection = open_readonly_database()
    connection.set_authorizer(allow_select_only)
    deadline = time.monotonic() + SQL_TIME_LIMIT_SECONDS
    # 무한 재귀 CTE 같은 끝나지 않는 조회를 SQLite 명령 1000개마다 시간을 확인해 끊음 (fetch 중에도 동작)
    connection.set_progress_handler(lambda: time.monotonic() > deadline, 1000)
    try:
        cursor = connection.execute(statement)
        rows = cursor.fetchmany(MAX_SQL_ROWS + 1)
    except (sqlite3.Warning, sqlite3.ProgrammingError):
        raise UsageLogError("SELECT 문 하나만 실행할 수 있습니다.") from None
    except sqlite3.DatabaseError as error:
        if time.monotonic() > deadline:
            raise UsageLogError("SQL 실행이 %g초를 넘어 중단했습니다. 조건을 좁혀 주세요." % SQL_TIME_LIMIT_SECONDS) from None
        raise UsageLogError("SQL 실행에 실패했습니다: %s" % error) from None
    finally:
        connection.set_progress_handler(None, 0)
        connection.set_authorizer(None)
    lines = ["\t".join(sql_cell(column[0]) for column in cursor.description)]
    for row in rows[:MAX_SQL_ROWS]:
        lines.append("\t".join("NULL" if value is None else sql_cell(str(value)) for value in row))
    if len(rows) > MAX_SQL_ROWS:
        lines.append("외 더 있음 (최대 %d행까지만 출력)" % MAX_SQL_ROWS)
    emit(lines)


# ---------------------------------------------------------------- 진입점

def build_parser():
    parser = argparse.ArgumentParser(description="기기 사용 기록 쌓기와 조회 (자세한 설명은 이 파일 맨 위 참고)")
    commands = parser.add_subparsers(dest="command", required=True)

    ingest = commands.add_parser("ingest", help="첨부 파일을 복호화해 DB에 쌓기")
    ingest.add_argument("file")
    ingest.set_defaults(handler=command_ingest)

    decrypt = commands.add_parser("decrypt", help="첨부 파일의 평문 출력")
    decrypt.add_argument("file")
    decrypt.set_defaults(handler=command_decrypt)

    summary = commands.add_parser("summary", help="하루 합계")
    summary.add_argument("date")
    summary.set_defaults(handler=command_summary)

    timeline = commands.add_parser("timeline", help="시간대별 한 줄")
    timeline.add_argument("date")
    timeline.add_argument("start", nargs="?")
    timeline.add_argument("end", nargs="?")
    timeline.set_defaults(handler=command_timeline)

    notifications = commands.add_parser("notifications", help="알림 조회")
    notifications.add_argument("date", nargs="?", help="YYYY-MM-DD 또는 YYYY-MM-DD~YYYY-MM-DD (--new면 생략)")
    notifications.add_argument("--app", help="앱 이름에 포함된 글자")
    notifications.add_argument("--search", help="제목·본문에 포함된 글자")
    notifications.add_argument(
        "--new", action="store_true",
        help="지난 --new 뒤에 쌓인 알림만 (날짜, --app, --search와 함께 쓸 수 없음)")
    notifications.set_defaults(handler=command_notifications)

    home = commands.add_parser("home", help="집에 있던 구간")
    home.add_argument("date", help="YYYY-MM-DD 또는 YYYY-MM-DD~YYYY-MM-DD")
    home.set_defaults(handler=command_home)

    apps = commands.add_parser("apps", help="앱별 사용 시간 합계와 날짜별 추이")
    apps.add_argument("period", help="YYYY-MM-DD 또는 YYYY-MM-DD~YYYY-MM-DD")
    apps.add_argument("--app", help="앱 이름에 포함된 글자")
    apps.set_defaults(handler=command_apps)

    sql = commands.add_parser("sql", help="읽기 전용 SELECT 조회")
    sql.add_argument("statement")
    sql.set_defaults(handler=command_sql)
    return parser


def use_utf8_output():
    """표준 출력·오류를 UTF-8로 재설정

    Windows에서 파이프로 받으면 기본 인코딩이 cp949라 이모지 같은 글자에서 출력이 실패하고,
    봇·비서가 UTF-8로 읽으면 한글이 깨지기 때문. UTF-8로도 못 쓰는 글자는 \\x 형태로 바꿔 씀.
    """
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8", errors="backslashreplace")


def main(argv=None):
    use_utf8_output()
    arguments = build_parser().parse_args(argv)
    try:
        arguments.handler(arguments)
    except UsageLogError as error:
        print("오류: %s" % error, file=sys.stderr)
        return 1
    except sqlite3.Error:
        print("오류: 기록 DB를 열거나 읽을 수 없습니다.", file=sys.stderr)
        return 1
    except Exception as error:  # 키·기록 내용이 섞일 수 있어 메시지 없이 종류만 알림
        print("오류: 예상하지 못한 문제가 생겼습니다 (%s)." % type(error).__name__, file=sys.stderr)
        return 1
    finally:
        while OPEN_CONNECTIONS:
            OPEN_CONNECTIONS.pop().close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
