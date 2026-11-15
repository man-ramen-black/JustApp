"""usage_log.py 테스트

실행: 저장소 루트에서 py -B tools/usage-log/test_usage_log.py (Windows, cryptography 패키지 필요)
가짜 묶음은 이 파일의 도우미가 테스트용 키로 직접 암호화해 만들며,
앱이 만든 표본을 쓰는 테스트는 DecryptTest.test_decrypt_kotlin_sample뿐.
"""

import base64
import contextlib
import datetime
import errno
import gzip
import io
import json
import os
import re
import sqlite3
import sys
import tempfile
import time
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import usage_log  # noqa: E402
from cryptography.hazmat.primitives.ciphers.aead import AESGCM  # noqa: E402

TEST_KEY = bytes(range(100, 132))
TIMEZONE_OFFSET_MINUTES = 540


def local_ms(hour, minute, day=12):
    """2025-11-<day> hour:minute(UTC+9)에 해당하는 epoch 밀리초"""
    local = datetime.datetime(2025, 11, day, hour, minute, tzinfo=datetime.timezone.utc)
    return int(local.timestamp() * 1000) - TIMEZONE_OFFSET_MINUTES * 60_000


def app_usage(record_id, package, app_name, start, end):
    return {"id": record_id, "type": "app_usage", "package": package, "appName": app_name,
            "start": start, "end": end, "t": start}


def simple(record_id, record_type, time):
    return {"id": record_id, "type": record_type, "t": time}


def notification(record_id, key, package, app_name, time, title, text, category="msg"):
    return {"id": record_id, "type": "notification", "key": key, "package": package, "appName": app_name,
            "t": time, "title": title, "text": text, "category": category}


def removal(record_id, key, package, time, reason):
    return {"id": record_id, "type": "notification_removed", "key": key, "package": package,
            "t": time, "reason": reason}


def wifi(record_id, record_type, time, ssid="HomeWifi", home=True):
    return {"id": record_id, "type": record_type, "t": time, "ssid": ssid, "home": home, "inferred": False}


def sms(record_id, time, direction, peers, text, subject=None, mms=False, attachments=0, key=None):
    return {"id": record_id, "type": "sms", "t": time, "messageKey": key or "sms:%s:%d" % (record_id, time),
            "direction": direction, "peers": peers, "subject": subject, "text": text, "mms": mms,
            "attachments": attachments}


def encrypt_batch(records, key=TEST_KEY, seq=1, offset_minutes=TIMEZONE_OFFSET_MINUTES, version=1, app="com.black.app"):
    """meta 줄과 기록 줄을 gzip·AES-GCM으로 만든 첨부 파일 바이트"""
    meta = {"type": "meta", "v": version, "seq": seq, "sentAt": local_ms(23, 0), "reason": "periodic",
            "device": "SM-FAKE", "app": app, "tzOffsetMinutes": offset_minutes}
    plain = "".join(json.dumps(line, ensure_ascii=False) + "\n" for line in [meta] + records)
    nonce = os.urandom(12)
    return nonce + AESGCM(key).encrypt(nonce, gzip.compress(plain.encode("utf-8")), None)


class UsageLogTestCase(unittest.TestCase):

    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        environment = mock.patch.dict(os.environ, {
            "USAGE_LOG_DB": os.path.join(self.folder.name, "test.sqlite3"),
            "USAGE_LOG_KEY": base64.b64encode(TEST_KEY).decode(),
        })
        environment.start()
        self.addCleanup(environment.stop)
        os.environ.pop("USAGE_LOG_HOME_DEBOUNCE_MINUTES", None)
        os.environ.pop("USAGE_LOG_APP", None)
        self.attachment_count = 0

    def run_command(self, *arguments):
        """명령 실행 결과 (종료 코드, 표준 출력, 표준 오류)"""
        output, errors = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            code = usage_log.main(list(arguments))
        return code, output.getvalue(), errors.getvalue()

    def write_attachment(self, records, **options):
        """가짜 기록 묶음을 첨부 파일로 쓰고 경로를 반환"""
        self.attachment_count += 1
        path = os.path.join(self.folder.name, "usage-log-%d.bin" % self.attachment_count)
        with open(path, "wb") as file:
            file.write(encrypt_batch(records, **options))
        return path

    def ingest(self, records, **options):
        """가짜 기록 묶음을 만들어 ingest하고 표준 출력을 반환"""
        code, output, errors = self.run_command("ingest", self.write_attachment(records, **options))
        self.assertEqual((0, ""), (code, errors))
        return output

    def query(self, *arguments):
        code, output, errors = self.run_command(*arguments)
        self.assertEqual((0, ""), (code, errors))
        return output


