# 기기 사용 기록 PC 쪽 안내

## 1. 무엇인가

폰의 JustApp 앱이 앱 사용 내역, 화면 켜짐·꺼짐, 받은 알림, Wi-Fi 연결·해제(집 도착·출발)를 모아 1시간마다, 그리고 집 도착·출발 때 전용 디스코드 채널로 보냅니다. 메시지 하나는 평문 머리말 한 줄과 암호화된 첨부 파일 하나입니다.

이 폴더는 그 메시지를 집 PC에서 받아 쌓고 꺼내 쓰는 방법을 설명합니다.

- 복호화, 중복 제거, 저장은 모두 코드(`usage_log.py`)가 합니다. 메시지가 올 때마다 LLM을 부르지 않으므로 토큰을 쓰지 않습니다.
- 개인비서는 사용자가 요청할 때만 조회 명령으로 필요한 범위를 꺼내 읽습니다.

폴더 구성은 다음과 같습니다.

- `usage_log.py`: 복호화, SQLite 적재, 조회 명령
- `test_usage_log.py`: `usage_log.py` 테스트(2절 끝의 실행 방법 참고)
- `README.md`: 이 문서
- `assistant-prompt.md`: 비서의 시스템 프롬프트나 도구 설명에 붙여 넣을 설명문. 붙여 넣기 전에 `<인터프리터>`와 `<usage_log.py 절대 경로>`를 이 PC의 값으로 바꿉니다(예: `py`와 `C:\Users\<사용자>\StudioProjects\JustApp\tools\usage-log\usage_log.py`).

## 2. 설치(Windows)

- [ ] python.org에서 Python 3.9 이상을 설치합니다. 설치 화면의 `py launcher` 항목을 켜 두면 `py` 명령으로 실행할 수 있습니다.
    - winget이 있으면 `winget install --id Python.Python.3.14 -e --scope user`로 설치 화면 없이 설치해도 됩니다. `py` 런처가 함께 설치됩니다.
    - 설치 전에 떠 있던 봇과 터미널은 다시 시작해야 PATH의 `py`를 찾습니다.
- [ ] 명령 프롬프트나 PowerShell에서 `py -m pip install cryptography`를 실행합니다.
- [ ] `usage_log.py`는 복사하지 않고 저장소 안의 경로를 그대로 씁니다. 봇과 비서가 같은 절대 경로(예: `C:\Users\<사용자>\StudioProjects\JustApp\tools\usage-log\usage_log.py`)를 가리키게 하면 `git pull`만으로 둘 다 새 버전을 씁니다.
- [ ] 환경 변수를 설정합니다(아래 표와 "Windows에서 환경 변수 설정").

실행은 `<인터프리터> <usage_log.py 절대 경로> <명령>`으로 하며, 이 문서의 예시는 `py`로 적습니다. 인터프리터는 다음 중 하나입니다.

- `py`: python.org 설치본의 기본
- `python`: `py`가 없을 때
- `python3`: Windows가 아닌 PC

| 환경 변수 | 설명 | 기본값 |
|---|---|---|
| `USAGE_LOG_KEY` | 암호화 키(Base64 문자열 하나). `ingest`와 `decrypt`에만 필요합니다. | 없음, 봇에 필수 |
| `USAGE_LOG_DB` | SQLite 파일 위치 | `~/.usage_log/usage_log.sqlite3` |
| `USAGE_LOG_APP` | 쌓을 묶음을 보낸 앱 패키지. 다른 앱이 보낸 묶음은 `ingest`가 거부합니다. 시험용 채널에서 개발 앱 기록을 받을 때만 `com.black.app.dev`로 바꿉니다. | `com.black.app` |
| `USAGE_LOG_HOME_DEBOUNCE_MINUTES` | 집 Wi-Fi가 해제된 뒤 이 시간(분) 안에 다시 연결되면 떠난 것으로 보지 않는 기준 | `3` |

### Windows에서 환경 변수 설정

```text
setx USAGE_LOG_KEY "<앱에서 복사한 키>"
setx USAGE_LOG_DB "C:\Users\<사용자>\.usage_log\usage_log.sqlite3"
```

