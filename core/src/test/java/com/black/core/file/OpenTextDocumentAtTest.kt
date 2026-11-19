package com.black.core.file

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.IntentCompat
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [OpenTextDocumentAt]의 열기 Intent 구성과 선택 결과 해석 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OpenTextDocumentAtTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /** 미디어 문서 제공자가 발급하는 형태의 content uri */
    private val documentUri = Uri.parse("content://com.android.providers.media.documents/document/document%3A126")

    /**
     * Android 8.0 이상에서 text/plain 열기 Intent에 입력 uri를 처음 위치로 넣는지 검증
     *
     * Given: SDK 34, 문서 uri
     * When: createIntent 호출
     * Then: ACTION_OPEN_DOCUMENT, MIME 형식 text/plain, EXTRA_INITIAL_URI가 입력 uri
     */
    @Test
    @Config(sdk = [34])
    fun test_01_createIntentPutsInitialUri() = runTest {
        /** Given **/
        val contract = OpenTextDocumentAt()

        /** When **/
        val intent = contract.createIntent(context, documentUri)

        /** Then **/
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertArrayEquals(arrayOf("text/plain"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertEquals(documentUri, IntentCompat.getParcelableExtra(intent, DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java))
    }

    /**
     * EXTRA_INITIAL_URI를 지원하지 않는 Android 8.0 미만에서는 처음 위치 없이 여는지 검증
     *
     * Given: SDK 25, 문서 uri
     * When: createIntent 호출
     * Then: ACTION_OPEN_DOCUMENT, MIME 형식 text/plain, EXTRA_INITIAL_URI 없음
     */
    @Test
    @Config(sdk = [25])
    fun test_02_createIntentOmitsInitialUriBeforeOreo() = runTest {
        /** Given **/
        val contract = OpenTextDocumentAt()

        /** When **/
        val intent = contract.createIntent(context, documentUri)

        /** Then **/
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertArrayEquals(arrayOf("text/plain"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertFalse(intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI))
    }

    /**
     * 선택을 마친 결과에서 고른 문서 uri를 꺼내는지 검증
     *
     * Given: data가 문서 uri인 결과 Intent
     * When: RESULT_OK로 parseResult 호출
     * Then: 문서 uri 반환
     */
    @Test
    fun test_03_parseResultReturnsSelectedUri() = runTest {
        /** Given **/
        val contract = OpenTextDocumentAt()
        val resultIntent = Intent().setData(documentUri)

        /** When **/
        val result = contract.parseResult(Activity.RESULT_OK, resultIntent)

        /** Then **/
        assertEquals(documentUri, result)
    }

    /**
     * 선택을 취소한 결과는 null로 해석하는지 검증
     *
     * Given: data가 문서 uri인 결과 Intent
     * When: RESULT_CANCELED로 parseResult 호출
     * Then: null 반환
     */
    @Test
    fun test_04_parseResultReturnsNullWhenCanceled() = runTest {
        /** Given **/
        val contract = OpenTextDocumentAt()
        val resultIntent = Intent().setData(documentUri)

        /** When **/
        val result = contract.parseResult(Activity.RESULT_CANCELED, resultIntent)

        /** Then **/
        assertNull(result)
    }
}
