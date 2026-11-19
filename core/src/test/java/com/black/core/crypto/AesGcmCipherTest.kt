package com.black.core.crypto

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

/** [AesGcmCipher]의 AES-GCM 암복호화와 키 문자열 변환 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AesGcmCipherTest : BaseTest() {

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
        val key = AesGcmCipher.generateKey()
        val plain = "한글 원문".toByteArray()

        /** When **/
        val decrypted = AesGcmCipher.decrypt(AesGcmCipher.encrypt(plain, key), key)

        /** Then **/
        assertEquals(AesGcmCipher.KEY_SIZE_BYTES, key.size)
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
        val payload = AesGcmCipher.encrypt("plain".toByteArray(), AesGcmCipher.generateKey())

        /** When / Then **/
        assertThrows(GeneralSecurityException::class.java) {
            AesGcmCipher.decrypt(payload, AesGcmCipher.generateKey())
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
        val key = AesGcmCipher.generateKey()
        val payload = AesGcmCipher.encrypt("plain".toByteArray(), key)
        payload[payload.lastIndex] = (payload.last().toInt() xor 0xFF).toByte()

        /** When / Then **/
        assertThrows(GeneralSecurityException::class.java) { AesGcmCipher.decrypt(payload, key) }
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
        val nonce = ByteArray(AesGcmCipher.NONCE_SIZE_BYTES) { it.toByte() }
        val plain = "plain text".toByteArray()

        /** When **/
        val payload = AesGcmCipher.encrypt(plain, AesGcmCipher.generateKey(), nonce)

        /** Then **/
        assertArrayEquals(nonce, payload.copyOfRange(0, AesGcmCipher.NONCE_SIZE_BYTES))
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
        val key = AesGcmCipher.generateKey()
        val shortKey = Base64.encodeToString(ByteArray(16), Base64.NO_WRAP)

        /** When / Then **/
        assertArrayEquals(key, AesGcmCipher.decodeKey(AesGcmCipher.encodeKey(key)))
        assertNull(AesGcmCipher.decodeKey(shortKey))
        assertNull(AesGcmCipher.decodeKey("not base64"))
    }

    /**
     * 고정 키, nonce, 평문의 결과가 표준 AES-256-GCM 결과와 같고 그 값을 복호화하는지 검증
     *
     * 기대값은 GCM 명세(McGrew, Viega)의 Test Case 15이며, 옛 UsageLogCipher와 PC 쪽 복호화가 쓰는 형식(nonce + 암호문 + 태그 16바이트)을 고정함.
     *
     * Given: Test Case 15의 256비트 키, 96비트 nonce, 64바이트 평문
     * When: encrypt 호출, 기대 결과를 decrypt 호출
     * Then: nonce + 기대 암호문 + 기대 태그, 복호화 결과는 평문
     */
    @Test
    fun test_06_knownAnswer() = runTest {
        /** Given **/
        val key = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".hexToByteArray()
        val nonce = "cafebabefacedbaddecaf888".hexToByteArray()
        val plain = (
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
                "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255"
            ).hexToByteArray()
        val expected = (
            "cafebabefacedbaddecaf888" +
                "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad" +
                "b094dac5d93471bdec1a502270e3cc6c"
            ).hexToByteArray()

        /** When **/
        val encrypted = AesGcmCipher.encrypt(plain, key, nonce)
        val decrypted = AesGcmCipher.decrypt(expected, key)

        /** Then **/
        assertArrayEquals(expected, encrypted)
        assertArrayEquals(plain, decrypted)
    }
}
