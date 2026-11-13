#!/usr/bin/env python3
"""기기 사용 기록 쌓기와 조회 스크립트

폰 앱이 디스코드 전용 채널로 보낸 암호화 첨부 파일(usage-log-N.bin)을 풀어 SQLite에 쌓고,
비서가 요청받았을 때 필요한 범위만 사람이 읽기 좋은 줄로 꺼내 주는 명령줄 도구.
이 파일 하나와 `cryptography` 패키지(pip install cryptography)만 있으면 동작함.

첨부 파일 형식
    앞 12바이트가 nonce, 나머지가 AES-256-GCM 암호문(끝 16바이트가 인증 태그).
    복호화한 결과는 gzip으로 압축된 JSON Lines이며, 첫 줄은 묶음 정보(meta),
    이어지는 줄마다 기록 하나. 시각은 모두 epoch 밀리초.

환경 변수
    USAGE_LOG_KEY                     Base64로 인코딩한 32바이트 키 (ingest, decrypt에 필요)
    USAGE_LOG_DB                      SQLite 파일 위치 (기본 ~/.usage_log/usage_log.sqlite3)
    USAGE_LOG_HOME_DEBOUNCE_MINUTES   집 Wi-Fi 해제 뒤 이 시간 안에 다시 연결되면 떠난 것으로 보지 않음 (기본 3)

명령
    ingest <첨부 파일>                          복호화해 DB에 쌓기. 출력은 "새 기록 N건, 중복 M건"뿐
    decrypt <첨부 파일>                         복호화한 평문 출력 (문제 확인용)
    summary <날짜>                              하루 합계
    timeline <날짜> [시작 HH:MM] [끝 HH:MM]     시간대별 한 줄
    notifications <날짜|날짜~날짜> [--app 이름] [--search 단어] [--new]
                                                --new는 지난 --new가 출력한 마지막 알림 뒤의 알림만 돌려주며
                                                --app, --search와 함께 쓸 수 없음. 300줄에서 잘렸으면 다음
                                                --new가 잘린 곳부터 이어서 돌려줌
    home <날짜|날짜~날짜>                       집에 있던 구간
    apps <날짜|날짜~날짜> [--app 이름]          앱별 사용 시간 합계와 날짜별 추이
    sql "<SELECT 문>"                           읽기 전용 조회 (SELECT 하나만, 최대 200행)

날짜는 YYYY-MM-DD. 하루는 기록이 들어온 묶음이 가진 시간대 값(tzOffsetMinutes)으로 따진 현지 날짜.
출력 줄이 300줄을 넘으면 앞 300줄과 "외 N줄"만 출력함.

DB 테이블
    records        기록 한 건당 한 행. id가 기본키이며 같은 id는 한 번만 들어감.
                   t, start_t, end_t는 epoch 밀리초, local_t, local_start, local_end는 현지 시각으로 옮긴 같은 값,
                   tz_offset_minutes는 그 기록이 속한 묶음의 시간대 값.
    read_position  notifications --new가 마지막으로 출력한 알림 records의 rowid.
"""

