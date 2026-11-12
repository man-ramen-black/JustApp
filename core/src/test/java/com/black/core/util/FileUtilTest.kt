package com.black.core.util

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** [FileUtil]의 텍스트 읽기·쓰기 보존 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FileUtilTest : BaseTest() {

    /**
     * CRLF 줄바꿈과 마지막 줄바꿈을 그대로 읽는지 검증
     *
     * Given: CRLF 줄바꿈이고 마지막 줄바꿈이 있는 UTF-8 텍스트
     * When: readText 호출
     * Then: 원문과 같은 문자열 반환
     */
    @Test
    fun test_01_readTextKeepsLineSeparators() = runTest {
        /** Given **/
        val original = "첫 줄\r\n둘째 줄\r\n"
        val inputStream = ByteArrayInputStream(original.toByteArray(Charsets.UTF_8))

        /** When **/
        val text = FileUtil.readText(inputStream)

        /** Then **/
        assertEquals(original, text)
    }

    /**
     * 읽은 텍스트를 그대로 쓰면 바이트가 바뀌지 않는지 검증
     *
     * Given: LF 줄바꿈과 마지막 줄바꿈이 있는 한글 UTF-8 텍스트
     * When: readText 결과를 writeText로 저장
     * Then: 저장한 바이트가 원본 바이트와 같음
     */
    @Test
    fun test_02_readThenWriteKeepsBytes() = runTest {
        /** Given **/
        val originalBytes = "메모\n내용\n".toByteArray(Charsets.UTF_8)
        val outputStream = ByteArrayOutputStream()

        /** When **/
        val text = FileUtil.readText(ByteArrayInputStream(originalBytes))
        FileUtil.writeText(outputStream) { it.write(text) }

        /** Then **/
        assertEquals(originalBytes.toList(), outputStream.toByteArray().toList())
    }
}