class DecryptTest(UsageLogTestCase):

    def test_decrypt_kotlin_sample(self):
        sample_folder = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "build", "usage-log-sample")
        if not os.path.isdir(sample_folder):
            # 표본 생성까지 마친 검증에서는 USAGE_LOG_REQUIRE_SAMPLE=1로 건너뛰기 대신 실패로 처리
            if os.environ.get("USAGE_LOG_REQUIRE_SAMPLE") == "1":
                self.fail("앱 표본 폴더(app/build/usage-log-sample)가 없음")
            self.skipTest("앱 표본 폴더(app/build/usage-log-sample)가 없음")
        with open(os.path.join(sample_folder, "key.txt"), encoding="utf-8") as file:
            os.environ["USAGE_LOG_KEY"] = file.read().strip()
        with open(os.path.join(sample_folder, "plain.jsonl"), encoding="utf-8") as file:
            expected = file.read()

        code, output, errors = self.run_command("decrypt", os.path.join(sample_folder, "sample.bin"))

        self.assertEqual((0, ""), (code, errors))
        self.assertEqual(expected, output)
        self.assertEqual("새 기록 20건, 중복 0건", self.ingest_sample(sample_folder))
        self.assertEqual([
            "2025-11-12 요약",
            "앱별 사용 시간 상위 2개",
            "  YouTube 30분",
            "  카카오톡 10분",
            "화면 켠 횟수 2회",
            "잠금 해제 횟수 1회",
            "받은 알림 3건",
            "첫 사용 07:31, 마지막 사용 12:31",
            "집에 있던 시간 8시간 30분",
            "집 여부를 알 수 없는 시간 50분",
            "기록 없음 13:10~14:00 (수집을 끈 기간)",
            "마지막 기록 11-12 18:30",
        ], self.query("summary", "2025-11-12").splitlines())
        self.assertEqual([
            "07:35 카카오톡 홍길동: 내일 몇 시? · 열어 봄",
            "07:36 카카오톡 홍길동: 줄바꿈 ⏎ \"따옴표\" 포함",
            "08:00 쇼핑 (내용 없음) · 지움",
        ], self.query("notifications", "2025-11-12").splitlines())
        self.assertEqual([
            "집 도착 2번(추정 1번), 집 출발 1번",
            "11-12 00:00~08:30 집 (8시간 30분)",
            "11-12 13:10~14:00 알 수 없음 (수집을 끈 기간)",
            "11-12 18:30(추정)~ 집에 있음 (마지막 기록까지)",
            "마지막 기록 11-12 18:30 기준 지금은 집에 있음",
        ], self.query("home", "2025-11-12").splitlines())
        self.assertEqual([
            "09:00 받음 홍길동(01012345678) [모임 안내] 첫 줄 ⏎ 둘째 줄 · 첨부 1",
            "09:05 보냄 0212345678 확인했습니다",
        ], self.query("sms", "2025-11-12").splitlines())

    def ingest_sample(self, sample_folder):
        code, output, errors = self.run_command("ingest", os.path.join(sample_folder, "sample.bin"))
        self.assertEqual((0, ""), (code, errors))
        return output.strip()

    def test_wrong_or_missing_key_gives_short_error(self):
        path = os.path.join(self.folder.name, "wrong.bin")
        with open(path, "wb") as file:
            file.write(encrypt_batch([simple("a-1", "screen_on", local_ms(8, 0))], key=bytes(32)))

        wrong_code, wrong_output, wrong_errors = self.run_command("ingest", path)
        del os.environ["USAGE_LOG_KEY"]
        missing_code, _, missing_errors = self.run_command("ingest", path)

        self.assertNotEqual(0, wrong_code)
        self.assertEqual("", wrong_output)
        self.assertTrue(wrong_errors.startswith("오류: "))
        self.assertNotEqual(0, missing_code)
        for message in (wrong_errors, missing_errors):
            self.assertNotIn("Traceback", message)
            self.assertNotIn(base64.b64encode(TEST_KEY).decode(), message)
            self.assertEqual(1, len(message.strip().splitlines()))


class IngestTest(UsageLogTestCase):

    def test_ingest_twice_has_no_duplicates(self):
        records = [simple("a-1", "screen_on", local_ms(8, 0)), simple("a-2", "unlock", local_ms(8, 1))]

        first = self.ingest(records)
        second = self.ingest(records)

        self.assertEqual("새 기록 2건, 중복 0건\n", first)
        self.assertEqual("새 기록 0건, 중복 2건\n", second)

    def test_ingest_rejects_other_version_or_app(self):
        records = [simple("a-1", "screen_on", local_ms(8, 0))]

        other_version = self.run_command("ingest", self.write_attachment(records, version=2))
        other_app = self.run_command("ingest", self.write_attachment(records, app="com.black.app.dev"))
        os.environ["USAGE_LOG_APP"] = "com.black.app.dev"
        chosen_app = self.run_command("ingest", self.write_attachment(records, app="com.black.app.dev"))

        self.assertEqual((1, "", "오류: 지원하지 않는 기록 형식 버전(v=2)이라 쌓지 않습니다.\n"), other_version)
        self.assertEqual(
            (1, "", "오류: 보낸 앱(com.black.app.dev)이 USAGE_LOG_APP(com.black.app)과 달라 쌓지 않습니다.\n"), other_app)
        self.assertEqual((0, "새 기록 1건, 중복 0건\n", ""), chosen_app)

    def test_database_path_with_uri_characters(self):
        os.environ["USAGE_LOG_DB"] = os.path.join(self.folder.name, "기록 #1 %20 폴더", "test.sqlite3")
        self.ingest([simple("a-1", "screen_on", local_ms(8, 0))])

        lines = self.query("summary", "2025-11-12").splitlines()

        self.assertIn("화면 켠 횟수 1회", lines)


