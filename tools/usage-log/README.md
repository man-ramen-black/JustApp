# 기기 사용 기록 PC 쪽 안내

## 1. 무엇인가

폰의 JustApp 앱이 앱 사용 내역, 화면 켜짐·꺼짐, 받은 알림, Wi-Fi 연결·해제(집 도착·출발)를 모아 1시간마다, 그리고 집 도착·출발 때 전용 디스코드 채널로 보냅니다. 메시지 하나는 평문 머리말 한 줄과 암호화된 첨부 파일 하나입니다.

이 폴더는 그 메시지를 집 PC에서 받아 쌓고 꺼내 쓰는 방법을 설명합니다.

- 복호화, 중복 제거, 저장은 모두 코드(`usage_log.py`)가 합니다. 메시지가 올 때마다 LLM을 부르지 않으므로 토큰을 쓰지 않습니다.
- 개인비서는 사용자가 요청할 때만 조회 명령으로 필요한 범위를 꺼내 읽습니다.

폴더 구성은 다음과 같습니다.

- `usage_log.py`: 복호화, SQLite 적재, 조회 명령
- `README.md`: 이 문서
- `assistant-prompt.md`: 비서의 시스템 프롬프트나 도구 설명에 붙여 넣을 설명문

## 2. 설치

- [ ] Python 3를 준비합니다.
- [ ] `pip install cryptography`를 실행합니다.
- [ ] `usage_log.py`를 봇이 실행할 수 있는 위치에 둡니다. 봇과 비서가 같은 파일을 쓰도록 경로를 하나로 정합니다.
- [ ] 환경 변수를 설정합니다.

| 환경 변수 | 설명 | 기본값 |
|---|---|---|
| `USAGE_LOG_KEY` | 암호화 키(Base64 문자열 하나) | 없음, 필수 |
| `USAGE_LOG_DB` | SQLite 파일 위치 | `~/.usage_log/usage_log.sqlite3` |
| `USAGE_LOG_HOME_DEBOUNCE_MINUTES` | 집 Wi-Fi가 해제된 뒤 이 시간(분) 안에 다시 연결되면 떠난 것으로 보지 않는 기준 | `3` |

### 키 옮기기

키는 폰 앱의 기기 사용 기록 화면에서 전송, 암호화 키, 복사 순서로 눌러 복사합니다. 이 키를 `USAGE_LOG_KEY`에 넣습니다.

- 키를 기록이 올라오는 디스코드 채널로 옮기면 암호화가 의미 없어집니다. 그 채널이 아닌 다른 경로로 옮깁니다.
- 앱에서 키를 새로 만들면 그 뒤 전송분부터 새 키로 암호화됩니다. PC의 `USAGE_LOG_KEY`도 같이 바꿔야 합니다.
- 키를 바꾸기 전에 온 첨부를 다시 풀어야 하면 그 첨부는 옛 키가 필요하므로 옛 키를 따로 보관합니다.

## 3. 메시지 형식

### 메시지와 첨부

메시지 본문(content)에는 JSON 한 줄뿐입니다.

```json
{"kind":"justapp-usage-log","v":1,"seq":123}
```

- `seq`는 전송 순번입니다.
- 기록 내용은 첨부 파일 하나 `usage-log-<seq>.bin`에 있습니다. 첨부가 8MB를 넘으면 여러 메시지로 나뉘어 옵니다.
- 첨부 구조는 앞 12바이트가 nonce이고 그 뒤가 AES-256-GCM 암호문입니다. 암호문 끝 16바이트가 인증 태그입니다.
- 복호화한 평문은 gzip으로 압축된 JSON Lines입니다. 압축을 풀면 한 줄이 JSON 객체 하나입니다.
- 키 없이 만든 가짜 메시지는 인증 태그 검사에서 거부됩니다.

### 평문의 줄

첫 줄은 `meta`이고 그 뒤로 줄마다 기록 하나입니다. 시각은 모두 epoch 밀리초입니다.

```json
{"type":"meta","v":1,"seq":123,"sentAt":1763000000000,"reason":"home_leave","device":"SM-S921N","app":"com.black.app","tzOffsetMinutes":540}
{"id":"n-4821","type":"notification","t":1762999000000,"key":"0|com.example.chat|2|null|10164","package":"com.example.chat","appName":"채팅앱","title":"홍길동","text":"내일 몇 시?","category":"msg"}
```

`meta` 줄의 필드는 다음과 같습니다.

| 필드 | 설명 |
|---|---|
| `seq`, `sentAt` | 전송 순번과 보낸 시각 |
| `reason` | `periodic`, `home_arrive`, `home_leave`, `manual` 중 하나 |
| `device` | 폰 모델 |
| `app` | 보낸 앱의 패키지. 실제 앱은 `com.black.app`, 개발 앱은 `com.black.app.dev` |
| `tzOffsetMinutes` | 폰의 UTC 오프셋(분). 기록 시각을 현지 시각으로 바꿀 때 씁니다. |

