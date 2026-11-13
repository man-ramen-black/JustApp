package com.black.core.file

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Intent
import com.black.core.file.SAFHelper.getFileName
import com.black.core.file.SAFHelper.openInputStream
import com.black.core.file.SAFHelper.readDocumentText
import com.black.core.file.SAFHelper.writeDocumentText
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

/** [SAFHelper]의 파일 이름 조회, 영구 권한 요청, 스트림 열기, 문서 텍스트 읽기와 쓰기 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SAFHelperTest : BaseTest() {

    /** 미디어 문서 제공자가 발급하는 형태의 content uri */
    private val documentUri = Uri.parse("content://com.android.providers.media.documents/document/document%3A126")

    private lateinit var contentResolver: ContentResolver
    private lateinit var context: Context

    @Before
    fun setupContext() {
        contentResolver = mockk()
        context = mockk {
            every { contentResolver } returns this@SAFHelperTest.contentResolver
        }
    }

    /**
     * 문서 제공자가 표시 이름을 돌려주면 그 이름을 반환하는지 검증
     *
     * Given: DISPLAY_NAME "memo.txt" 한 행을 돌려주는 query
     * When: getFileName 호출
     * Then: "memo.txt" 반환
     */
    @Test
    fun test_01_getFileNameReturnsDisplayName() = runTest {
        /** Given **/
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("memo.txt")) }
        every { contentResolver.query(documentUri, null, null, null, null) } returns cursor

        /** When **/
        val fileName = documentUri.getFileName(context)

        /** Then **/
        assertEquals("memo.txt", fileName)
    }

    /**
     * 영구 권한이 없는 uri를 query해 SecurityException이 나면 크래시 대신 null을 반환하는지 검증
     *
     * Given: SecurityException을 던지는 query(재설치 등으로 영구 권한이 사라진 uri)
     * When: getFileName 호출
     * Then: 예외 전파 없이 null 반환
     */
    @Test
    fun test_02_getFileNameReturnsNullWhenPermissionDenied() = runTest {
        /** Given **/
        every { contentResolver.query(documentUri, null, null, null, null) } throws
            SecurityException("Permission Denial: opening provider com.android.providers.media.MediaDocumentsProvider")

        /** When **/
        val fileName = documentUri.getFileName(context)

        /** Then **/
        assertNull(fileName)
    }

    /**
     * 쓰기 영구 권한을 주지 않는 문서는 읽기 영구 권한만 받는지 검증
     *
     * Given: 읽기·쓰기 요청은 SecurityException, 읽기 요청은 성공하는 contentResolver
     * When: keepUriPermission 호출
     * Then: true 반환, 읽기만 요청한 호출이 있음
     */
    @Test
    fun test_03_keepUriPermissionFallsBackToReadOnly() = runTest {
        /** Given **/
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        every { contentResolver.takePersistableUriPermission(documentUri, readWrite) } throws SecurityException("no write")
        every { contentResolver.takePersistableUriPermission(documentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } just runs

        /** When **/
        val kept = SAFHelper.keepUriPermission(context, documentUri)

        /** Then **/
        assertTrue(kept)
        verify(exactly = 1) { contentResolver.takePersistableUriPermission(documentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /**
     * 영구 권한을 하나도 받지 못하면 예외 없이 false를 반환하는지 검증
     *
     * Given: 모든 요청에 SecurityException을 던지는 contentResolver
     * When: keepUriPermission 호출
     * Then: false 반환
     */
    @Test
    fun test_04_keepUriPermissionReturnsFalseWhenDenied() = runTest {
        /** Given **/
        every { contentResolver.takePersistableUriPermission(documentUri, any()) } throws SecurityException("denied")

        /** When **/
        val kept = SAFHelper.keepUriPermission(context, documentUri)

        /** Then **/
        assertFalse(kept)
    }

    /**
     * 읽기 스트림을 열 때 영구 권한을 요청하지 않는지 검증
     *
     * Given: openInputStream이 null을 돌려주는 contentResolver
     * When: openInputStream 호출
     * Then: takePersistableUriPermission 미호출
     */
    @Test
    fun test_05_openInputStreamDoesNotTakePermission() = runTest {
        /** Given **/
        every { contentResolver.openInputStream(documentUri) } returns null

        /** When **/
        documentUri.openInputStream(context)

        /** Then **/
        verify(exactly = 0) { contentResolver.takePersistableUriPermission(any(), any()) }
    }

    /**
     * 문서 텍스트를 읽어 후속 처리 결과를 성공 결과로 반환하는지 검증
     *
     * Given: CRLF 줄 끝과 한글이 든 UTF-8 문서
     * When: readDocumentText 호출(후속 처리는 읽은 텍스트에 표시를 덧붙임)
     * Then: 줄 끝을 바꾸지 않은 텍스트로 후속 처리한 결과가 성공 결과
     */
    @Test
    fun test_06_readDocumentTextReturnsOnReadResult() = runTest {
        /** Given **/
        every { contentResolver.openInputStream(documentUri) } returns
            ByteArrayInputStream("가\r\nb\r\n".toByteArray(Charsets.UTF_8))

        /** When **/
        val result = documentUri.readDocumentText(context) { "$it!" }

        /** Then **/
        assertEquals("가\r\nb\r\n!", result.getOrNull())
    }

    /**
     * 읽기 스트림을 열 수 없으면 후속 처리 없이 FileNotFoundException 실패 결과를 반환하는지 검증
     *
     * Given: openInputStream이 null을 돌려주는 contentResolver
     * When: readDocumentText 호출
     * Then: FileNotFoundException 실패 결과, 후속 처리 미실행
     */
    @Test
    fun test_07_readDocumentTextFailsWhenStreamIsNull() = runTest {
        /** Given **/
        every { contentResolver.openInputStream(documentUri) } returns null
        var onReadCalled = false

        /** When **/
        val result = documentUri.readDocumentText(context) { onReadCalled = true }

        /** Then **/
        assertTrue(result.exceptionOrNull() is FileNotFoundException)
        assertFalse(onReadCalled)
    }

    /**
     * 영구 권한이 없는 문서는 SecurityException을 그대로 실패 결과로 넘기는지 검증(호출부가 이 예외로 문서 재선택 안내)
     *
     * Given: openInputStream이 SecurityException을 던지는 contentResolver
     * When: readDocumentText 호출
     * Then: SecurityException 실패 결과
     */
    @Test
    fun test_08_readDocumentTextKeepsSecurityException() = runTest {
        /** Given **/
        every { contentResolver.openInputStream(documentUri) } throws SecurityException("No persistable permission grants found")

        /** When **/
        val result = documentUri.readDocumentText(context) { it }

        /** Then **/
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    /**
     * 읽은 뒤 후속 처리에서 코루틴이 취소되면 실패 결과로 바꾸지 않고 취소를 다시 던지는지 검증
     *
     * Given: 문서 내용, CancellationException을 던지는 후속 처리
     * When: readDocumentText 호출
     * Then: CancellationException 전파
     */
    @Test
    fun test_09_readDocumentTextRethrowsCancellation() = runTest {
        /** Given **/
        every { contentResolver.openInputStream(documentUri) } returns ByteArrayInputStream(byteArrayOf())

        /** When **/
        val thrown = runCatching {
            documentUri.readDocumentText(context) { throw CancellationException("cancelled") }
        }.exceptionOrNull()

        /** Then **/
        assertTrue(thrown is CancellationException)
    }

    /**
     * 기존 내용을 지우는 모드로 문서에 텍스트를 쓰고 후속 처리를 실행하는지 검증
     *
     * Given: "wt" 모드 쓰기 스트림을 돌려주는 contentResolver
     * When: writeDocumentText 호출
     * Then: 성공 결과, 스트림에 UTF-8 텍스트가 그대로 쓰임, 후속 처리 실행
     */
    @Test
    fun test_10_writeDocumentTextWritesTextAndRunsOnWritten() = runTest {
        /** Given **/
        val outputStream = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(documentUri, "wt") } returns outputStream
        var onWrittenCalled = false

        /** When **/
        val result = documentUri.writeDocumentText(context, createText = { "가\r\nb" }) { onWrittenCalled = true }

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals("가\r\nb", outputStream.toString(Charsets.UTF_8.name()))
        assertTrue(onWrittenCalled)
    }

    /**
     * 쓰기 스트림을 열 수 없으면 후속 처리 없이 FileNotFoundException 실패 결과를 반환하는지 검증
     *
     * Given: openOutputStream이 null을 돌려주는 contentResolver
     * When: writeDocumentText 호출
     * Then: FileNotFoundException 실패 결과, 후속 처리 미실행
     */
    @Test
    fun test_11_writeDocumentTextFailsWhenStreamIsNull() = runTest {
        /** Given **/
        every { contentResolver.openOutputStream(documentUri, "wt") } returns null
        var onWrittenCalled = false

        /** When **/
        val result = documentUri.writeDocumentText(context, createText = { "text" }) { onWrittenCalled = true }

        /** Then **/
        assertTrue(result.exceptionOrNull() is FileNotFoundException)
        assertFalse(onWrittenCalled)
    }

    /**
     * 쓸 텍스트를 만들다 실패하면 기존 내용을 지우는 스트림을 열지 않고 실패 결과를 반환하는지 검증
     *
     * Given: IllegalArgumentException을 던지는 텍스트 생성(XML에 쓸 수 없는 문자 등)
     * When: writeDocumentText 호출
     * Then: IllegalArgumentException 실패 결과, openOutputStream 미호출
     */
    @Test
    fun test_12_writeDocumentTextDoesNotOpenStreamWhenCreateTextFails() = runTest {
        /** Given **/
        val createText: () -> String = { throw IllegalArgumentException("Illegal character (U+1)") }

        /** When **/
        val result = documentUri.writeDocumentText(context, createText)

        /** Then **/
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        verify(exactly = 0) { contentResolver.openOutputStream(any(), any()) }
    }

    /**
     * 쓴 뒤 후속 처리에서 코루틴이 취소되면 실패 결과로 바꾸지 않고 취소를 다시 던지는지 검증
     *
     * Given: 쓰기 스트림, CancellationException을 던지는 후속 처리
     * When: writeDocumentText 호출
     * Then: CancellationException 전파
     */
    @Test
    fun test_13_writeDocumentTextRethrowsCancellation() = runTest {
        /** Given **/
        every { contentResolver.openOutputStream(documentUri, "wt") } returns ByteArrayOutputStream()

        /** When **/
        val thrown = runCatching {
            documentUri.writeDocumentText(context, createText = { "text" }) { throw CancellationException("cancelled") }
        }.exceptionOrNull()

        /** Then **/
        assertTrue(thrown is CancellationException)
    }
}