class QueryTest(UsageLogTestCase):

    def test_summary_counts(self):
        self.ingest([
            simple("a-1", "screen_on", local_ms(7, 30)),
            simple("a-2", "unlock", local_ms(7, 30) + 5000),
            app_usage("a-3", "com.kakao.talk", "카카오톡", local_ms(7, 31), local_ms(7, 41)),
            simple("a-4", "screen_off", local_ms(8, 5)),
            simple("a-5", "screen_on", local_ms(12, 0)),
            app_usage("a-6", "com.google.android.youtube", "YouTube", local_ms(12, 1), local_ms(12, 31)),
            app_usage("a-7", "com.kakao.talk", "카카오톡", local_ms(12, 40), local_ms(12, 45)),
            simple("a-8", "screen_off", local_ms(12, 50)),
            {"id": "a-9", "type": "gap", "start": local_ms(13, 10), "end": local_ms(14, 0),
             "cause": "collection_off", "t": local_ms(13, 10)},
        ])

        lines = self.query("summary", "2025-11-12").splitlines()

        self.assertIn("  YouTube 30분", lines)
        self.assertIn("  카카오톡 15분", lines)
        self.assertLess(lines.index("  YouTube 30분"), lines.index("  카카오톡 15분"))
        self.assertIn("화면 켠 횟수 2회", lines)
        self.assertIn("잠금 해제 횟수 1회", lines)
        self.assertIn("첫 사용 07:31, 마지막 사용 12:45", lines)
        self.assertIn("기록 없음 13:10~14:00 (수집을 끈 기간)", lines)

    def test_notifications_returns_all_and_marks_removed(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "홍길동", "내일 몇 시?"),
            removal("a-2", "k1", "com.kakao.talk", local_ms(7, 40), "click"),
            notification("a-3", "k2", "com.example.shop", "쇼핑", local_ms(8, 0), "할인", "줄바꿈\n\"따옴표\" 포함", None),
            removal("a-4", "k2", "com.example.shop", local_ms(8, 1), "dismiss"),
            notification("a-5", "k3", "com.example.news", "뉴스", local_ms(9, 0), None, None, None),
        ])

        lines = self.query("notifications", "2025-11-12").splitlines()

        self.assertEqual(3, len(lines))
        self.assertEqual("07:35 카카오톡 홍길동: 내일 몇 시? · 열어 봄", lines[0])
        self.assertTrue(lines[1].startswith("08:00 쇼핑 할인: 줄바꿈 ⏎ \"따옴표\" 포함"))
        self.assertTrue(lines[1].endswith("지움"))
        self.assertEqual("09:00 뉴스 (내용 없음)", lines[2])

    def test_notifications_filters_by_app_and_search(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "엄마", "저녁 먹자"),
            notification("a-2", "k2", "com.kakao.talk", "카카오톡", local_ms(7, 36), "홍길동", "내일 몇 시?"),
            notification("a-3", "k3", "com.example.shop", "쇼핑", local_ms(8, 0), "할인", "엄마 선물 특가"),
        ])

        by_app = self.query("notifications", "2025-11-12", "--app", "카카오").splitlines()
        by_search = self.query("notifications", "2025-11-12", "--search", "엄마").splitlines()
        both = self.query("notifications", "2025-11-12", "--app", "카카오", "--search", "엄마").splitlines()

        self.assertEqual(2, len(by_app))
        self.assertEqual(2, len(by_search))
        self.assertEqual(["07:35 카카오톡 엄마: 저녁 먹자"], both)

    def test_notifications_new_returns_only_new(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "홍길동", "첫째"),
            notification("a-2", "k2", "com.kakao.talk", "카카오톡", local_ms(7, 36), "홍길동", "둘째"),
        ])
        first = self.query("notifications", "--new").splitlines()
        self.ingest([notification("b-1", "k3", "com.kakao.talk", "카카오톡", local_ms(9, 0), "홍길동", "셋째")])

        second = self.query("notifications", "--new").splitlines()
        third = self.query("notifications", "--new").splitlines()
        everything = self.query("notifications", "2025-11-12").splitlines()

        self.assertEqual(2, len(first))
        self.assertEqual(["11-12 09:00 카카오톡 홍길동: 셋째"], second)
        self.assertEqual(["새 알림 없음"], third)
        self.assertEqual(3, len(everything))

    def test_notifications_new_continues_after_300_line_cut(self):
        self.ingest([
            notification("a-%03d" % number, "k%d" % number, "com.kakao.talk", "카카오톡", local_ms(7, 0) + number, "t", "n%d" % number)
            for number in range(305)
        ])

        first = self.query("notifications", "--new").splitlines()
        second = self.query("notifications", "--new").splitlines()
        third = self.query("notifications", "--new").splitlines()

        self.assertEqual(301, len(first))
        self.assertEqual("외 5줄", first[-1])
        self.assertTrue(first[299].endswith("t: n299"))
        self.assertEqual(5, len(second))
        self.assertTrue(second[0].endswith("t: n300"))
        self.assertTrue(second[-1].endswith("t: n304"))
        self.assertEqual(["새 알림 없음"], third)

    def test_notifications_new_rejects_date_and_filters_and_keeps_position(self):
        self.ingest([notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "홍길동", "첫째")])

        results = [
            self.run_command("notifications", "2025-11-12", "--new"),
            self.run_command("notifications", "--new", "--app", "카카오"),
            self.run_command("notifications", "--new", "--search", "첫째"),
        ]
        no_date = self.run_command("notifications")
        after = self.query("notifications", "--new").splitlines()

        for code, output, errors in results:
            self.assertEqual(1, code)
            self.assertEqual("", output)
            self.assertEqual("오류: --new는 날짜, --app, --search와 함께 쓸 수 없습니다.\n", errors)
        self.assertEqual((1, "", "오류: 날짜를 적거나 --new를 붙여 주세요.\n"), no_date)
        self.assertEqual(["11-12 07:35 카카오톡 홍길동: 첫째"], after)

    def test_notifications_new_spans_midnight_without_date(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(0, 10), "홍길동", "오늘"),
            notification("a-2", "k2", "com.kakao.talk", "카카오톡", local_ms(23, 50, day=11), "홍길동", "어제"),
        ])

        lines = self.query("notifications", "--new").splitlines()

        self.assertEqual(["11-11 23:50 카카오톡 홍길동: 어제", "11-12 00:10 카카오톡 홍길동: 오늘"], lines)

    def test_notifications_new_reversed_ingest_has_no_repeat_or_skip(self):
        # 시각이 늦은 알림부터 쌓아 rowid 순서와 시각 순서를 뒤집음
        self.ingest([
            notification("a-%03d" % number, "k%d" % number, "com.kakao.talk", "카카오톡", local_ms(7, 0) + number * 1000,
                         "t", "n%03d" % number)
            for number in reversed(range(305))
        ])
        self.ingest([notification("b-1", "k-early", "com.kakao.talk", "카카오톡", local_ms(6, 0), "t", "early")])

        outputs = [self.query("notifications", "--new").splitlines() for _ in range(3)]

        printed = [line for output in outputs for line in output if line.startswith("11-12 ")]
        self.assertEqual("외 6줄", outputs[0][-1])
        self.assertEqual(6, len(outputs[1]))
        self.assertEqual(["새 알림 없음"], outputs[2])
        self.assertEqual(306, len(printed))
        self.assertEqual(306, len(set(printed)))
        for output in outputs[:2]:
            body = [line for line in output if line.startswith("11-12 ")]
            self.assertEqual(sorted(body), body)

    def test_notifications_new_keeps_position_when_output_fails(self):
        self.ingest([notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "홍길동", "첫째")])

        class BrokenOutput(io.StringIO):
            def write(self, text):
                raise OSError(errno.EPIPE, "파이프 끊김")

        errors = io.StringIO()
        with contextlib.redirect_stdout(BrokenOutput()), contextlib.redirect_stderr(errors):
            code = usage_log.main(["notifications", "--new"])
        after = self.query("notifications", "--new").splitlines()

        self.assertEqual(1, code)
        self.assertTrue(errors.getvalue().startswith("오류: "))
        self.assertEqual(["11-12 07:35 카카오톡 홍길동: 첫째"], after)

    def test_output_is_utf8_on_cp949_stdout(self):
        self.ingest([notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "홍길동", "좋아요 👍")])
        buffer = io.BytesIO()
        output = io.TextIOWrapper(buffer, encoding="cp949", newline="\n")
        errors = io.StringIO()

        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            code = usage_log.main(["notifications", "2025-11-12"])
        output.flush()

        self.assertEqual((0, ""), (code, errors.getvalue()))
        self.assertEqual("07:35 카카오톡 홍길동: 좋아요 👍\n", buffer.getvalue().decode("utf-8"))

    def test_removal_closes_every_earlier_post_of_same_key(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "홍길동", "첫째"),
            notification("a-2", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 37), "홍길동", "첫째, 둘째"),
            removal("a-3", "k1", "com.kakao.talk", local_ms(7, 40), "click"),
        ])

        lines = self.query("notifications", "2025-11-12").splitlines()

        self.assertTrue(lines[0].startswith("07:35 ") and lines[0].endswith(" · 열어 봄"), lines)
        self.assertTrue(lines[1].startswith("07:37 ") and lines[1].endswith(" · 열어 봄"), lines)

    def test_repost_after_removal_keeps_earlier_label(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "홍길동", "첫째"),
            removal("a-2", "k1", "com.kakao.talk", local_ms(7, 36), "click"),
            notification("a-3", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 50), "홍길동", "둘째"),
            removal("a-4", "k1", "com.kakao.talk", local_ms(8, 0), "dismiss"),
            notification("a-5", "k1", "com.kakao.talk", "카카오톡", local_ms(8, 10), "홍길동", "셋째"),
        ])

        lines = self.query("notifications", "2025-11-12").splitlines()

        self.assertEqual([
            "07:35 카카오톡 홍길동: 첫째 · 열어 봄",
            "07:50 카카오톡 홍길동: 둘째 · 지움",
            "08:10 카카오톡 홍길동: 셋째",
        ], lines)

    def test_notification_text_line_breaks_become_one_line(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "제목\r\n둘째 줄", "본문\r끝\n다음"),
        ])

        lines = self.query("notifications", "2025-11-12").splitlines()

        self.assertEqual(["07:35 카카오톡 제목 ⏎ 둘째 줄: 본문 ⏎ 끝 ⏎ 다음"], lines)

    def test_notifications_range_shows_dates(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35, day=11), "홍길동", "어제"),
            notification("a-2", "k2", "com.kakao.talk", "카카오톡", local_ms(7, 35, day=12), "홍길동", "오늘"),
        ])

        lines = self.query("notifications", "2025-11-11~2025-11-12").splitlines()

        self.assertEqual(["11-11 07:35 카카오톡 홍길동: 어제", "11-12 07:35 카카오톡 홍길동: 오늘"], lines)

    def test_home_debounce(self):
        self.ingest([
            wifi("a-1", "wifi_connected", local_ms(10, 0)),
            wifi("a-2", "wifi_disconnected", local_ms(10, 30)),
            wifi("a-3", "wifi_connected", local_ms(10, 32)),
        ])
        quick_return = self.query("home", "2025-11-12").splitlines()

        os.environ["USAGE_LOG_DB"] = os.path.join(self.folder.name, "second.sqlite3")
        self.ingest([
            wifi("b-1", "wifi_connected", local_ms(10, 0)),
            wifi("b-2", "wifi_disconnected", local_ms(10, 30)),
            wifi("b-3", "wifi_connected", local_ms(10, 35)),
        ])
        late_return = self.query("home", "2025-11-12").splitlines()

        self.assertEqual("집 도착 1번, 집 출발 0번", quick_return[0])
        self.assertIn("11-12 10:00~ 집에 있음 (마지막 기록까지)", quick_return)
        self.assertEqual("집 도착 2번, 집 출발 1번", late_return[0])
        self.assertIn("11-12 10:00~10:30 집 (30분)", late_return)

    def test_home_separates_unknown_time_and_marks_inferred(self):
        self.ingest([
            wifi("a-1", "wifi_connected", local_ms(8, 0)),
            wifi("a-2", "wifi_disconnected", local_ms(9, 0)),
            {"id": "a-3", "type": "gap", "start": local_ms(9, 10), "end": local_ms(9, 40),
             "cause": "usage_events_expired", "t": local_ms(9, 10)},
            wifi("a-4", "wifi_connected", local_ms(10, 0), ssid=None, home=None),
            wifi("a-5", "wifi_disconnected", local_ms(11, 0), ssid=None, home=None),
            {"id": "a-6", "type": "gap", "start": local_ms(12, 0), "end": local_ms(13, 0),
             "cause": "collection_off", "t": local_ms(12, 0)},
            dict(wifi("a-7", "wifi_connected", local_ms(14, 0)), inferred=True),
        ])

        home = self.query("home", "2025-11-12").splitlines()
        summary = self.query("summary", "2025-11-12").splitlines()
        timeline = self.query("timeline", "2025-11-12", "07:00", "15:00").splitlines()

        self.assertEqual([
            "집 도착 2번(추정 1번), 집 출발 1번",
            "11-12 00:00~08:00 알 수 없음 (첫 Wi-Fi 기록 전)",
            "11-12 08:00~09:00 집 (1시간)",
            "11-12 10:00~11:00 알 수 없음 (Wi-Fi 이름을 알 수 없음)",
            "11-12 12:00~13:00 알 수 없음 (수집을 끈 기간)",
            "11-12 14:00(추정)~ 집에 있음 (마지막 기록까지)",
            "마지막 기록 11-12 14:00 기준 지금은 집에 있음",
        ], home)
        self.assertIn("집에 있던 시간 1시간", summary)
        self.assertIn("집 여부를 알 수 없는 시간 10시간", summary)
        self.assertEqual([
            "2025-11-12 시간대별 기록",
            "07:00~08:00 알 수 없음",
            "08:00~09:00 집",
            "09:00~10:00 밖 기록 없음 09:10~09:40",
            "10:00~11:00 알 수 없음",
            "11:00~12:00 밖",
            "12:00~13:00 알 수 없음 기록 없음 12:00~13:00",
            "13:00~14:00 밖",
            "14:00~15:00 마지막 기록 뒤",
        ], timeline)

    def test_home_unknown_after_unsent_deleted_until_next_wifi(self):
        self.ingest([
            wifi("a-1", "wifi_connected", local_ms(8, 0)),
            {"id": "a-2", "type": "gap", "start": local_ms(9, 0), "end": local_ms(10, 0),
             "cause": "unsent_deleted", "t": local_ms(9, 0)},
            simple("a-3", "screen_on", local_ms(12, 0)),
        ])

        lines = self.query("home", "2025-11-12").splitlines()

        self.assertEqual([
            "집 도착 1번, 집 출발 0번",
            "11-12 00:00~08:00 알 수 없음 (첫 Wi-Fi 기록 전)",
            "11-12 08:00~09:00 집 (1시간)",
            "11-12 09:00~10:00 알 수 없음 (보내기 전에 지운 기록)",
            "11-12 10:00~ 알 수 없음 (보내기 전에 지운 기록 뒤, 마지막 기록까지)",
            "마지막 기록 11-12 12:00 기준 지금은 집 여부를 알 수 없음",
        ], lines)

    def test_summary_for_day_with_only_notifications_or_wifi(self):
        self.ingest([
            notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(9, 0, day=11), "홍길동", "어제"),
            wifi("a-2", "wifi_connected", local_ms(10, 0)),
            wifi("a-3", "wifi_disconnected", local_ms(11, 0)),
        ])

        notification_day = self.query("summary", "2025-11-11").splitlines()
        wifi_day = self.query("summary", "2025-11-12").splitlines()
        empty_day = self.query("summary", "2025-11-13").splitlines()

        self.assertIn("받은 알림 1건", notification_day)
        self.assertIn("집 여부를 알 수 없는 시간 24시간", notification_day)
        self.assertIn("집에 있던 시간 1시간", wifi_day)
        self.assertIn("마지막 기록 11-12 11:00", wifi_day)
        self.assertEqual(["2025-11-13 기록 없음", "마지막 기록 11-12 11:00"], empty_day)

    def test_home_debounce_minutes_can_be_changed(self):
        os.environ["USAGE_LOG_HOME_DEBOUNCE_MINUTES"] = "10"
        self.ingest([
            wifi("a-1", "wifi_connected", local_ms(10, 0)),
            wifi("a-2", "wifi_disconnected", local_ms(10, 30)),
            wifi("a-3", "wifi_connected", local_ms(10, 35)),
        ])

        self.assertEqual("집 도착 1번, 집 출발 0번", self.query("home", "2025-11-12").splitlines()[0])

    def test_apps_totals_and_trend(self):
        self.ingest([
            app_usage("a-1", "com.google.android.youtube", "YouTube", local_ms(12, 0, day=11), local_ms(12, 30, day=11)),
            app_usage("a-2", "com.google.android.youtube", "YouTube", local_ms(12, 0), local_ms(13, 30)),
            app_usage("a-3", "com.kakao.talk", "카카오톡", local_ms(8, 0), local_ms(8, 10)),
        ])

        lines = self.query("apps", "2025-11-11~2025-11-12").splitlines()
        only_youtube = self.query("apps", "2025-11-11~2025-11-12", "--app", "youtube").splitlines()

        self.assertEqual("YouTube 합계 2시간 (11-11 30분, 11-12 1시간 30분)", lines[0])
        self.assertEqual("카카오톡 합계 10분 (11-12 10분)", lines[1])
        self.assertEqual(1, len(only_youtube))

    def test_timeline_has_hourly_rows(self):
        self.ingest([
            wifi("a-1", "wifi_connected", local_ms(0, 0)),
            wifi("a-2", "wifi_disconnected", local_ms(8, 30)),
            app_usage("a-3", "com.kakao.talk", "카카오톡", local_ms(7, 31), local_ms(7, 41)),
        ])

        lines = self.query("timeline", "2025-11-12", "07:00", "09:00").splitlines()

        self.assertIn("07:00~08:00 집 카카오톡 10분", lines)
        self.assertIn("08:00~09:00 집", lines)

    def test_single_day_commands_reject_range_and_bad_time(self):
        self.ingest([simple("a-1", "screen_on", local_ms(8, 0))])

        rejected = [
            self.run_command("summary", "2025-11-11~2025-11-12"),
            self.run_command("timeline", "2025-11-11~2025-11-12"),
            self.run_command("timeline", "2025-11-12", "23:00", "24:59"),
            self.run_command("timeline", "2025-11-12", "23:60", "24:00"),
        ]
        until_midnight = self.query("timeline", "2025-11-12", "23:00", "24:00").splitlines()

        self.assertEqual((1, "", "오류: 이 명령은 기간 없이 날짜 하나만 받습니다.\n"), rejected[0])
        self.assertEqual((1, "", "오류: 이 명령은 기간 없이 날짜 하나만 받습니다.\n"), rejected[1])
        for result in rejected[2:]:
            self.assertEqual((1, "", "오류: 시각은 00:00부터 24:00 사이의 HH:MM 형식으로 적어 주세요.\n"), result)
        self.assertEqual("2025-11-12 시간대별 기록", until_midnight[0])

    def test_long_output_is_cut_at_300_lines(self):
        self.ingest([
            notification("a-%d" % number, "k%d" % number, "com.kakao.talk", "카카오톡", local_ms(7, 0) + number, "t", "n%d" % number)
            for number in range(305)
        ])

        lines = self.query("notifications", "2025-11-12").splitlines()

        self.assertEqual(301, len(lines))
        self.assertEqual("외 5줄", lines[-1])