import argparse
import base64
import binascii
import datetime
import gzip
import json
import os
import re
import sqlite3
import sys
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
NONCE_BYTES = 12
KEY_BYTES = 32
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
    uri = "file:" + path.replace("?", "%3f").replace("#", "%23") + "?mode=ro"
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
    time = to_int_or_none(record.get("t"))
    if time is None:
        time = start
    if time is None:
        raise UsageLogError("시각이 없는 기록이 있어 쌓을 수 없습니다.")
    return {
        "id": str(record["id"]),
        "type": str(record["type"]),
        "t": time,
        "local_t": time + offset_ms,
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


def command_ingest(arguments):
    meta, records = parse_plain_text(decrypt_attachment(arguments.file))
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


def parse_time_of_day(text):
    """HH:MM을 자정부터의 밀리초로 변환"""
    matched = re.fullmatch(r"(\d{1,2}):(\d{2})", text.strip())
    if not matched or int(matched.group(1)) > 24 or int(matched.group(2)) > 59:
        raise UsageLogError("시각은 HH:MM 형식으로 적어 주세요.")
    return int(matched.group(1)) * HOUR_MS + int(matched.group(2)) * MINUTE_MS


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


def emit(lines):
    """줄 목록 출력. 300줄을 넘으면 앞 300줄과 남은 줄 수만 출력"""
    for line in lines[:MAX_OUTPUT_LINES]:
        print(line)
    if len(lines) > MAX_OUTPUT_LINES:
        print("외 %d줄" % (len(lines) - MAX_OUTPUT_LINES))


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


def build_home_intervals(connection):
    """집 Wi-Fi 연결·해제 기록으로 집에 있던 구간 목록을 만듦

    반환은 ([시작, 끝 또는 None], ...)이며 끝이 None이면 마지막 기록 기준 아직 집에 있음.
    해제 뒤 USAGE_LOG_HOME_DEBOUNCE_MINUTES 안에 다시 연결되면 같은 구간으로 이어 붙임.
    """
    debounce = home_debounce_ms()
    rows = connection.execute(
        "SELECT type, local_t FROM records WHERE type IN ('wifi_connected', 'wifi_disconnected') AND home = 1 "
        "ORDER BY t, rowid"
    ).fetchall()
    intervals = []
    for row in rows:
        if row["type"] == "wifi_connected":
            if intervals and intervals[-1][1] is None:
                continue
            if intervals and row["local_t"] - intervals[-1][1] <= debounce:
                intervals[-1][1] = None
            else:
                intervals.append([row["local_t"], None])
        elif intervals and intervals[-1][1] is None:
            intervals[-1][1] = max(row["local_t"], intervals[-1][0])
    return intervals


def latest_local_time(connection):
    row = connection.execute("SELECT MAX(local_t) AS latest FROM records").fetchone()
    return row["latest"] or 0


def resolve_open_end(interval, latest):
    """끝이 열린 집 구간의 끝을 마지막 기록 시각으로 채움"""
    return interval[1] if interval[1] is not None else max(latest, interval[0])


# ---------------------------------------------------------------- summary

def command_summary(arguments):
    window_start, window_end = parse_day_range(arguments.date)
    window_end = window_start + DAY_MS
    connection = open_readonly_database()
    lines = []
    day_label = to_datetime(window_start).strftime("%Y-%m-%d")

    intervals = fetch_usage_intervals(connection, window_start, window_end)
    gaps = fetch_gaps(connection, window_start, window_end)
    counts = {
        record_type: connection.execute(
            "SELECT COUNT(*) FROM records WHERE type = ? AND local_t >= ? AND local_t < ?",
            (record_type, window_start, window_end),
        ).fetchone()[0]
        for record_type in ("screen_on", "unlock")
    }
    if not intervals and not gaps and not any(counts.values()):
        emit(["%s 기록 없음" % day_label])
        return

    lines.append("%s 요약" % day_label)
    totals = total_usage_by_app(intervals, window_start, window_end)
    ranked = sorted(totals.items(), key=lambda item: -item[1])[:10]
    lines.append("앱별 사용 시간 상위 %d개" % len(ranked) if ranked else "앱 사용 기록 없음")
    lines.extend("  %s %s" % (app_name, format_duration(length)) for app_name, length in ranked)
    lines.append("화면 켠 횟수 %d회" % counts["screen_on"])
    lines.append("잠금 해제 횟수 %d회" % counts["unlock"])
    clipped = [
        (max(start, window_start), min(end, window_end))
        for _, start, end in intervals
        if overlap(start, end, window_start, window_end)
    ]
    if clipped:
        lines.append(
            "첫 사용 %s, 마지막 사용 %s" % (format_time(min(s for s, _ in clipped)), format_time(max(e for _, e in clipped)))
        )
    home_intervals = build_home_intervals(connection)
    if home_intervals:
        latest = latest_local_time(connection)
        home_length = sum(
            overlap(start, resolve_open_end([start, end], latest), window_start, window_end)
            for start, end in home_intervals
        )
        lines.append("집에 있던 시간 %s" % (format_duration(home_length) if home_length else "없음"))
    else:
        lines.append("집 정보 없음")
    for start, end, cause in gaps:
        lines.append(
            "기록 없음 %s~%s (%s)" % (format_time(max(start, window_start)), format_time(min(end, window_end)), cause)
        )
    emit(lines)


# ---------------------------------------------------------------- timeline

def command_timeline(arguments):
    day_start, _ = parse_day_range(arguments.date)
    window_start = day_start + (parse_time_of_day(arguments.start) if arguments.start else 0)
    window_end = day_start + (parse_time_of_day(arguments.end) if arguments.end else DAY_MS)
    if window_end <= window_start:
        raise UsageLogError("끝 시각이 시작 시각보다 늦어야 합니다.")
    connection = open_readonly_database()
    intervals = fetch_usage_intervals(connection, window_start, window_end)
    gaps = fetch_gaps(connection, window_start, window_end)
    home_intervals = build_home_intervals(connection)
    latest = latest_local_time(connection)

    rows = []
    cursor = window_start
    while cursor < window_end:
        next_boundary = min((cursor // HOUR_MS + 1) * HOUR_MS, window_end)
        if home_intervals:
            home_length = sum(
                overlap(start, resolve_open_end([start, end], latest), cursor, next_boundary)
                for start, end in home_intervals
            )
            state = "집" if home_length * 2 >= next_boundary - cursor else "밖"
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
    """알림 한 건 뒤에 이어진 제거 기록이 있으면 `열어 봄`·`지움`, 없으면 빈 문자열

    같은 key로 알림이 다시 게시되면 다음 게시 전까지의 제거만 이 알림의 것으로 봄.
    """
    key = row["notification_key"]
    if key is None:
        return ""
    next_post = connection.execute(
        "SELECT MIN(t) FROM records WHERE type = 'notification' AND notification_key = ? AND package_name IS ? AND t > ?",
        (key, row["package_name"], row["t"]),
    ).fetchone()[0]
    removal = connection.execute(
        "SELECT reason FROM records WHERE type = 'notification_removed' AND notification_key = ? "
        "AND package_name IS ? AND t >= ? AND t < ? ORDER BY t LIMIT 1",
        (key, row["package_name"], row["t"], next_post if next_post is not None else 2 ** 62),
    ).fetchone()
    if removal is None:
        return ""
    return {"click": "열어 봄", "dismiss": "지움"}.get(removal["reason"], "")


def format_notification(row, label, show_date):
    parts = [row["app_name"] or "(앱 이름 없음)"]
    title = (row["title"] or "").replace("\n", " ⏎ ")
    text = (row["text"] or "").replace("\n", " ⏎ ")
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
    if arguments.new and (arguments.app or arguments.search):
        raise UsageLogError("--new는 --app, --search와 함께 쓸 수 없습니다.")
    window_start, window_end = parse_day_range(arguments.date)
    show_date = window_end - window_start > DAY_MS
    if arguments.new:
        connection = open_writable_database()
        connection.row_factory = sqlite3.Row
    else:
        connection = open_readonly_database()
    try:
        last_read = 0
        if arguments.new:
            found = connection.execute("SELECT last_rowid FROM read_position WHERE name = 'notifications'").fetchone()
            last_read = found["last_rowid"] if found else 0
        rows = connection.execute(
            "SELECT rowid AS position, * FROM records WHERE type = 'notification' AND local_t >= ? AND local_t < ? "
            "AND rowid > ? ORDER BY t, rowid",
            (window_start, window_end, last_read),
        ).fetchall()
        app_needle = arguments.app.lower() if arguments.app else None
        search_needle = arguments.search.lower() if arguments.search else None
        selected = []
        for row in rows:
            if app_needle and app_needle not in (row["app_name"] or "").lower():
                continue
            if search_needle and search_needle not in ("%s\n%s" % (row["title"] or "", row["text"] or "")).lower():
                continue
            selected.append(row)
        lines = [format_notification(row, find_removal_label(connection, row), show_date) for row in selected]
        # 300줄 제한으로 잘린 알림은 다음 --new가 이어서 돌려주도록 실제 출력한 마지막 행까지만 위치를 옮김
        printed = selected[:MAX_OUTPUT_LINES]
        if arguments.new and printed:
            with connection:
                connection.execute(
                    "INSERT OR REPLACE INTO read_position (name, last_rowid) VALUES ('notifications', ?)",
                    (printed[-1]["position"],),
                )
    finally:
        connection.close()
    if not lines:
        lines = ["조건에 맞는 알림 없음"]
    emit(lines)


# ---------------------------------------------------------------- home

def command_home(arguments):
    window_start, window_end = parse_day_range(arguments.date)
    connection = open_readonly_database()
    intervals = build_home_intervals(connection)
    latest = latest_local_time(connection)
    if not intervals:
        emit(["집 Wi-Fi 기록 없음"])
        return
    lines = []
    arrivals = leaves = 0
    for start, end in intervals:
        if window_start <= start < window_end:
            arrivals += 1
        if end is not None and window_start <= end < window_end:
            leaves += 1
        in_window = overlap(start, resolve_open_end([start, end], latest), window_start, window_end) > 0
        if not in_window and not window_start <= start < window_end:
            continue
        if end is None:
            lines.append("%s~ 집에 있음 (마지막 기록까지)" % format_month_day_time(start))
        else:
            same_day = to_datetime(start).date() == to_datetime(end).date()
            end_text = format_time(end) if same_day else format_month_day_time(end)
            lines.append("%s~%s 집 (%s)" % (format_month_day_time(start), end_text, format_duration(end - start)))
    lines.insert(0, "집 도착 %d번, 집 출발 %d번" % (arrivals, leaves))
    lines.append("마지막 기록 기준 지금은 %s" % ("집에 있음" if intervals[-1][1] is None else "집 밖"))
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


def command_sql(arguments):
    statement = arguments.statement.strip()
    if statement.endswith(";"):
        statement = statement[:-1].rstrip()
    if not re.match(r"select\b", statement, re.IGNORECASE):
        raise UsageLogError("SELECT 문 하나만 실행할 수 있습니다.")
    connection = open_readonly_database()
    connection.set_authorizer(allow_select_only)
    try:
        cursor = connection.execute(statement)
        rows = cursor.fetchmany(MAX_SQL_ROWS + 1)
    except (sqlite3.Warning, sqlite3.ProgrammingError):
        raise UsageLogError("SELECT 문 하나만 실행할 수 있습니다.") from None
    except sqlite3.DatabaseError as error:
        raise UsageLogError("SQL 실행에 실패했습니다: %s" % error) from None
    finally:
        connection.set_authorizer(None)
    lines = ["\t".join(column[0] for column in cursor.description)]
    for row in rows[:MAX_SQL_ROWS]:
        lines.append("\t".join("NULL" if value is None else str(value).replace("\n", " ⏎ ") for value in row))
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
    notifications.add_argument("date", help="YYYY-MM-DD 또는 YYYY-MM-DD~YYYY-MM-DD")
    notifications.add_argument("--app", help="앱 이름에 포함된 글자")
    notifications.add_argument("--search", help="제목·본문에 포함된 글자")
    notifications.add_argument(
        "--new", action="store_true",
        help="지난 --new가 출력한 마지막 알림 뒤의 알림만 (--app, --search와 함께 쓸 수 없음)")
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


def main(argv=None):
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