- `setx`는 사용자 환경 변수에 저장하고, 그 뒤에 새로 시작한 프로그램부터 적용됩니다. 이미 떠 있는 봇, 터미널, Claude Code·Codex는 다시 시작해야 새 값을 봅니다.
- 봇과 비서는 같은 `USAGE_LOG_DB`를 봐야 합니다. 봇이 쌓은 DB를 비서가 조회하기 때문입니다. 같은 Windows 사용자로 실행하면 기본값도 같지만, 봇을 서비스나 다른 계정으로 실행한다면 두 쪽 모두 같은 절대 경로를 지정합니다.
- `USAGE_LOG_KEY`는 봇에만 있으면 됩니다. 비서의 조회 명령은 키 없이 동작합니다.
- 출력은 언제나 UTF-8입니다. PowerShell에서 결과의 한글이 깨지면 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`을 먼저 실행합니다.

### 키 옮기기

키는 폰 앱의 기기 사용 기록 화면에서 전송, 암호화 키, 복사 순서로 눌러 복사합니다. 이 키를 `USAGE_LOG_KEY`에 넣습니다.

- 키를 기록이 올라오는 디스코드 채널로 옮기면 암호화가 의미 없어집니다. 그 채널이 아닌 다른 경로로 옮깁니다.
- 앱에서 키를 새로 만들면 그 뒤 전송분부터 새 키로 암호화됩니다. PC의 `USAGE_LOG_KEY`도 같이 바꿔야 합니다.
- 키를 바꾸기 전에 온 첨부를 다시 풀어야 하면 그 첨부는 옛 키가 필요하므로 옛 키를 따로 보관합니다.

### 테스트 실행

`test_usage_log.py`는 테스트용 키로 만든 가짜 기록만 씁니다. 실제 기록 DB와 키는 건드리지 않습니다.

1. (선택) Android 빌드 환경이 있는 PC에서 앱이 만든 표본 첨부를 생성합니다. 저장소 루트에서 `.\gradlew :app:testDebugUnitTest --tests "com.black.app.usagelog.upload.UsageLogSampleExportTest"`를 실행하면 `app/build/usage-log-sample/`에 표본이 생깁니다.
2. 저장소 루트에서 `py -B tools/usage-log/test_usage_log.py`를 실행합니다.

- 표본 폴더가 없으면 표본 테스트(`test_decrypt_kotlin_sample`)만 건너뜁니다.
- 표본까지 꼭 확인해야 하는 검증에서는 환경 변수 `USAGE_LOG_REQUIRE_SAMPLE`을 `1`로 두고 실행합니다. 그러면 표본이 없을 때 건너뛰지 않고 실패합니다. PowerShell에서는 `$env:USAGE_LOG_REQUIRE_SAMPLE = "1"`을 먼저 실행합니다.

## 3. 메시지 형식

### 메시지와 첨부

메시지 본문(content)에는 JSON 한 줄뿐입니다.

```json
{"kind":"justapp-usage-log","v":1,"seq":123}
```

- `seq`는 전송 순번입니다. 반복될 수 있으므로 중복 거르기에 쓰지 않습니다(아래 "순번과 중복 거르기").
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
| `v` | 형식 버전. `ingest`는 `1`만 받고 다른 값이면 거부합니다. |
| `seq`, `sentAt` | 전송 순번과 보낸 시각 |
| `reason` | `periodic`, `home_arrive`, `home_leave`, `manual` 중 하나 |
| `device` | 폰 모델 |
| `app` | 보낸 앱의 패키지. 실제 앱은 `com.black.app`, 개발 앱은 `com.black.app.dev`입니다. `ingest`는 `USAGE_LOG_APP`과 같은 값만 받습니다. |
| `tzOffsetMinutes` | 보낼 때 폰의 UTC 오프셋(분). 묶음 안의 모든 기록 시각을 현지 시각으로 바꿀 때 씁니다. |

기록 줄의 공통 필드는 `id`, `type`, `t`(기록 시각)입니다. 종류별 필드는 다음과 같습니다.

| `type` | 공통 필드 외 필드 | 설명 |
|---|---|---|
| `app_usage` | `package`, `appName`, `start`, `end` | 앱 사용 구간. `t`는 `start`와 같습니다. |
| `screen_on`, `screen_off`, `unlock` | 없음 | 화면 켜짐, 꺼짐, 잠금 해제 |
| `notification` | `key`, `package`, `appName`, `title`, `text`, `category` | 제목·본문·카테고리가 없으면 `null`입니다. |
| `notification_removed` | `key`, `package`, `reason` | `reason`은 `click`, `dismiss`, `app_cancel`, `other` 중 하나입니다. |
| `wifi_connected` | `ssid`, `home`, `inferred` | `home`은 `true`, `false`, `null` 중 하나입니다. SSID를 읽지 못하면(위치 권한·위치 설정 없음) `ssid`와 `home`이 `null`입니다. |
| `wifi_disconnected` | `ssid`, `home`, `inferred` | `ssid`와 `home`은 직전에 연결되어 있던 Wi-Fi 기준입니다. |
| `gap` | `start`, `end`, `cause` | 기록이 없는 구간입니다. `cause`는 `collection_off`(수집을 끈 기간), `usage_events_expired`(시스템 보관 기간이 지나 앱 사용·화면 기록을 잃은 기간), `unsent_deleted`(보내기 전에 30일이 지나 지운 기록의 범위) 중 하나입니다. "폰을 쓰지 않음"이 아니라 "기록 없음"으로 다룹니다. |

- 집 도착은 `home`이 `true`인 `wifi_connected`, 집 출발은 `home`이 `true`인 `wifi_disconnected`입니다.
- 폰은 Wi-Fi 연결·해제를 거르지 않고 있는 그대로 보냅니다. 공유기 재시작 같은 짧은 끊김은 PC 쪽(6절의 3분 규칙)에서 처리합니다.

`inferred`의 뜻은 다음과 같습니다.

- `false`: 폰이 연결·해제를 그 순간에 보고 남긴 기록입니다.
- `true`: 폰이 변화를 그 순간에 보지 못하고 나중에 알아채 남긴 추정 기록입니다. 알림 접근 서비스가 다시 연결되거나 수집을 다시 켤 때 저장된 상태와 지금 상태가 다른 경우, 집 Wi-Fi 등록을 바꾼 경우, 위치 권한을 나중에 허용해 SSID가 뒤늦게 보인 경우에 남깁니다. 시각은 알아챈 시각이라 실제 연결·해제는 그보다 이를 수 있습니다. `home` 조회는 이 시각에 `(추정)`을 붙입니다.

### 순번과 중복 거르기

- `id`는 기록마다 고유합니다. 설치마다 한 번 만드는 임의 ID와 기록 번호를 이은 값이라, 앱을 다시 설치해도 겹치지 않습니다. `ingest`는 `id`로 걸러 같은 기록을 한 번만 저장합니다.
- `seq`는 반복될 수 있습니다. 디스코드가 메시지를 받았는데 응답이 폰에 닿지 못하면 폰은 같은 순번으로 다시 보내고, 앱 데이터를 지우거나 다시 설치하면 순번이 1부터 다시 시작합니다. 그래서 중복 거르기는 `seq`가 아니라 기록 `id`로 합니다.
- 순번이 건너뛴 것을 찾아 알리는 기능은 없습니다.

### 개발 앱과 시험용 채널

개발 앱(`com.black.app.dev`)으로 시험할 때는 실제 채널과 다른 시험용 웹후크와 채널을 씁니다. 같은 채널에 섞여도 `meta`의 `app` 값으로 구별되며, 기본 설정의 `ingest`는 개발 앱의 묶음을 거부하고 메시지를 남깁니다. 시험용 채널을 처리하는 봇과 DB는 `USAGE_LOG_APP=com.black.app.dev`와 별도의 `USAGE_LOG_DB`로 실제 기록과 나눕니다.

## 4. 전용 채널과 봇 연동

기록은 사용자와 대화하는 채널이 아니라 이 기능 전용 채널로 옵니다. 대화 채널에 올라오면 전송마다 LLM 대화가 생길 수 있어서 채널을 나눕니다.

### 봇 연동 체크리스트

봇을 만든 언어와 관계없이 지킬 항목입니다. 집 PC의 봇은 Node.js(discord.js)이며, 5절의 discord.py 예시는 같은 흐름을 보여 주는 참고용입니다.

- [ ] 기존 봇의 메시지 처리 맨 앞에서 전용 채널의 메시지를 걸러 AI 처리(Claude Code·Codex 세션 만들기, 답장 등)로 넘어가지 않게 합니다. 다른 어떤 처리보다 이 검사를 먼저 둡니다.
- [ ] 디스코드 개발자 포털의 봇 설정에서 Message Content Intent를 켜고, 코드에서도 같은 intent를 요청합니다(discord.js는 `GatewayIntentBits.MessageContent`). 없으면 웹후크 메시지의 본문과 첨부를 읽을 수 없습니다.
- [ ] 봇에게 이 채널의 채널 보기, 메시지 기록 보기, 메시지 관리(삭제) 권한을 줍니다.
- [ ] content가 JSON으로 읽히고 `kind`가 `justapp-usage-log`이며 첨부가 있는 메시지만 처리합니다. 나머지는 건드리지 않습니다.
- [ ] 첨부를 임시 파일로 내려받아 `py <usage_log.py 절대 경로> ingest <파일>`을 실행합니다. 이벤트 처리를 막지 않도록 비동기로 실행합니다(Node.js는 `child_process.execFile`).
- [ ] ingest의 종료 코드가 0일 때만 메시지를 삭제합니다. 실패하면 메시지를 남겨 다음 거슬러 읽기에서 다시 처리합니다.
- [ ] ingest의 출력은 UTF-8입니다. 성공하면 `새 기록 N건, 중복 M건` 한 줄, 실패하면 표준 오류에 `오류: ...` 한 줄이며 키와 기록 내용은 들어 있지 않습니다.
- [ ] 봇이 처음 연결될 때와 다시 연결될 때마다 채널에 남은 메시지를 오래된 것부터 모두 읽어 처리합니다(5절).
    - Discord API는 메시지 목록을 한 번에 최대 100개 줍니다. `after`(처음은 0, 다음부터는 받은 메시지 중 가장 큰 ID)로 페이지를 넘기고, 받은 묶음은 메시지 ID 순으로 정렬해 오래된 것부터 처리합니다.
    - discord.js라면 새 세션이 준비될 때마다 오는 `shardReady` 이벤트에서 실행합니다. 끊긴 세션을 이어 붙이는 재연결은 놓친 이벤트를 디스코드가 다시 보내 줍니다.
- [ ] 한 번에 한 메시지씩 처리하고, 이번 실행에서 처리를 마친 메시지 ID를 기억해 새 메시지 이벤트와 거슬러 읽기가 겹쳐도 한 번만 처리합니다. 두 번 ingest돼도 기록 `id`로 걸러지므로 데이터는 안전합니다.
- [ ] 삭제 단계에서 메시지가 이미 없다는 응답(404)을 받으면 처리가 끝난 것으로 봅니다. 내려받기 단계의 404는 실패로 다룹니다.
- [ ] ingest가 몇 번(예: 3번) 연달아 실패하면 사용자가 보는 곳(DM 등)으로 알립니다. 앱에서 키를 새로 만들었거나 `USAGE_LOG_APP`이 맞지 않으면 모든 메시지가 실패하고 채널에 쌓이기만 합니다.
- [ ] 복호화한 내용, 첨부, 키를 로그에 남기거나 AI에 넘기지 않습니다.

## 5. 놓치지 않기

폰은 디스코드가 메시지를 받았다고 응답한 기록을 다시 보내지 않습니다. 그래서 봇이 새로 오는 메시지만 처리하면, PC나 봇이 꺼져 있거나 연결이 끊긴 동안 채널에 올라온 기록이 `gap` 표시도 없이 조용히 빠집니다.

이를 막는 방법은 다음과 같습니다.

- 봇은 연결될 때마다(처음 켜질 때와 재연결 때 모두) 채널에 남아 있는 메시지를 오래된 것부터 모두 읽어 `ingest`합니다.
    - discord.py의 `on_ready`는 처음 연결 때와, 끊긴 세션을 잇지 못해 새로 연결할 때마다 불립니다. 한 번만 실행하도록 막지 않고 매번 `channel.history(limit=None, oldest_first=True)`로 거슬러 읽습니다.
    - 끊긴 세션을 이어 붙인 재연결은 놓친 이벤트를 디스코드가 다시 보내 주므로 `on_message`로 처리됩니다.
- 그 뒤 새로 오는 메시지는 `on_message`로 처리합니다.
- 처리한 메시지는 삭제하므로 채널에 남은 메시지가 곧 아직 처리하지 않은 기록입니다. 별도의 처리 목록이 필요 없습니다.
- 같은 메시지를 두 번 ingest해도 `id` 중복 거르기가 막으므로 안전합니다. 예시는 잠금과 처리한 메시지 ID 목록으로 한 번만 처리합니다.
- ingest가 연달아 실패하면 알립니다. 실패한 메시지는 채널에 남아 원인을 고친 뒤 다음 거슬러 읽기에서 처리됩니다.

discord.py 2.x 예시입니다. 채널 ID와 경로만 바꿔 씁니다.

```python
import asyncio
import json
import os
import subprocess
import sys
import tempfile