기록 줄의 공통 필드는 `id`, `type`, `t`(기록 시각)입니다. 종류별 필드는 다음과 같습니다.

| `type` | 공통 필드 외 필드 | 설명 |
|---|---|---|
| `app_usage` | `package`, `appName`, `start`, `end` | 앱 사용 구간. `t`는 `start`와 같습니다. |
| `screen_on`, `screen_off`, `unlock` | 없음 | 화면 켜짐, 꺼짐, 잠금 해제 |
| `notification` | `key`, `package`, `appName`, `title`, `text`, `category` | 제목·본문·카테고리가 없으면 `null`입니다. |
| `notification_removed` | `key`, `package`, `reason` | `reason`은 `click`, `dismiss`, `app_cancel`, `other` 중 하나입니다. |
| `wifi_connected` | `ssid`, `home`, `inferred` | `home`은 `true`, `false`, `null` 중 하나입니다. |
| `wifi_disconnected` | `ssid`, `home`, `inferred` | `ssid`와 `home`은 직전에 연결되어 있던 Wi-Fi 기준입니다. |
| `gap` | `start`, `end`, `cause` | 기록이 없는 구간입니다. `cause`는 `collection_off`(수집을 끈 기간), `usage_events_expired`(시스템 보관 기간이 지나 앱 사용·화면 기록을 잃은 기간), `unsent_deleted`(보내기 전에 30일이 지나 지운 기록의 범위) 중 하나입니다. "폰을 쓰지 않음"이 아니라 "기록 없음"으로 다룹니다. |

- 집 도착은 `home`이 `true`인 `wifi_connected`, 집 출발은 `home`이 `true`인 `wifi_disconnected`입니다.
- `id`는 기록마다 고유합니다. 같은 기록이 다시 오는 경우는 디스코드가 메시지를 받았는데 응답이 폰에 닿지 못했을 때뿐이며, `id`로 걸러 한 번만 저장합니다.
- 폰은 Wi-Fi 연결·해제를 거르지 않고 있는 그대로 보냅니다. 공유기 재시작 같은 짧은 끊김은 PC 쪽(6절의 3분 규칙)에서 처리합니다.

### 개발 앱과 시험용 채널

개발 앱(`com.black.app.dev`)으로 시험할 때는 실제 채널과 다른 시험용 웹후크와 채널을 씁니다. 같은 채널에 섞여도 `meta`의 `app` 값으로 구별할 수 있습니다.

## 4. 전용 채널과 봇 연동

기록은 사용자와 대화하는 채널이 아니라 이 기능 전용 채널로 옵니다. 대화 채널에 올라오면 전송마다 LLM 대화가 생길 수 있어서 채널을 나눕니다.

봇이 할 일은 다음과 같습니다.

1. 전용 채널의 메시지 중 content가 JSON으로 읽히고 `kind`가 `justapp-usage-log`인 것만 처리합니다.
2. 첨부 파일을 임시 파일로 내려받습니다.
3. `python3 usage_log.py ingest <파일>`을 실행합니다. 성공하면 `새 기록 N건, 중복 M건`만 출력합니다.
4. ingest가 성공한 뒤에만 그 메시지를 삭제합니다. 실패하면 지우지 않고 남깁니다.
5. 이 메시지들에는 LLM을 부르지 않습니다.

웹후크가 올린 메시지의 본문과 첨부를 읽으려면 디스코드 개발자 포털의 봇 설정에서 Message Content Intent를 켜야 합니다. 봇에게는 이 채널의 메시지 읽기, 메시지 기록 보기, 메시지 관리(삭제) 권한이 필요합니다.

## 5. 놓치지 않기

폰은 디스코드가 메시지를 받았다고 응답한 기록을 다시 보내지 않습니다. 그래서 봇이 새로 오는 메시지만 처리하면, PC나 봇이 꺼져 있는 동안 채널에 올라온 기록이 `gap` 표시도 없이 조용히 빠집니다.

이를 막는 방법은 다음과 같습니다.

- 봇은 켜질 때 채널에 남아 있는 메시지를 오래된 것부터 모두 읽어 `ingest`합니다. `channel.history(limit=None, oldest_first=True)`를 씁니다.
- 그 뒤 새로 오는 메시지는 `on_message`로 처리합니다.
- 처리한 메시지는 삭제하므로 채널에 남은 메시지가 곧 아직 처리하지 않은 기록입니다. 별도의 처리 목록이 필요 없습니다.
- 같은 메시지를 두 번 ingest해도 `id` 중복 거르기가 막으므로 안전합니다. 켜지는 중에 온 메시지와 거슬러 읽기가 겹칠 수 있으므로 예시는 잠금과 처리한 메시지 ID 목록으로 한 번만 처리하고, 이미 지워진 메시지는 처리가 끝난 것으로 봅니다.