# 문자 열이 생기기 전 스크립트가 만들던 records 표 (현재 SCHEMA와 무관하게 고정)
OLD_RECORDS_TABLE = """
CREATE TABLE records (
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
)
"""
EXPECTED_SMS_COLUMNS = ["message_key", "direction", "peers", "subject", "is_mms", "attachments"]


class SmsTest(UsageLogTestCase):

    def ingest_two_sms(self):
        self.ingest([
            sms("s-1", local_ms(9, 0), "received", [{"address": "01012345678", "name": "홍길동"}],
                "첫 줄\n둘째 줄", subject="모임 안내", mms=True, attachments=1),
            sms("s-2", local_ms(9, 5), "sent", [{"address": "0212345678", "name": None}], "확인했습니다"),
        ])

    def create_old_database(self, with_sms_row=False):
        """옛 records 표에 알림 한 건(과 선택적으로 문자 한 건)이 든 DB를 만듦"""
        connection = sqlite3.connect(os.environ["USAGE_LOG_DB"])
        try:
            connection.execute(OLD_RECORDS_TABLE)
            connection.execute(
                "INSERT INTO records (id, type, t, local_t, tz_offset_minutes, seq, app_name, notification_key, title, text)"
                " VALUES ('n-1', 'notification', ?, ?, ?, 1, '카카오톡', 'key-1', '홍길동', '내일 몇 시?')",
                (local_ms(7, 35), local_ms(7, 35) + TIMEZONE_OFFSET_MINUTES * 60_000, TIMEZONE_OFFSET_MINUTES))
            if with_sms_row:
                connection.execute(
                    "INSERT INTO records (id, type, t, local_t, tz_offset_minutes, seq, text)"
                    " VALUES ('s-old', 'sms', ?, ?, ?, 1, '옛 스크립트가 쌓은 문자')",
                    (local_ms(9, 0), local_ms(9, 0) + TIMEZONE_OFFSET_MINUTES * 60_000, TIMEZONE_OFFSET_MINUTES))
            connection.commit()
        finally:
            connection.close()

    def table_columns(self):
        connection = sqlite3.connect(os.environ["USAGE_LOG_DB"])
        try:
            return [item[1] for item in connection.execute("PRAGMA table_info(records)")]
        finally:
            connection.close()

    def test_01_sms_is_stored_and_listed(self):
        """
        받은 MMS와 보낸 SMS를 적재해 조회

        Given 받은 MMS와 보낸 SMS를 적재했을 때
        When sms 명령으로 그날을 조회하면
        Then 시각, 방향, 상대, 제목, 본문, 첨부가 한 줄씩 나옴
        """
        # Given
        self.ingest_two_sms()

        # When
        output = self.query("sms", "2025-11-12")

        # Then
        self.assertEqual([
            "09:00 받음 홍길동(01012345678) [모임 안내] 첫 줄 ⏎ 둘째 줄 · 첨부 1",
            "09:05 보냄 0212345678 확인했습니다",
        ], output.splitlines())

    def test_02_who_and_search_filter_sms(self):
        """
        --who와 --search로 문자 거르기

        Given 문자 두 건을 적재했을 때
        When --who(이름, 하이픈 섞은 번호)와 --search(제목, 본문)로 조회하면
        Then 조건에 맞는 문자만 나오고 없으면 안내 문구가 나옴
        """
        # Given
        self.ingest_two_sms()

        # When
        by_name = self.query("sms", "2025-11-12", "--who", "홍길동").splitlines()
        by_number = self.query("sms", "2025-11-12", "--who", "010-1234").splitlines()
        by_subject = self.query("sms", "2025-11-12", "--search", "모임").splitlines()
        by_text = self.query("sms", "2025-11-12", "--search", "확인").splitlines()
        none = self.query("sms", "2025-11-12", "--who", "김철수")

        # Then
        self.assertEqual(1, len(by_name))
        self.assertIn("홍길동", by_name[0])
        self.assertEqual(by_name, by_number)
        self.assertEqual(by_name, by_subject)
        self.assertEqual(1, len(by_text))
        self.assertIn("보냄", by_text[0])
        self.assertEqual("조건에 맞는 문자 없음", none.strip())

    def test_03_same_message_key_is_stored_once(self):
        """
        같은 messageKey는 한 번만 적재

        Given messageKey가 같고 id가 다른 문자 두 건을
        When 한 번에 적재하면
        Then 새 기록 1건, 중복 1건으로 한 줄만 쌓임
        """
        # Given
        peers = [{"address": "01012345678", "name": None}]
        records = [
            sms("s-1", local_ms(9, 0), "received", peers, "안녕", key="sms:1"),
            sms("s-2", local_ms(9, 0), "received", peers, "안녕", key="sms:1"),
        ]

        # When
        output = self.ingest(records)

        # Then
        self.assertEqual("새 기록 1건, 중복 1건", output.strip())
        self.assertEqual(1, len(self.query("sms", "2025-11-12").splitlines()))

    def test_04_range_query_shows_date(self):
        """
        기간 조회는 날짜를 붙임

        Given 문자를 적재했을 때
        When 기간으로 조회하면
        Then 시각 앞에 날짜가 붙음
        """
        # Given
        self.ingest_two_sms()

        # When
        output = self.query("sms", "2025-11-11~2025-11-12")

        # Then
        self.assertTrue(output.splitlines()[0].startswith("11-12 09:00 "))

    def test_05_empty_content_and_missing_peer_are_labeled(self):
        """
        내용·상대가 없는 문자의 표시

        Given 본문·제목이 없는 문자와 상대가 없는 문자를 적재했을 때
        When 조회하면
        Then 각각 (내용 없음)과 (상대 없음)으로 나옴
        """
        # Given
        self.ingest([
            sms("s-1", local_ms(9, 0), "received", [{"address": "01011112222", "name": None}], None),
            sms("s-2", local_ms(9, 5), "sent", [], "본문만"),
        ])

        # When
        lines = self.query("sms", "2025-11-12").splitlines()

        # Then
        self.assertEqual("09:00 받음 01011112222 (내용 없음)", lines[0])
        self.assertEqual("09:05 보냄 (상대 없음) 본문만", lines[1])

    def test_06_old_database_is_migrated(self):
        """
        옛 DB에 문자 열을 더하고 색인으로 중복을 거름

        Given 문자 열이 없는 옛 records 표와 알림 한 건이 있는 DB가
        When 문자를 적재하면
        Then 옛 알림 조회는 그대로이고 새 열 6개와 유일 색인이 생겨 같은 messageKey는 중복 처리됨
        """
        # Given
        self.create_old_database()
        before = self.query("notifications", "2025-11-12")
        peers = [{"address": "01012345678", "name": None}]

        # When
        self.ingest_two_sms()
        duplicate = self.ingest([sms("s-dup", local_ms(9, 0), "received", peers, "중복", key="sms:s-1:%d" % local_ms(9, 0))])

        # Then
        self.assertEqual("07:35 카카오톡 홍길동: 내일 몇 시?", before.strip())
        self.assertEqual(before, self.query("notifications", "2025-11-12"))
        columns = self.table_columns()
        for name in EXPECTED_SMS_COLUMNS:
            self.assertIn(name, columns)
        self.assertEqual("새 기록 0건, 중복 1건", duplicate.strip())
        self.assertEqual(2, len(self.query("sms", "2025-11-12").splitlines()))

    def test_07_sms_row_of_old_script_is_readable_without_ingest(self):
        """
        문자 열이 없는 옛 DB의 문자 행 조회

        Given 문자 열이 없는 DB에 옛 스크립트가 쌓은 문자 행이 있을 때
        When ingest 없이 sms를 조회하면
        Then 오류 없이 상대 없음과 방향 모름으로 나옴
        """
        # Given
        self.create_old_database(with_sms_row=True)

        # When
        output = self.query("sms", "2025-11-12")

        # Then
        self.assertEqual("09:00 (방향 모름) (상대 없음) 옛 스크립트가 쌓은 문자", output.strip())

    def test_08_who_compares_digits_only_for_phone_like_needle(self):
        """
        --who 숫자 비교는 전화번호 모양일 때만

        Given 이름에 숫자가 든 상대와 번호만 있는 상대의 문자를 적재했을 때
        When --who 학원2와 --who 010-1234로 조회하면
        Then 학원2는 이름 일치만, 010-1234는 번호 일치만 걸림
        """
        # Given
        self.ingest([
            sms("s-1", local_ms(9, 0), "received", [{"address": "01022223333", "name": "학원"}], "일반 학원"),
            sms("s-2", local_ms(9, 5), "received", [{"address": "01099992222", "name": "학원2"}], "두 번째 학원"),
        ])

        # When
        by_name = self.query("sms", "2025-11-12", "--who", "학원2").splitlines()
        by_number = self.query("sms", "2025-11-12", "--who", "010-9999").splitlines()

        # Then
        self.assertEqual(1, len(by_name))
        self.assertIn("두 번째 학원", by_name[0])
        self.assertEqual(1, len(by_number))
        self.assertIn("두 번째 학원", by_number[0])

    def test_09_who_treats_country_code_82_as_zero(self):
        """
        국가번호 82와 0을 같게 비교

        Given 국가번호 형식(+82)으로 저장된 번호와 국내 형식으로 저장된 번호가 있을 때
        When 반대 형식으로 --who 조회하면
        Then 둘 다 걸림
        """
        # Given
        self.ingest([
            sms("s-1", local_ms(9, 0), "received", [{"address": "+821012345678", "name": None}], "국제 형식"),
            sms("s-2", local_ms(9, 5), "received", [{"address": "01087654321", "name": None}], "국내 형식"),
        ])

        # When
        by_domestic = self.query("sms", "2025-11-12", "--who", "010-1234-5678").splitlines()
        by_international = self.query("sms", "2025-11-12", "--who", "+82 10-8765-4321").splitlines()
        by_partial = self.query("sms", "2025-11-12", "--who", "821").splitlines()

        # Then
        self.assertEqual(1, len(by_domestic))
        self.assertIn("국제 형식", by_domestic[0])
        self.assertEqual(1, len(by_international))
        # 82로 시작하는 짧은 번호 조각은 국가번호로 바꾸지 않아 01이 든 모든 번호에 걸리지 않음
        self.assertEqual(1, len(by_partial))
        self.assertIn("국제 형식", by_partial[0])
        self.assertIn("국내 형식", by_international[0])

    def test_10_malformed_peers_do_not_break_query(self):
        """
        깨진 peers 값이 있는 행

        Given peers가 JSON이 아닌 행, 목록이 아닌 행, 이름·번호가 숫자이고 객체 아닌 항목이 든 행이 섞여 있을 때
        When sms를 조회하고 --who로 거르면
        Then 오류 없이 모든 행이 나오고 걸러도 깨지지 않음
        """
        # Given
        self.ingest([
            sms("s-1", local_ms(9, 0), "received", [{"address": "01011112222", "name": None}], "정상"),
            sms("s-2", local_ms(9, 1), "received", [], "JSON 아님"),
            sms("s-3", local_ms(9, 2), "received", [], "목록 아님"),
        ])
        connection = sqlite3.connect(os.environ["USAGE_LOG_DB"])
        try:
            connection.execute("UPDATE records SET peers = '{not json' WHERE id = 's-2'")
            connection.execute("UPDATE records SET peers = '{\"address\": \"01011112222\"}' WHERE id = 's-3'")
            connection.execute(
                "INSERT INTO records (id, type, t, local_t, tz_offset_minutes, text, direction, peers)"
                " VALUES ('s-4', 'sms', ?, ?, ?, '숫자 이름과 번호, 객체 아닌 항목', 'sent',"
                " '[{\"address\": 123, \"name\": 5}, \"문자열\", 7]')",
                (local_ms(9, 3), local_ms(9, 3) + TIMEZONE_OFFSET_MINUTES * 60_000, TIMEZONE_OFFSET_MINUTES))
            connection.commit()
        finally:
            connection.close()

        # When
        lines = self.query("sms", "2025-11-12").splitlines()
        filtered = self.query("sms", "2025-11-12", "--who", "0101111").splitlines()

        # Then
        self.assertEqual(4, len(lines))
        self.assertEqual("09:00 받음 01011112222 정상", lines[0])
        self.assertEqual("09:01 받음 (상대 없음) JSON 아님", lines[1])
        self.assertEqual("09:02 받음 (상대 없음) 목록 아님", lines[2])
        self.assertEqual("09:03 보냄 (상대 없음) 숫자 이름과 번호, 객체 아닌 항목", lines[3])
        self.assertEqual(["09:00 받음 01011112222 정상"], filtered)

    def test_11_who_and_search_combine(self):
        """
        --who와 --search 동시 사용

        Given 같은 상대의 문자 두 건과 다른 상대의 문자 한 건이 있을 때
        When --who와 --search를 함께 주면
        Then 둘 다 맞는 문자만 나옴
        """
        # Given
        peers = [{"address": "01012345678", "name": "홍길동"}]
        self.ingest([
            sms("s-1", local_ms(9, 0), "received", peers, "점심 약속"),
            sms("s-2", local_ms(9, 5), "received", peers, "저녁 약속"),
            sms("s-3", local_ms(9, 10), "received", [{"address": "01099998888", "name": "김철수"}], "점심 약속"),
        ])

        # When
        lines = self.query("sms", "2025-11-12", "--who", "홍길동", "--search", "점심").splitlines()

        # Then
        self.assertEqual(["09:00 받음 홍길동(01012345678) 점심 약속"], lines)

    def test_12_migration_rolls_back_on_error(self):
        """
        마이그레이션 실패 시 롤백

        Given 문자 열이 없는 옛 DB에서 마지막 열 추가가 실패하도록 잘못된 열 정의를 끼웠을 때
        When migrate를 실행하면
        Then 오류가 나도 열이 반쯤 더해진 채 남지 않음
        """
        # Given
        self.create_old_database()
        connection = sqlite3.connect(os.environ["USAGE_LOG_DB"])
        try:
            with mock.patch.object(usage_log, "SMS_COLUMNS", usage_log.SMS_COLUMNS + (("broken", "NOT A TYPE ((",),)):
                # When
                with self.assertRaises(sqlite3.Error):
                    usage_log.migrate(connection)
            # Then
            columns = [item[1] for item in connection.execute("PRAGMA table_info(records)")]
        finally:
            connection.close()
        for name in EXPECTED_SMS_COLUMNS:
            self.assertNotIn(name, columns)

    def test_13_who_keeps_zero_written_after_country_code(self):
        """
        국가번호 뒤에 0을 붙여 쓴 번호 비교

        Given 국가번호 형식과 국내 형식으로 저장된 같은 번호, 다른 번호가 있을 때
        When --who +82 010-1234-5678로 조회하면
        Then 0을 한 번만 붙여 비교해 같은 번호 두 건만 걸림
        """
        # Given
        self.ingest([
            sms("s-1", local_ms(9, 0), "received", [{"address": "+821012345678", "name": None}], "국제 형식"),
            sms("s-2", local_ms(9, 5), "received", [{"address": "01012345678", "name": None}], "국내 형식"),
            sms("s-3", local_ms(9, 10), "received", [{"address": "01087654321", "name": None}], "다른 번호"),
        ])

        # When
        lines = self.query("sms", "2025-11-12", "--who", "+82 010-1234-5678").splitlines()

        # Then
        self.assertEqual(2, len(lines))
        self.assertIn("국제 형식", lines[0])
        self.assertIn("국내 형식", lines[1])

    def test_14_who_skips_digits_when_only_country_code_is_given(self):
        """
        국가번호만 준 검색어는 숫자 비교 안 함

        Given 국가번호 형식과 국내 형식으로 저장된 번호가 있을 때
        When --who +82와 --who +82 1로 조회하면
        Then 0이나 01이 든 번호에 걸리지 않고, +82는 번호 글자에 +82가 든 국가번호 형식만 걸림
        """
        # Given
        self.ingest([
            sms("s-1", local_ms(9, 0), "received", [{"address": "+821012345678", "name": None}], "국제 형식"),
            sms("s-2", local_ms(9, 5), "received", [{"address": "01087654321", "name": None}], "국내 형식"),
        ])

        # When
        country_code_only = self.query("sms", "2025-11-12", "--who", "+82").splitlines()
        one_digit_after = self.query("sms", "2025-11-12", "--who", "+82 1")

        # Then
        self.assertEqual(1, len(country_code_only))
        self.assertIn("국제 형식", country_code_only[0])
        self.assertEqual("조건에 맞는 문자 없음", one_digit_after.strip())