import discord

CHANNEL_ID = 123456789012345678  # 전용 채널 ID(예시 값)
PYTHON = sys.executable  # cryptography를 설치한 Python. 봇의 Python과 다르면 그 python.exe 경로로 바꿈
SCRIPT = r"C:\Users\<사용자>\StudioProjects\JustApp\tools\usage-log\usage_log.py"  # 저장소의 usage_log.py(예시 경로)
ALERT_AFTER_FAILURES = 3  # 이 횟수만큼 연달아 실패하면 알림

intents = discord.Intents.default()
intents.message_content = True  # Message Content Intent
client = discord.Client(intents=intents)
lock = asyncio.Lock()  # 한 번에 한 메시지만 처리
done_ids = set()  # 이번 실행에서 처리를 마친 메시지 ID
failures = 0  # 연달아 실패한 횟수


def is_usage_log(message):
    if message.channel.id != CHANNEL_ID or not message.attachments:
        return False
    try:
        return json.loads(message.content).get("kind") == "justapp-usage-log"
    except (ValueError, AttributeError):
        return False


async def alert(text):
    """연달아 실패했을 때 사용자에게 알림. 예시는 출력만 하며, DM으로 바꿀 때도 기록 내용은 넣지 않음"""
    print(text)


async def ingest(message):
    """첨부를 임시 파일로 받아 ingest. 실패하면 예외"""
    attachment = message.attachments[0]
    with tempfile.TemporaryDirectory() as folder:
        path = os.path.join(folder, "usage-log.bin")
        await attachment.save(path)
        # 이벤트 루프를 막지 않도록 별도 스레드에서 실행. 출력은 UTF-8
        await asyncio.to_thread(
            subprocess.run, [PYTHON, SCRIPT, "ingest", path],
            check=True, capture_output=True, encoding="utf-8",
        )