discord.py 2.x 예시입니다. 채널 ID와 경로만 바꿔 씁니다.

```python
import asyncio
import json
import os
import subprocess
import tempfile

import discord

CHANNEL_ID = 123456789012345678  # 전용 채널 ID(예시 값)
SCRIPT = "/path/to/usage_log.py"  # 예시 경로

intents = discord.Intents.default()
intents.message_content = True  # Message Content Intent
client = discord.Client(intents=intents)
backfilled = False
lock = asyncio.Lock()  # 한 번에 한 메시지만 처리
done_ids = set()  # 이번 실행에서 처리를 마친 메시지 ID


def is_usage_log(message):
    if message.channel.id != CHANNEL_ID or not message.attachments:
        return False
    try:
        return json.loads(message.content).get("kind") == "justapp-usage-log"
    except (ValueError, AttributeError):
        return False


async def process(message):
    """ingest가 성공했을 때만 메시지를 지운다. 복호화한 내용은 출력하지 않는다."""
    attachment = message.attachments[0]
    with tempfile.TemporaryDirectory() as folder:
        path = os.path.join(folder, "usage-log.bin")
        await attachment.save(path)
        # 이벤트 루프를 막지 않도록 별도 스레드에서 실행
        await asyncio.to_thread(
            subprocess.run, ["python3", SCRIPT, "ingest", path], check=True
        )
    await message.delete()


async def try_process(message):
    async with lock:
        if message.id in done_ids:  # on_message와 거슬러 읽기가 겹친 경우
            return
        try:
            await process(message)
            done_ids.add(message.id)
        except discord.NotFound:  # 이미 지워졌으면 처리가 끝난 것
            done_ids.add(message.id)
        except (subprocess.CalledProcessError, discord.HTTPException) as error:
            print("처리 실패, 메시지를 남겨 둠:", message.id, type(error).__name__)


@client.event
async def on_ready():
    global backfilled
    if backfilled:  # on_ready는 재연결 때 다시 불리므로 한 번만 실행
        return
    backfilled = True
    channel = client.get_channel(CHANNEL_ID) or await client.fetch_channel(CHANNEL_ID)
    async for message in channel.history(limit=None, oldest_first=True):
        if is_usage_log(message):
            await try_process(message)


@client.event
async def on_message(message):
    if is_usage_log(message):
        await try_process(message)


client.run(os.environ["DISCORD_BOT_TOKEN"])
```

`try_process`는 어떤 오류가 나도 예외를 밖으로 내보내지 않으므로 거슬러 읽는 반복이 중간에 멈추지 않습니다. 이미 봇이 있다면 `is_usage_log`가 참인 메시지를 위 처리로 보내는 부분과 켜질 때 거슬러 읽는 부분만 기존 봇에 옮깁니다. 거짓인 메시지는 이 코드가 건드리지 않습니다. ingest나 내려받기가 실패하면 메시지가 남으므로 다음에 봇이 켜질 때 다시 처리됩니다.

## 6. 쌓기와 조회 원칙

- 쌓기는 코드로만 합니다. 봇은 머리말의 `kind`로 메시지를 알아보고 LLM 없이 복호화, 중복 제거, SQLite 저장까지 합니다.
- 비서는 사용자가 요청할 때만 기록을 읽습니다. 정해 둔 시각의 자동 요약, 기록이 들어올 때마다의 반응, 집 도착·출발 때의 LLM 호출은 하지 않습니다.
- 비서는 원본 파일이나 테이블 전체를 읽지 않습니다. 7절의 조회 명령으로 요청에 필요한 날짜·앱·검색어 범위만 꺼냅니다.
- 조회 결과에는 기계용 값(패키지 이름, 알림 `key`, epoch 시각, JSON 필드 이름)을 넣지 않고 앱 이름, `14:05` 형식의 시각, 내용만 씁니다. 정보는 빼지 않고 표기만 줄입니다.
- 알림은 모두 쌓고 조회에서도 거르지 않습니다. 눌러서 연 알림, 광고성 앱의 알림도 빼지 않고, 같은 대화방에서 이어서 온 알림도 묶지 않으며, 본문을 자르지 않습니다. 범위는 사용자의 요청에 따라 조회 조건으로만 좁힙니다.
- 제거 기록이 있는 알림은 줄 끝에 `열어 봄`(눌러서 엶) 또는 `지움`(사용자가 직접 지움)이 붙습니다. 앱이 스스로 거둔 알림이나 그 밖의 제거에는 표시가 붙지 않습니다. 비서가 중요한 알림을 고를 때 사용자가 이미 본 알림인지 알 수 있습니다.
- `home` 조회는 집 Wi-Fi가 해제된 뒤 3분 안에 다시 연결되면 집을 떠난 것으로 보지 않습니다. 3분은 기본값이고 `USAGE_LOG_HOME_DEBOUNCE_MINUTES`로 바꿉니다.
- 집 도착·출발에 LLM 없는 규칙 동작(조명 켜기 등)을 붙인다면 같은 3분 기준으로 확정된 이벤트에만 실행합니다. 이 규칙 동작은 `usage_log.py`에 들어 있지 않습니다. 집 도착·출발 때는 폰이 즉시 전송합니다.
- 폰은 아직 보내지 않은 기록만 1시간마다 보내므로, 평소 비서가 보는 기록은 최대 1시간 늦을 수 있습니다.

