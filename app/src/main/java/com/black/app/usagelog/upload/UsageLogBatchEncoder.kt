package com.black.app.usagelog.upload

import com.black.app.model.StoredUsageLogRecord
import com.black.app.usagelog.record.UsageLogRecordCodec
import com.black.core.util.JsonUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * 한 번의 전송에 공통인 맥락
 *
 * [installId]는 meta 줄에 들어가지 않고 기록 id의 접두사로만 쓰임.
 */
data class BatchContext(
    val sentAt: Long,
    val reason: SendReason,
    val device: String,
    val appPackage: String,
    val tzOffsetMinutes: Int,
    val installId: String,
)

/** 웹후크에 올릴 암호화된 묶음 하나 */
class EncodedBatch(
    val sequence: Long,
    val header: String,
    val fileName: String,
    val recordIds: List<Long>,
    val attachment: ByteArray,
)

/** 첨부 맨 앞 줄의 메타 정보 */
@Serializable
private data class BatchMeta(
    val type: String = "meta",
    val v: Int,
    val seq: Long,
    val sentAt: Long,
    val reason: String,
    val device: String,
    @SerialName("app") val appPackage: String,
    val tzOffsetMinutes: Int,
)

/** 웹후크 메시지 본문에 실리는 머리말 */
@Serializable
private data class BatchHeader(
    val kind: String = "justapp-usage-log",
    val v: Int,
    val seq: Long,
)

/**
 * 미전송 기록을 JSON Lines, gzip, AES-GCM 순으로 변환한 전송 묶음 생성
 *
 * 첨부 한도를 넘으면 기록을 앞뒤 절반으로 나눠 묶음 여러 개를 만듦.
 */
object UsageLogBatchEncoder {

    const val MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024
    private const val FORMAT_VERSION = 1

    /** meta 줄에 기록 줄을 이어 붙인 평문이며 줄마다 끝에 "\n" */
    fun plainText(sequence: Long, context: BatchContext, records: List<StoredUsageLogRecord>): String {
        val meta = BatchMeta(
            v = FORMAT_VERSION,
            seq = sequence,
            sentAt = context.sentAt,
            reason = context.reason.value,
            device = context.device,
            appPackage = context.appPackage,
            tzOffsetMinutes = context.tzOffsetMinutes,
        )
        val builder = StringBuilder()
        builder.append(toJson(meta)).append('\n')
        records.forEach { stored ->
            builder.append(UsageLogRecordCodec.toJsonLine("${context.installId}-${stored.id}", stored.record)).append('\n')
        }
        return builder.toString()
    }

    /**
     * [records]를 순번 [firstSequence]부터 이어지는 묶음 목록으로 변환
     *
     * 기록 하나가 혼자서 한도를 넘으면 더 나눌 수 없으므로 그대로 한 묶음으로 만듦.
     */
    fun encode(
        records: List<StoredUsageLogRecord>,
        firstSequence: Long,
        context: BatchContext,
        key: ByteArray,
        maxAttachmentBytes: Int = MAX_ATTACHMENT_BYTES,
        nonceProvider: () -> ByteArray = UsageLogCipher::randomNonce,
    ): List<EncodedBatch> {
        val batch = encodeSingle(records, firstSequence, context, key, nonceProvider)
        if (batch.attachment.size <= maxAttachmentBytes || records.size < 2) return listOf(batch)

        // 한도 초과 시 앞뒤 절반으로 나눠 순번을 이어 붙임
        val middle = records.size / 2
        val front = encode(records.subList(0, middle), firstSequence, context, key, maxAttachmentBytes, nonceProvider)
        val back = encode(
            records.subList(middle, records.size), firstSequence + front.size, context, key, maxAttachmentBytes, nonceProvider,
        )
        return front + back
    }

    private fun encodeSingle(
        records: List<StoredUsageLogRecord>,
        sequence: Long,
        context: BatchContext,
        key: ByteArray,
        nonceProvider: () -> ByteArray,
    ): EncodedBatch {
        val compressed = gzip(plainText(sequence, context, records).toByteArray(Charsets.UTF_8))
        return EncodedBatch(
            sequence = sequence,
            header = toJson(BatchHeader(v = FORMAT_VERSION, seq = sequence)),
            fileName = "usage-log-$sequence.bin",
            recordIds = records.map { it.id },
            attachment = UsageLogCipher.encrypt(compressed, key, nonceProvider()),
        )
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private inline fun <reified T> toJson(value: T): String =
        checkNotNull(JsonUtil.to<T>(value, true, JsonUtil.kotlinSerializationCompact))
}
