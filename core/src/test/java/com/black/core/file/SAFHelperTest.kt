package com.black.core.file

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import com.black.core.file.SAFHelper.Companion.getFileName
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [SAFHelper.getFileName]의 content uri 파일 이름 조회와 권한 없는 uri 처리 검증 */
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
}