## 7. 조회 명령 사용 예

날짜는 `YYYY-MM-DD`, 기간은 `2026-11-01~2026-11-07` 형식입니다. 아래 명령은 `usage_log.py`가 있는 위치에서 실행합니다.

```text
python3 usage_log.py summary 2026-11-13
python3 usage_log.py timeline 2026-11-13 09:00 18:00
python3 usage_log.py notifications 2026-11-13 --app 채팅앱 --search 약속
python3 usage_log.py notifications 2026-11-13 --new
python3 usage_log.py home 2026-11-13
python3 usage_log.py apps 2026-11-07~2026-11-13 --app 유튜브
python3 usage_log.py sql "SELECT type, COUNT(*) FROM records GROUP BY type"
```

| 명령 | 돌려주는 내용 |
|---|---|
| `summary <날짜>` | 하루 합계. 앱별 사용 시간 상위 10개, 화면 켠 횟수, 잠금 해제 횟수, 첫 사용과 마지막 사용 시각, 집에 있던 시간, `gap` 구간. 약 300토큰입니다. |
| `timeline <날짜> [시작 시각] [끝 시각]` | 시간대별 한 줄. 집 또는 밖, 주로 쓴 앱과 사용 시간, `gap` 구간. 시각을 생략하면 하루 전체입니다. |
| `notifications <날짜 또는 기간> [--app 앱 이름] [--search 검색어] [--new]` | 조건에 맞는 알림 전부. 한 줄에 시각, 앱 이름, 제목, 본문 전체, `열어 봄`·`지움` 표시. |
| `home <날짜 또는 기간>` | 집에 있던 구간 목록과 지금 집에 있는지. |
| `apps <기간> [--app 앱 이름]` | 기간 동안 앱별 사용 시간 합계와 날짜별 추이. |
| `sql "<SELECT 문>"` | 읽기 전용 조회 결과. SELECT만 허용하고 최대 200행입니다. 위 명령으로 답할 수 없을 때만 씁니다. |

- `--new`는 이전 `--new` 조회 뒤에 새로 쌓인 알림만 돌려줍니다. 조회 위치는 DB에 저장되며, 마지막으로 출력한 알림까지만 앞으로 옮겨집니다. 그래서 "새로 온 알림 정리해 줘"를 여러 번 요청해도 같은 알림을 다시 읽지 않고, 결과가 `외 N줄`로 잘렸으면 다음 `--new`가 잘린 곳 바로 뒤부터 이어서 돌려주므로 빠지는 알림이 없습니다.
- `--new`는 `--app`, `--search`와 함께 쓸 수 없고 함께 쓰면 오류입니다.
- 결과가 300줄을 넘으면 앞 300줄과 `외 N줄`만 출력합니다. 날짜, 시간대, `--app`, `--search`로 범위를 좁혀 다시 조회합니다.
- 시각은 각 기록이 들어온 묶음의 `tzOffsetMinutes`로 바꾼 현지 시각입니다.

## 8. 문제 확인

첨부 하나가 제대로 풀리는지 확인할 때는 `decrypt`를 씁니다. 평문 JSON Lines를 그대로 출력하며 DB에는 저장하지 않습니다.

```text
python3 usage_log.py decrypt usage-log-123.bin
```

- 키가 맞지 않거나 첨부가 손상되었거나 키 없이 만든 가짜 첨부면 오류로 끝납니다.
- 오류가 나면 먼저 `USAGE_LOG_KEY`가 앱의 현재 키와 같은지 확인합니다. 앱에서 키를 새로 만든 적이 있다면 그 전에 온 첨부는 옛 키로만 풀립니다.
- `decrypt` 출력에는 알림 본문 같은 실제 내용이 들어 있으므로 화면에 남기거나 다른 곳에 붙여 넣지 않습니다.
