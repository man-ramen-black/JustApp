package com.black.app.usagelog.collect

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.black.app.usagelog.record.SmsRecord
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

/** 시간 범위의 받은·보낸 문자 조회 */
fun interface SmsSource {
    /**
     * [begin] 이상 [end] 미만에 저장된 받은·보낸 문자를 시각 오름차순으로 조회
     *
     * 문자 읽기 권한이 없거나 조회에 실패하면 null.
     * MMS는 초 단위 시각이라 [begin]을 초로 내림해 조회하므로 [begin] 앞 1초 미만의 MMS가 섞일 수 있음.
     */
    fun query(begin: Long, end: Long): List<SmsRecord>?
}

/**
 * ContentResolver 기반 [SmsSource]
 *
 * 문자 읽기 권한이 없으면 null. 연락처 권한이 있으면 번호마다 연락처 이름을 찾고 한 번의 조회 안에서 다시 찾지 않음.
 * 조회 예외는 클래스 이름만 남기고 null(본문·번호가 섞일 수 있어 메시지는 남기지 않음).
 */
class SystemSmsSource(private val context: Context) : SmsSource {

    override fun query(begin: Long, end: Long): List<SmsRecord>? {
        if (!PermissionHelper.isGranted(context, Manifest.permission.READ_SMS)) {
            // 권한 거부는 사용자가 고른 정상 상태라 수집 때마다 경고하지 않음
            Log.d("[SystemSmsSource] 문자 읽기 권한 없어 조회하지 않음")
            return null
        }
        val names = ContactNames(context)
        return try {
            (querySms(begin, end, names::find) + queryMms(begin, end, names::find)).sortedBy { it.time }
        } catch (failure: Exception) {
            Log.w("[SystemSmsSource] 문자 조회 실패: ${failure::class.java.simpleName}")
            null
        }
    }