async def try_process(message):
    """ingest가 성공했을 때만 메시지를 지움. 어떤 예외도 밖으로 내보내지 않음"""
    global failures
    async with lock:
        if message.id in done_ids:  # on_message와 거슬러 읽기가 겹친 경우
            return
        try:
            await ingest(message)
        except Exception as error:  # 내려받기, ingest 실패. 메시지를 남겨 다음 거슬러 읽기에서 다시 처리
            failures += 1
            reason = error.stderr.strip() if isinstance(error, subprocess.CalledProcessError) else type(error).__name__
            print("처리 실패, 메시지를 남겨 둠:", message.id, reason)
            if failures == ALERT_AFTER_FAILURES:
                await alert("기기 사용 기록 처리가 %d번 연달아 실패했습니다: %s" % (failures, reason))
            return
        failures = 0
        try:
            await message.delete()
        except discord.NotFound:  # 삭제 단계에서 이미 지워졌으면 처리가 끝난 것
            pass
        except Exception as error:  # 쌓기는 끝남. 남은 메시지는 다음 거슬러 읽기에서 중복으로 걸러진 뒤 다시 지움
            print("삭제 실패:", message.id, type(error).__name__)
            return
        done_ids.add(message.id)


@client.event
async def on_ready():
    # 처음 연결과, 끊긴 세션을 잇지 못해 새로 연결할 때마다 불림. 그동안 못 받은 메시지를 매번 거슬러 읽음
    try:
        channel = client.get_channel(CHANNEL_ID) or await client.fetch_channel(CHANNEL_ID)
        async for message in channel.history(limit=None, oldest_first=True):
            if is_usage_log(message):
                await try_process(message)
    except Exception as error:  # 채널을 읽지 못함. 남은 메시지는 다음 연결 때 다시 거슬러 읽음
        await alert("기기 사용 기록 채널을 거슬러 읽지 못했습니다: %s" % type(error).__name__)