class SqlTest(UsageLogTestCase):

    def count_records(self):
        return int(self.query("sql", "SELECT COUNT(*) AS total FROM records").splitlines()[1])

    def test_sql_rejects_write(self):
        self.ingest([simple("a-1", "screen_on", local_ms(8, 0))])

        statements = [
            "DELETE FROM records",
            "WITH removed AS (SELECT 1) DELETE FROM records",
            "SELECT 1; DELETE FROM records",
            "UPDATE records SET type = 'x'",
            "DROP TABLE records",
            "SELECT 1; SELECT 2",
        ]
        results = [self.run_command("sql", statement) for statement in statements]

        for code, output, errors in results:
            self.assertNotEqual(0, code)
            self.assertEqual("", output)
            self.assertTrue(errors.startswith("오류: "))
            self.assertNotIn("Traceback", errors)
        self.assertEqual(1, self.count_records())

    def test_sql_select_is_limited_to_200_rows(self):
        self.ingest([simple("a-%d" % number, "screen_on", local_ms(8, 0) + number) for number in range(250)])

        lines = self.query("sql", "SELECT id FROM records ORDER BY id;").splitlines()

        self.assertEqual("id", lines[0])
        self.assertEqual(200, len([line for line in lines[1:] if not line.startswith("외 ")]))
        self.assertTrue(lines[-1].startswith("외 더 있음"))

    def test_sql_stops_endless_query_within_time_limit(self):
        self.ingest([simple("a-1", "screen_on", local_ms(8, 0))])
        statement = ("SELECT (WITH RECURSIVE counter(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM counter) "
                     "SELECT COUNT(*) FROM counter) AS total")

        with mock.patch.object(usage_log, "SQL_TIME_LIMIT_SECONDS", 0.3):
            started = time.monotonic()
            code, output, errors = self.run_command("sql", statement)
            elapsed = time.monotonic() - started

        self.assertEqual((1, "", "오류: SQL 실행이 0.3초를 넘어 중단했습니다. 조건을 좁혀 주세요.\n"), (code, output, errors))
        self.assertLess(elapsed, 3)

    def test_sql_values_keep_one_line_and_columns(self):
        self.ingest([notification("a-1", "k1", "com.kakao.talk", "카카오톡", local_ms(7, 35), "탭\t제목", "줄\r\n바꿈")])

        lines = self.query("sql", "SELECT title, text FROM records").splitlines()

        self.assertEqual(["title\ttext", "탭 제목\t줄 ⏎ 바꿈"], lines)


