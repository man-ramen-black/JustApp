package com.black.core.file

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Intent
import com.black.core.file.SAFHelper.Companion.getFileName
import com.black.core.file.SAFHelper.Companion.openInputStream
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [SAFHelper]의 파일 이름 조회, 영구 권한 요청, 스트림 열기 동작 검증 */
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
}
