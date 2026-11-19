package com.black.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import com.black.app.data.datastore.TextEditorDataStore
import com.black.test.BaseTest
import com.black.test.testFileUri
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import java.io.IOException

/** [TextEditorRepository]의 파일 읽기·저장 때 줄 끝 처리와 초기화 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TextEditorRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = TextEditorRepository(context, TextEditorDataStore(context))

    /** 각 테스트 전 최근 문서와 줄 끝 기준 초기화(DataStore가 프로세스 단일 인스턴스라 테스트 간 공유) */
    @Before
    fun resetRepository() {
        runTest { repository.reset().getOrThrow() }
    }

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
        val uri = context.testFileUri("crlf.txt", "line1\r\nline2\r\n")

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
        val uri = context.testFileUri("lf.txt", content)

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
        val uri = context.testFileUri("mixed.txt", content)

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
        val crlfUri = context.testFileUri("crlf_source.txt", "line1\r\nline2\r\n")
        repository.loadTextFile(crlfUri).getOrThrow()
        val newUri = context.testFileUri("new.txt", "")

        /** When **/
        repository.saveTextFile(newUri, "new1\nnew2\n").getOrThrow()

        /** Then **/
        assertEquals("new1\nnew2\n", readFile(newUri))
    }

    /**
     * 초기화하면 최근 문서 기록과 줄 끝 기준을 지우는지 검증
     *
     * Given: CRLF 파일을 읽은 상태
     * When: reset 호출 뒤 같은 파일에 LF 텍스트 저장
     * Then: 성공 결과, 저장 전 최근 문서 기록 없음, 파일은 LF 그대로
     */
    @Test
    fun test_05_resetClearsLatestFileAndCrlfBase() = runTest {
        /** Given **/
        val uri = context.testFileUri("crlf_reset.txt", "line1\r\nline2\r\n")
        repository.loadTextFile(uri).getOrThrow()

        /** When **/
        val result = repository.reset()
        val latestFileUri = repository.currentFileUriFlow.first()
        repository.saveTextFile(uri, "new1\nnew2\n").getOrThrow()

        /** Then **/
        assertTrue(result.isSuccess)
        assertNull(latestFileUri)
        assertEquals("new1\nnew2\n", readFile(uri))
    }

    /**
     * 최근 문서 기록을 지우지 못하면 실패 결과를 돌려주고 줄 끝 기준을 그대로 두는지 검증
     *
     * Given: 저장이 항상 IOException으로 실패하는 DataStore를 쓰는 저장소, CRLF 파일을 읽은 상태
     * When: reset 호출 뒤 같은 파일에 LF 텍스트 저장
     * Then: 실패 결과(원인 IOException), 파일은 CRLF로 저장
     */
    @Test
    fun test_06_resetFailureKeepsCrlfBase() = runTest {
        /** Given **/
        val failingDataStore = mockk<DataStore<Preferences>> {
            every { data } returns flowOf(emptyPreferences())
            coEvery { updateData(any()) } throws IOException("disk full")
        }
        val failingRepository = TextEditorRepository(context, TextEditorDataStore(context, failingDataStore))
        val uri = context.testFileUri("crlf_reset_failure.txt", "line1\r\nline2\r\n")
        failingRepository.loadTextFile(uri).getOrThrow()

        /** When **/
        val result = failingRepository.reset()
        failingRepository.saveTextFile(uri, "new1\nnew2\n").getOrThrow()

        /** Then **/
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("new1\r\nnew2\r\n", readFile(uri))
    }
}