class OutputFormatTest(UsageLogTestCase):

    def test_output_has_no_machine_values(self):
        self.ingest([
            wifi("a-1", "wifi_connected", local_ms(7, 0)),
            simple("a-2", "screen_on", local_ms(7, 30)),
            simple("a-3", "unlock", local_ms(7, 30) + 5000),
            app_usage("a-4", "com.kakao.talk", "카카오톡", local_ms(7, 31), local_ms(7, 41)),
            notification("a-5", "0|com.kakao.talk|2|null|10164", "com.kakao.talk", "카카오톡", local_ms(7, 35), "홍길동", "내일 몇 시?"),
            removal("a-6", "0|com.kakao.talk|2|null|10164", "com.kakao.talk", local_ms(7, 40), "click"),
            wifi("a-7", "wifi_disconnected", local_ms(8, 30)),
            sms("a-9", local_ms(9, 0), "received", [{"address": "01012345678", "name": "홍길동"}], "안녕", key="sms:key-9"),
            sms("a-10", local_ms(9, 5), "sent", [{"address": "0212345678", "name": None}], "네", key="sms:key-10"),
            {"id": "a-8", "type": "gap", "start": local_ms(13, 10), "end": local_ms(14, 0),
             "cause": "usage_events_expired", "t": local_ms(13, 10)},
        ])
        commands = [
            ("summary", "2025-11-12"),
            ("timeline", "2025-11-12"),
            ("notifications", "2025-11-12"),
            ("notifications", "2025-11-11~2025-11-12"),
            ("home", "2025-11-12"),
            ("apps", "2025-11-11~2025-11-12"),
            ("sms", "2025-11-12"),
            ("sms", "2025-11-11~2025-11-12"),
        ]
        forbidden = [r"\d{13}", r"com\.kakao", r"com\.black", r"0\|com", r"\btzOffsetMinutes\b", r"\bappName\b",
                     r'"type"', r"\bnotification_removed\b", r"\bpackage\b",
                     r"sms:key", r"\breceived\b", r"\bsent\b", r"\bmessageKey\b", r"\bpeers\b"]

        for command in commands:
            output = self.query(*command)
            self.assertTrue(output.strip(), command)
            for pattern in forbidden:
                self.assertIsNone(re.search(pattern, output), (command, pattern, output))


if __name__ == "__main__":
    unittest.main()
