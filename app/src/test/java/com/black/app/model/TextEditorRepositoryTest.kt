package com.black.app.model

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.datastore.TextEditorDataStore
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File

/** [TextEditorRepository]의 파일 읽기·저장 때 줄 끝 처리 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TextEditorRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = TextEditorRepository(context, TextEditorDataStore(context))

    /** 각 테스트 전 최근 문서와 줄 끝 기준 초기화(DataStore가 프로세스 단일 인스턴스라 테스트 간 공유) */
    @Before
    fun resetRepository() {
        runTest { repository.reset() }
    }

    /** filesDir에 [content]를 쓴 테스트 파일의 Uri */
    private fun createFileUri(name: String, content: String): Uri =
        Uri.fromFile(File(context.filesDir, name).apply { writeText(content) })

    /** [uri] 파일의 실제 내용 */
    private fun readFile(uri: Uri): String = File(requireNotNull(uri.path)).readText()

    /**
     * 줄 끝이 모두 CRLF인 문서는 LF로 바꿔 넘기고, 편집 뒤 저장하면 CRLF로 되돌려 쓰는지 검증
     *
     * Given: 줄 끝이 모두 CRLF이고 마지막 줄바꿈이 있는 파일
     * When: 읽은 텍스트의 줄 끝에 내용을 더해 같은 파일에 저장
     * Then: 읽은 텍스트는 LF만 사용, 저장한 파일은 더한 내용까지 CRLF만 사용
     */
    @Test
    fun test_01_crlfFileEditsWithLfAndSavesWithCrlf() = runTest {
        /** Given **/
        val uri = createFileUri("crlf.txt", "line1\r\nline2\r\n")

        /** When **/
        val loadedText = repository.loadTextFile(uri).getOrThrow()
        repository.saveTextFile(uri, loadedText.replace("line2\n", "line2 edited\nline3\n")).getOrThrow()

        /** Then **/
        assertEquals("line1\nline2\n", loadedText)
        assertEquals("line1\r\nline2 edited\r\nline3\r\n", readFile(uri))
    }

    /**
     * 줄 끝이 LF인 문서는 그대로 읽고 그대로 저장하는지 검증
     *
     * Given: 줄 끝이 LF이고 마지막 줄바꿈이 없는 파일
     * When: 읽은 텍스트를 같은 파일에 저장
     * Then: 읽은 텍스트와 저장한 파일 모두 원래 내용과 같음
     */
    @Test
    fun test_02_lfFileRoundTripsUnchanged() = runTest {
        /** Given **/
        val content = "line1\nline2"
        val uri = createFileUri("lf.txt", content)

        /** When **/
        val loadedText = repository.loadTextFile(uri).getOrThrow()
        repository.saveTextFile(uri, loadedText).getOrThrow()

        /** Then **/
        assertEquals(content, loadedText)
        assertEquals(content, readFile(uri))
    }

    /**
     * LF와 CRLF가 섞인 문서는 줄 끝을 바꾸지 않고 읽고 저장하는지 검증
     *
     * Given: CRLF 줄과 LF 줄이 섞인 파일
     * When: 읽은 텍스트를 같은 파일에 저장
     * Then: 읽은 텍스트와 저장한 파일 모두 원래 내용과 같음
     */
    @Test
    fun test_03_mixedLineEndingsRoundTripUnchanged() = runTest {
        /** Given **/
        val content = "line1\r\nline2\nline3\r\n"
        val uri = createFileUri("mixed.txt", content)

        /** When **/
        val loadedText = repository.loadTextFile(uri).getOrThrow()
        repository.saveTextFile(uri, loadedText).getOrThrow()

        /** Then **/
        assertEquals(content, loadedText)
        assertEquals(content, readFile(uri))
    }

    /**
     * CRLF 문서를 읽은 뒤 다른 파일에 저장하면 CRLF로 바꾸지 않는지 검증
     *
     * Given: CRLF 파일을 읽은 상태, 비어 있는 새 파일
     * When: 새 파일에 LF 텍스트 저장
     * Then: 새 파일은 LF 그대로
     */
    @Test
    fun test_04_saveToOtherFileKeepsLf() = runTest {
        /** Given **/
        val crlfUri = createFileUri("crlf_source.txt", "line1\r\nline2\r\n")
        repository.loadTextFile(crlfUri).getOrThrow()
        val newUri = createFileUri("new.txt", "")

        /** When **/
        repository.saveTextFile(newUri, "new1\nnew2\n").getOrThrow()

        /** Then **/
        assertEquals("new1\nnew2\n", readFile(newUri))
    }
}
