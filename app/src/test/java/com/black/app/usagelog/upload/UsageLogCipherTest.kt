package com.black.app.usagelog.upload

import android.util.Base64
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.security.GeneralSecurityException

/** [UsageLogCipher]의 AES-GCM 암복호화와 키 문자열 변환 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogCipherTest : BaseTest() {

    /**
     * 암호화한 값을 같은 키로 복호화하면 원문이 나오는지 검증
     *
     * Given: 생성한 키와 평문
     * When: encrypt 후 decrypt 호출
     * Then: 원문과 같은 바이트
     */
    @Test
    fun test_01_encryptDecryptRoundTrip() = runTest {
        /** Given **/
        val key = UsageLogCipher.generateKey()
        val plain = "사용 기록 원문".toByteArray()

        /** When **/
        val decrypted = UsageLogCipher.decrypt(UsageLogCipher.encrypt(plain, key), key)

        /** Then **/
        assertEquals(UsageLogCipher.KEY_SIZE_BYTES, key.size)
        assertArrayEquals(plain, decrypted)
    }

    /**
     * 다른 키로 복호화하면 실패하는지 검증
     *
     * Given: 한 키로 암호화한 값
     * When: 다른 키로 decrypt 호출
     * Then: GeneralSecurityException
     */
    @Test
    fun test_02_wrongKeyFails() = runTest {
        /** Given **/
        val payload = UsageLogCipher.encrypt("plain".toByteArray(), UsageLogCipher.generateKey())

        /** When / Then **/
        assertThrows(GeneralSecurityException::class.java) {
            UsageLogCipher.decrypt(payload, UsageLogCipher.generateKey())
        }
    }

    /**
     * 암호문이 변조되면 복호화가 실패하는지 검증
     *
     * Given: 암호화한 값의 마지막 바이트를 뒤집음
     * When: decrypt 호출
     * Then: GeneralSecurityException
     */
    @Test
    fun test_03_tamperedPayloadFails() = runTest {
        /** Given **/
        val key = UsageLogCipher.generateKey()
        val payload = UsageLogCipher.encrypt("plain".toByteArray(), key)
        payload[payload.lastIndex] = (payload.last().toInt() xor 0xFF).toByte()

        /** When / Then **/
        assertThrows(GeneralSecurityException::class.java) { UsageLogCipher.decrypt(payload, key) }
    }

    /**
     * 결과 앞에 nonce가 붙고 길이가 nonce + 평문 + 태그인지 검증
     *
     * Given: 고정 nonce와 평문
     * When: encrypt 호출
     * Then: 앞 12바이트가 nonce이고 전체 크기가 12 + 평문 + 16
     */
    @Test
    fun test_04_payloadStartsWithNonce() = runTest {
        /** Given **/
        val nonce = ByteArray(UsageLogCipher.NONCE_SIZE_BYTES) { it.toByte() }
        val plain = "plain text".toByteArray()

        /** When **/
        val payload = UsageLogCipher.encrypt(plain, UsageLogCipher.generateKey(), nonce)

        /** Then **/
        assertArrayEquals(nonce, payload.copyOfRange(0, UsageLogCipher.NONCE_SIZE_BYTES))
        assertEquals(12 + plain.size + 16, payload.size)
    }

    /**
     * 키 문자열 변환의 왕복과 잘못된 입력 거부 검증
     *
     * Given: 생성한 키, 16바이트 키의 Base64, Base64가 아닌 문자열
     * When: decodeKey 호출
     * Then: 생성한 키는 복원되고 나머지는 null
     */
    @Test
    fun test_05_decodeKeyRejectsWrongLength() = runTest {
        /** Given **/
        val key = UsageLogCipher.generateKey()
        val shortKey = Base64.encodeToString(ByteArray(16), Base64.NO_WRAP)

        /** When / Then **/
        assertArrayEquals(key, UsageLogCipher.decodeKey(UsageLogCipher.encodeKey(key)))
        assertNull(UsageLogCipher.decodeKey(shortKey))
        assertNull(UsageLogCipher.decodeKey("not base64"))
    }
}
