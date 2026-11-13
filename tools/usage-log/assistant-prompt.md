# 폰 사용 기록 조회 설명문

폰 사용 기록(앱 사용, 화면, 알림, 집 도착·출발)은 PC에 쌓여 있다. 사용자가 요청할 때만 아래 명령으로 필요한 범위만 꺼내 읽는다. 요청이 없으면 조회하지 않는다. 원본 파일이나 DB를 직접 읽지 않는다.

```text
python3 usage_log.py summary 2026-11-13
python3 usage_log.py timeline 2026-11-13 09:00 18:00
python3 usage_log.py notifications 2026-11-13 --app 앱이름 --search 단어
python3 usage_log.py notifications 2026-11-13 --new
python3 usage_log.py home 2026-11-13
python3 usage_log.py apps 2026-11-07~2026-11-13 --app 앱이름
python3 usage_log.py sql "SELECT ..."
```

- 하루 요약은 summary, 시간대별 흐름은 timeline, 알림은 notifications, 집 도착·출발은 home, 앱 사용 시간 추이는 apps를 쓴다. sql은 위 명령으로 안 될 때만 쓴다(SELECT만, 최대 200행).
- 결과 끝에 `외 N줄`이 있으면 날짜·시간대·`--app`·`--search`로 좁혀 다시 조회한다.
- "새로 온 알림"은 `notifications --new`를 쓴다. 이미 읽은 알림은 다시 나오지 않는다. `외 N줄`이 나오면 `--new`를 다시 불러 이어 받는다. `--app`·`--search`와 함께 쓰지 않는다.
- 알림은 거르거나 묶거나 자르지 않고 조회된 그대로 다룬다. `열어 봄`은 사용자가 이미 연 알림, `지움`은 사용자가 지운 알림이다.
