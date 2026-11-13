package com.black.core.util

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [JsonUtil]의 한 줄 출력 설정과 기존 설정의 출력 형태 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class JsonUtilTest : BaseTest() {

    @Serializable
    private data class Sample(val name: String, val memo: String? = null)

    /**
     * 한 줄 출력 설정이 줄바꿈 없이 출력하는지 검증
     *
     * Given: 필드가 둘인 객체
     * When: 한 줄 출력 설정으로 변환
     * Then: 줄바꿈과 들여쓰기 없는 한 줄
     */
    @Test
    fun test_01_compactOutputHasNoNewline() = runTest {
        /** Given / When **/
        val json = JsonUtil.to(Sample("a", "b"), true, JsonUtil.kotlinSerializationCompact)

        /** Then **/
        assertEquals("""{"name":"a","memo":"b"}""", json)
        assertFalse(json!!.contains('\n'))
    }

    /**
     * 한 줄 출력 설정이 null 필드를 null로 남기는지 검증
     *
     * Given: memo가 null인 객체
     * When: 한 줄 출력 설정으로 변환
     * Then: memo가 null 값으로 포함
     */
    @Test
    fun test_02_compactOutputKeepsNullField() = runTest {
        /** Given / When **/
        val json = JsonUtil.to(Sample("a"), true, JsonUtil.kotlinSerializationCompact)

        /** Then **/
        assertEquals("""{"name":"a","memo":null}""", json)
    }

    /**
     * 기존 설정의 출력이 그대로 여러 줄이고 null을 생략하는지 검증
     *
     * Given: memo가 null인 객체
     * When: 기존 설정으로 변환
     * Then: 여러 줄이며 memo 키가 없음
     */
    @Test
    fun test_03_defaultOutputIsUnchanged() = runTest {
        /** Given / When **/
        val json = JsonUtil.to(Sample("a"), true)

        /** Then **/
        assertTrue(json!!.contains('\n'))
        assertFalse(json.contains("memo"))
    }

    /**
     * 한 줄 출력 설정으로 쓴 값을 같은 설정으로 읽는지 검증
     *
     * Given: 한 줄 출력으로 변환한 객체
     * When: 같은 설정으로 복원
     * Then: 원본과 같음
     */
    @Test
    fun test_04_compactRoundTrip() = runTest {
        /** Given **/
        val json = JsonUtil.to(Sample("a", "b"), true, JsonUtil.kotlinSerializationCompact)!!

        /** When **/
        val restored = JsonUtil.from<Sample>(json, true, JsonUtil.kotlinSerializationCompact)

        /** Then **/
        assertEquals(Sample("a", "b"), restored)
    }

    /** [block] 실행 중 System.err에 출력된 내용(실행 뒤 System.err 원복) */
    private fun captureStderr(block: () -> Unit): String {
        val original = System.err
        val buffer = ByteArrayOutputStream()
        System.setErr(PrintStream(buffer, true))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return buffer.toString()
    }

    /**
     * logInput이 false면 복원 실패 시 입력 문자열이 출력되지 않는지 검증
     *
     * Given: 입력 문자열이 든 깨진 JSON
     * When: logInput = false로 복원
     * Then: null 반환, System.err에 입력 문자열 없음
     */
    @Test
    fun test_05_logInputFalseHidesInputOnFailure() = runTest {
        /** Given **/
        val broken = """{"name":"secret-value""""
        var result: Sample? = Sample("unset")

        /** When **/
        val err = captureStderr {
            result = JsonUtil.from<Sample>(broken, true, JsonUtil.kotlinSerializationCompact, logInput = false)
        }

        /** Then **/
        assertEquals(null, result)
        assertFalse(err.contains("secret-value"))
    }

    /**
     * 기본값이면 복원 실패 시 기존처럼 입력 문자열이 출력되는지 검증
     *
     * Given: 입력 문자열이 든 깨진 JSON
     * When: 기본값으로 복원
     * Then: System.err에 입력 문자열 포함
     */
    @Test
    fun test_06_defaultLogsInputOnFailure() = runTest {
        /** Given **/
        val broken = """{"name":"secret-value""""

        /** When **/
        val err = captureStderr {
            JsonUtil.from<Sample>(broken, true, JsonUtil.kotlinSerializationCompact)
        }

        /** Then **/
        assertTrue(err.contains("secret-value"))
    }
}
