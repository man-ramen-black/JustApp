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
        self.assertEqual("새 기록 18건, 중복 0건", self.ingest_sample(sample_folder))
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
        ]
        forbidden = [r"\d{13}", r"com\.kakao", r"com\.black", r"0\|com", r"\btzOffsetMinutes\b", r"\bappName\b",
                     r'"type"', r"\bnotification_removed\b", r"\bpackage\b"]

        for command in commands:
            output = self.query(*command)
            self.assertTrue(output.strip(), command)
            for pattern in forbidden:
                self.assertIsNone(re.search(pattern, output), (command, pattern, output))


if __name__ == "__main__":
    unittest.main()
