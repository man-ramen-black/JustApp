package com.black.app.usagelog.upload

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 전송 첨부의 AES-256-GCM 암복호화
 *
 * 암호문은 앞에 nonce가 붙고 끝에 인증 태그 16바이트가 붙은 형태.
 * 키와 평문은 로그에 남기지 않음.
 */
object UsageLogCipher {

    const val KEY_SIZE_BYTES = 32
    const val NONCE_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val secureRandom = SecureRandom()

    /** 무작위 256비트 키 */
    fun generateKey(): ByteArray = ByteArray(KEY_SIZE_BYTES).also(secureRandom::nextBytes)

    /** 무작위 96비트 nonce */
    fun randomNonce(): ByteArray = ByteArray(NONCE_SIZE_BYTES).also(secureRandom::nextBytes)

    /**
     * [plain]을 암호화한 nonce + 암호문
     *
     * 같은 키에서 nonce를 재사용하면 GCM이 깨지므로 호출마다 새 nonce를 써야 함.
     */
    fun encrypt(plain: ByteArray, key: ByteArray, nonce: ByteArray = randomNonce()): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        return nonce + cipher.doFinal(plain)
    }

    /**
     * [encrypt]한 값의 복호화
     *
     * 키가 다르거나 변조됐으면 [java.security.GeneralSecurityException]의 하위 예외.
     */
    fun decrypt(payload: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val nonce = payload.copyOfRange(0, NONCE_SIZE_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        return cipher.doFinal(payload, NONCE_SIZE_BYTES, payload.size - NONCE_SIZE_BYTES)
    }

    /** 키의 Base64 문자열 */
    fun encodeKey(key: ByteArray): String = Base64.encodeToString(key, Base64.NO_WRAP)

    /** [encodeKey]한 문자열의 복원이며 Base64가 아니거나 32바이트가 아니면 null */
    fun decodeKey(text: String): ByteArray? {
        val decoded = try {
            Base64.decode(text, Base64.NO_WRAP)
        } catch (exception: IllegalArgumentException) {
            return null
        }
        return decoded.takeIf { it.size == KEY_SIZE_BYTES }
    }
}
