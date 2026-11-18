package com.black.app.assistanttask.notion

/**
 * 노션 `Todo` 데이터베이스의 속성 이름과 선택 값
 *
 * 앱은 속성을 이름으로 찾으므로 노션에서 이름을 바꾸면 여기도 함께 바꿈.
 * 종류 선택 값은 ChatGPT, 노션 앱, 디스코드 비서가 새 항목에 쓰는 `Todo`와 맞춤(예전 값 `할 일`로 등록된 항목도 메모가 아니면 Todo로 읽음).
 */
object NotionTaskProperties {
    const val TITLE = "이름"
    const val KIND = "종류"
    const val DONE = "완료"
    const val DUE = "기한"
    const val REMINDER = "알림"
    const val CONTENT = "내용"

    const val KIND_TASK = "Todo"
    const val KIND_MEMO = "메모"

    const val REMINDER_HOME_ARRIVAL = "집 도착"
    const val REMINDER_TIME = "시각"
    const val REMINDER_NONE = "없음"
}