@client.event
async def on_message(message):
    if is_usage_log(message):
        await try_process(message)


client.run(os.environ["DISCORD_BOT_TOKEN"])
```

예외 처리는 다음과 같습니다.

- `try_process`는 내려받기, ingest, 삭제에서 나는 모든 예외를 `except Exception`으로 잡아 밖으로 내보내지 않습니다. 그래서 거슬러 읽는 반복이 중간에 멈추지 않습니다.
- `discord.NotFound`는 삭제 단계에서 난 경우만 처리가 끝난 것으로 봅니다. 내려받기에서 난 `NotFound`는 다른 실패와 같이 메시지를 남깁니다.
- 실패 사유로는 `usage_log.py`가 표준 오류에 쓴 한 줄이나 예외 종류만 남깁니다. 이 한 줄에는 키와 기록 내용이 들어 있지 않습니다.

이미 봇이 있다면 `is_usage_log`가 참인 메시지를 위 처리로 보내는 부분과 연결될 때마다 거슬러 읽는 부분만 기존 봇에 옮깁니다. 거짓인 메시지는 이 코드가 건드리지 않습니다.

## 6. 쌓기와 조회 원칙

- 쌓기는 코드로만 합니다. 봇은 머리말의 `kind`로 메시지를 알아보고 LLM 없이 복호화, 중복 제거, SQLite 저장까지 합니다.
- 비서는 사용자가 요청할 때만 기록을 읽습니다. 정해 둔 시각의 자동 요약, 기록이 들어올 때마다의 반응, 집 도착·출발 때의 LLM 호출은 하지 않습니다.
- 비서는 원본 파일이나 테이블 전체를 읽지 않습니다. 7절의 조회 명령으로 요청에 필요한 날짜·앱·검색어 범위만 꺼냅니다.
- 조회 결과에는 기계용 값(패키지 이름, 알림 `key`, epoch 시각, JSON 필드 이름)을 넣지 않고 앱 이름, `14:05` 형식의 시각, 내용만 씁니다. 정보는 빼지 않고 표기만 줄입니다.
- 알림은 모두 쌓고 조회에서도 거르지 않습니다. 눌러서 연 알림, 광고성 앱의 알림도 빼지 않고, 같은 대화방에서 이어서 온 알림도 묶지 않으며, 본문을 자르지 않습니다. 범위는 사용자의 요청에 따라 조회 조건으로만 좁힙니다.
- 제거 기록이 있는 알림은 줄 끝에 표시가 붙습니다. 같은 알림을 앱이 고쳐 여러 번 게시했으면 그 게시들 모두에 붙습니다.
    - `열어 봄`: 사용자가 눌러서 엶
    - `지움`: 사용자가 직접 지움
    - 표시 없음: 앱이 스스로 거둔 알림, 그 밖의 제거, 아직 제거되지 않은 알림
- 집 여부는 집, 밖, 알 수 없음 셋으로 나눕니다. 알 수 없음은 집 밖으로 세지 않습니다.
    - 알 수 없음이 되는 경우: 첫 Wi-Fi 기록 전, SSID를 몰라 `home`이 `null`인 연결 동안, `collection_off`·`unsent_deleted` `gap` 동안, `unsent_deleted` 범위 뒤 다음 Wi-Fi 기록 전까지
    - `usage_events_expired` `gap`은 Wi-Fi 기록이 남아 있으므로 집 여부에 영향이 없습니다.
- `home` 조회는 집 Wi-Fi가 해제된 뒤 3분 안에 다시 연결되면 집을 떠난 것으로 보지 않습니다. 3분은 기본값이고 `USAGE_LOG_HOME_DEBOUNCE_MINUTES`로 바꿉니다.
- 집 도착·출발에 LLM 없는 규칙 동작(조명 켜기 등)을 붙인다면 같은 3분 기준으로 확정된 이벤트에만 실행합니다. 이 규칙 동작은 `usage_log.py`에 들어 있지 않습니다. 집 도착·출발 때는 폰이 즉시 전송합니다.
- 폰은 아직 보내지 않은 기록만 1시간마다 보내므로, 평소 비서가 보는 기록은 최대 1시간 늦을 수 있습니다. `summary`와 `home`은 마지막 기록 시각을 함께 출력합니다.

## 7. 조회 명령 사용 예

날짜는 `YYYY-MM-DD`, 기간은 `2026-11-01~2026-11-07` 형식입니다. `<usage_log.py>`는 `usage_log.py`의 절대 경로입니다.

```text
py <usage_log.py> summary 2026-11-13
py <usage_log.py> timeline 2026-11-13 09:00 18:00
py <usage_log.py> notifications 2026-11-13 --app 채팅앱 --search 약속
py <usage_log.py> notifications --new
py <usage_log.py> home 2026-11-13
py <usage_log.py> apps 2026-11-07~2026-11-13 --app 유튜브
py <usage_log.py> sql "SELECT type, COUNT(*) FROM records GROUP BY type"
```

| 명령 | 돌려주는 내용 |
|---|---|
| `summary <날짜>` | 하루 합계. 앱별 사용 시간 상위 10개, 화면 켠 횟수, 잠금 해제 횟수, 받은 알림 건수, 첫 사용과 마지막 사용 시각, 집에 있던 시간과 집 여부를 알 수 없는 시간, `gap` 구간, 마지막 기록 시각. 기간은 받지 않습니다. 약 300토큰입니다. |
| `timeline <날짜> [시작 시각] [끝 시각]` | 시간대별 한 줄. 집, 밖, 알 수 없음, 마지막 기록 뒤 중 가장 긴 상태와 주로 쓴 앱과 사용 시간, `gap` 구간. 시각은 `00:00`부터 `24:00`까지이고 생략하면 하루 전체입니다. 기간은 받지 않습니다. |
| `notifications <날짜 또는 기간> [--app 앱 이름] [--search 검색어]` | 조건에 맞는 알림 전부. 한 줄에 시각, 앱 이름, 제목, 본문 전체, `열어 봄`·`지움` 표시. |
| `notifications --new` | 지난 `--new` 뒤에 새로 쌓인 알림. 날짜와 함께 시각 순으로 출력합니다. |
| `home <날짜 또는 기간>` | 집에 있던 구간과 알 수 없는 구간 목록, 집 도착·출발 횟수, 마지막 기록 시각 기준 지금 집에 있는지. 추정 기록이 만든 시각에는 `(추정)`이 붙습니다. |
| `apps <기간> [--app 앱 이름]` | 기간 동안 앱별 사용 시간 합계와 날짜별 추이. |
| `sql "<SELECT 문>"` | 읽기 전용 조회 결과. 위 명령으로 답할 수 없을 때만 씁니다. |

`notifications --new`는 다음과 같이 동작합니다.

- 이전 `--new` 뒤에 쌓인 알림을 쌓인 순서로 300건까지 고른 뒤 시각 순으로 출력합니다. 그래서 "새로 온 알림 정리해 줘"를 여러 번 요청해도 같은 알림을 다시 읽지 않습니다.
- 날짜, `--app`, `--search`와 함께 쓸 수 없고 함께 쓰면 오류입니다. 자정 전후에 온 알림도 날짜 없이 한 번에 받습니다.
- 300건이 넘으면 끝에 `외 N줄`이 붙습니다. 다음 `--new`가 그 뒤부터 이어서 돌려주므로 빠지거나 반복되는 알림이 없습니다.
- 읽은 위치는 DB에 저장하며, 출력을 마친 뒤에만 옮깁니다. 출력이 실패하면 다음 `--new`가 같은 알림을 다시 돌려줍니다.
- `--new`만 DB에 씁니다. Codex처럼 샌드박스에서 명령을 실행하는 비서는 DB 폴더에 쓸 권한이 없을 수 있습니다. 그러면 알림은 출력되지만 `오류: 알림은 출력했지만 읽은 위치를 저장하지 못해...`로 끝나고 다음 `--new`가 같은 알림을 다시 돌려줍니다. DB 폴더 쓰기를 허용하거나 `--new`만 샌드박스 밖에서 실행합니다. 다른 조회 명령은 읽기만 하므로 영향이 없습니다.

`sql`은 다음과 같이 동작합니다.

- SELECT 하나만 실행하고 최대 200행을 돌려줍니다. 5초를 넘기면 중단하고 오류로 끝납니다.
- 표는 `records`입니다. 주요 열은 `type`, `app_name`, `title`, `text`, `ssid`, `home`, `inferred`, `cause`, 현지 시각 열 `local_t`·`local_start`·`local_end`(epoch 밀리초)입니다. `package_name`, `notification_key`, epoch 열 `t`·`start_t`·`end_t`도 고를 수 있지만 비서의 답에는 쓰지 않습니다.
- 열은 탭으로 나뉩니다. 값 안의 탭은 공백으로, 줄바꿈은 ` ⏎ `로 바꿉니다.

그 밖의 출력 규칙은 다음과 같습니다.

- 결과가 300줄을 넘으면 앞 300줄과 `외 N줄`만 출력합니다. 날짜, 시간대, `--app`, `--search`로 범위를 좁혀 다시 조회합니다.
- 시각은 각 기록이 들어온 묶음의 `tzOffsetMinutes`로 바꾼 현지 시각입니다(8절의 시간대 한계 참고).

## 8. 알아 둘 한계

- 묶음 하나에는 시간대 값(`tzOffsetMinutes`)이 하나만 있습니다. 폰은 보낼 때의 UTC 오프셋을 묶음 안의 모든 기록에 적용하므로, 해외에서 시간대가 바뀌거나 서머타임이 바뀐 직후에 보낸 묶음은 바뀌기 전 기록의 현지 시각이 몇 시간 어긋날 수 있습니다. epoch 열(`t`, `start_t`, `end_t`)은 정확합니다.
- `seq`는 반복될 수 있고, 건너뛴 순번은 감지하지 않습니다(3절).
- 기록은 최대 1시간 늦게 들어옵니다. 마지막 기록 시각 뒤의 일은 아직 모릅니다.

## 9. 문제 확인

첨부 하나가 제대로 풀리는지 확인할 때는 `decrypt`를 씁니다. 평문 JSON Lines를 그대로 출력하며 DB에는 저장하지 않습니다.

```text
py <usage_log.py> decrypt usage-log-123.bin
```

- 키가 맞지 않거나 첨부가 손상되었거나 키 없이 만든 가짜 첨부면 오류로 끝납니다.
- 오류가 나면 먼저 `USAGE_LOG_KEY`가 앱의 현재 키와 같은지 확인합니다. 앱에서 키를 새로 만든 적이 있다면 그 전에 온 첨부는 옛 키로만 풀립니다.
- `decrypt`는 풀리는데 `ingest`가 거부하면 `meta`의 `v`와 `app`을 확인합니다. `v`가 `1`이 아니면 이 스크립트를 새 버전으로 받아야 하고, `app`이 `USAGE_LOG_APP`과 다르면 다른 앱(개발 앱 등)이 보낸 묶음입니다.
- `decrypt` 출력에는 알림 본문 같은 실제 내용이 들어 있으므로 화면에 남기거나 다른 곳에 붙여 넣지 않습니다.