    private fun querySms(begin: Long, end: Long, nameOf: (String) -> String?): List<SmsRecord> {
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.TYPE, Telephony.Sms.DATE, Telephony.Sms.ADDRESS, Telephony.Sms.BODY)
        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ? AND ${Telephony.Sms.TYPE} IN (" +
            "${SmsRecordRules.SMS_TYPE_INBOX}, ${SmsRecordRules.SMS_TYPE_SENT})"
        val records = ArrayList<SmsRecord>()
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, selection, arrayOf(begin.toString(), end.toString()), null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val row = SmsRow(cursor.getLong(0), cursor.getInt(1), cursor.getLong(2), cursor.getString(3), cursor.getString(4))
                    SmsRecordRules.fromSms(row, nameOf)?.let(records::add)
                }
            }
        return records
    }

    private fun queryMms(begin: Long, end: Long, nameOf: (String) -> String?): List<SmsRecord> {
        val projection = arrayOf(
            Telephony.Mms._ID, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_TYPE, Telephony.Mms.DATE,
            Telephony.Mms.SUBJECT, Telephony.Mms.SUBJECT_CHARSET,
        )
        val selection = "${Telephony.Mms.DATE} >= ? AND ${Telephony.Mms.DATE} < ? AND ${Telephony.Mms.MESSAGE_BOX} IN (" +
            "${SmsRecordRules.MMS_BOX_INBOX}, ${SmsRecordRules.MMS_BOX_SENT})"
        // MMS 시각은 초 단위라 끝을 올림해 경계 안 문자를 빠뜨리지 않음(남는 것은 수집기가 거름)
        val arguments = arrayOf((begin / 1000).toString(), ((end + 999) / 1000).toString())
        val records = ArrayList<SmsRecord>()
        context.contentResolver.query(Telephony.Mms.CONTENT_URI, projection, selection, arguments, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                // m_type이 null이면 0(mmsDirection이 거절), sub_cs가 null이면 null(제목을 그대로 사용)
                val row = MmsRow(
                    cursor.getLong(0), cursor.getInt(1), cursor.getInt(2), cursor.getLong(3),
                    cursor.getString(4), if (cursor.isNull(5)) null else cursor.getInt(5),
                )
                // 기록하지 않는 행은 파트·주소를 읽지 않음
                if (SmsRecordRules.mmsDirection(row.messageBox, row.messageType) == null) continue
                // 한 문자의 파트·주소 읽기 실패가 전체 조회를 막지 않도록 읽은 만큼으로 기록을 만듦
                val parts = readOrEmpty("파트") { mmsParts(row.id) }
                val addresses = readOrEmpty("주소") { mmsAddresses(row.id) }
                SmsRecordRules.fromMms(row, parts, addresses, nameOf)?.let(records::add)
            }
        }
        return records
    }

    /** [read]가 실패하면 예외 클래스 이름만 로그로 남기고 빈 목록 */
    private fun <T> readOrEmpty(label: String, read: () -> List<T>): List<T> = try {
        read()
    } catch (failure: Exception) {
        Log.w("[SystemSmsSource] MMS $label 조회 실패: ${failure::class.java.simpleName}")
        emptyList()
    }

    private fun mmsParts(messageId: Long): List<MmsPart> {
        val parts = ArrayList<MmsPart>()
        context.contentResolver.query(
            MMS_PART_URI,
            arrayOf(
                Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT,
                Telephony.Mms.Part._DATA, Telephony.Mms.Part.CHARSET,
            ),
            "${Telephony.Mms.Part.MSG_ID} = ?", arrayOf(messageId.toString()), Telephony.Mms.Part._ID,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contentType = cursor.getString(1)
                var text = cursor.getString(2)
                // 일부 제조사는 본문을 text 열 대신 파일(_data)에 저장
                if (text == null && cursor.getString(3) != null && contentType.equals("text/plain", ignoreCase = true)) {
                    text = readPartFile(cursor.getLong(0), if (cursor.isNull(4)) null else cursor.getInt(4))
                }
                parts.add(MmsPart(contentType, text))
            }
        }
        return parts
    }

    /**
     * 파트 파일 본문을 파트 문자 집합으로 읽고, 실패하면 null
     *
     * 파트 문자 집합이 없거나 모르는 값이면 UTF-8로 읽음.
     */
    private fun readPartFile(partId: Long, charsetMibEnum: Int?): String? = try {
        val charset = charsetMibEnum?.let(SmsRecordRules::charsetOf) ?: Charsets.UTF_8
        context.contentResolver.openInputStream(Uri.withAppendedPath(MMS_PART_URI, partId.toString()))
            ?.use { String(it.readBytes(), charset) }
    } catch (failure: Exception) {
        Log.w("[SystemSmsSource] MMS 파트 파일 읽기 실패: ${failure::class.java.simpleName}")
        null
    }

    private fun mmsAddresses(messageId: Long): List<MmsAddress> {
        val addresses = ArrayList<MmsAddress>()
        context.contentResolver.query(
            Uri.parse("content://mms/$messageId/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) addresses.add(MmsAddress(cursor.getString(0), cursor.getInt(1)))
        }
        return addresses
    }

    private companion object {
        // Telephony.Mms.Part.CONTENT_URI는 API 29부터라서 쓰지 않음
        val MMS_PART_URI: Uri = Uri.parse("content://mms/part")
    }
}

/**
 * 번호의 연락처 표시 이름 조회
 *
 * 연락처 권한이 없으면 항상 null. 같은 번호는 다시 조회하지 않음.
 */
private class ContactNames(private val context: Context) {
    private val granted = PermissionHelper.isGranted(context, Manifest.permission.READ_CONTACTS)
    private val cache = HashMap<String, String?>()

    fun find(address: String): String? {
        if (!granted) return null
        // getOrPut은 null 결과를 캐시하지 않아 직접 확인
        return if (address in cache) cache[address] else lookup(address).also { cache[address] = it }
    }

    private fun lookup(address: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null }
    } catch (failure: Exception) {
        Log.w("[SystemSmsSource] 연락처 조회 실패: ${failure::class.java.simpleName}")
        null
    }
}
