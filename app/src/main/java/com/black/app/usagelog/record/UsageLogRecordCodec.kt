package com.black.app.usagelog.record

import com.black.core.util.JsonUtil
import kotlinx.serialization.json.JsonPrimitive

/** [UsageLogRecord]와 JSON 한 줄 사이의 변환 */
object UsageLogRecordCodec {

    /**
     * type을 포함한 JSON 한 줄
     *
     * null 필드는 생략하지 않고 null로 씀. DB의 json 열에 저장되는 값.
     */
    fun encode(record: UsageLogRecord): String =
        checkNotNull(JsonUtil.to<UsageLogRecord>(record, true, JsonUtil.kotlinSerializationCompact))

    /**
     * [encode]한 JSON으로 기록 복원
     *
     * 복원할 수 없으면 null.
     */
    fun decode(json: String): UsageLogRecord? =
        JsonUtil.from<UsageLogRecord>(json, true, JsonUtil.kotlinSerializationCompact, logInput = false)

    /**
     * 전송용 JSON 한 줄
     *
     * [encode] 결과의 맨 앞에 id를 붙여 키 순서는 id, type, 나머지.
     */
    fun toJsonLine(id: String, record: UsageLogRecord): String =
        "{\"id\":${JsonPrimitive(id)}," + encode(record).removePrefix("{")
}
