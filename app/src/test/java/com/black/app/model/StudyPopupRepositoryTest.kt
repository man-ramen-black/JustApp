package com.black.app.model

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.database.studypopup.StudyPopupData
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File

/** [StudyPopupRepository]의 팝업 내용 저장·조회와 내용 존재 여부 판단 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = StudyPopupRepository(context)

    /** 각 테스트 전 팝업 내용 초기화(DB가 프로세스 단일 인스턴스라 테스트 간 공유) */
    @Before
    fun clearContents() {
        runTest { repository.save(emptyList()) }
    }

    /**
     * 메인 스레드에서 저장한 내용을 그대로 다시 읽어오는지 검증
     *
     * Given: 내용 두 개
     * When: 메인 스레드(화면의 viewModelScope와 같은 스레드)에서 저장
     * Then: 저장 순서·내용 그대로 조회
     */
    @Test
    fun test_01_saveOnMainThreadAndLoadRoundTrip() = runTest {
        /** Given **/
        val list = listOf(StudyPopupData.Contents("a"), StudyPopupData.Contents("b"))

        /** When **/
        repository.save(list)

        /** Then **/
        assertEquals(listOf("a", "b"), repository.load().map { it.contents })
    }

    /**
     * 등록된 내용이 없으면 내용 없음으로 판단하는지 검증
     *
     * Given: 내용 초기화(clearContents)
     * When: 내용 존재 여부 조회
     * Then: false
     */
    @Test
    fun test_02_hasContentsFalseWhenEmpty() = runTest {
        /** Given **/
        // clearContents에서 내용 초기화됨

        /** When / Then **/
        assertFalse(repository.hasContents())
    }

    /**
     * 공백뿐인 내용만 있으면 빈 팝업이 되므로 내용 없음으로 판단하는지 검증
     *
     * Given: 빈 문자열·공백·줄바꿈 내용만 저장
     * When: 내용 존재 여부 조회
     * Then: false
     */
    @Test
    fun test_03_hasContentsFalseWhenOnlyBlank() = runTest {
        /** Given **/
        repository.save(
            listOf(
                StudyPopupData.Contents(""),
                StudyPopupData.Contents(" "),
                StudyPopupData.Contents("\n"),
            )
        )

        /** When / Then **/
        assertFalse(repository.hasContents())
    }

    /**
     * 공백이 아닌 내용이 하나라도 있으면 내용 있음으로 판단하는지 검증
     *
     * Given: 빈 문자열과 일반 내용을 함께 저장
     * When: 내용 존재 여부 조회
     * Then: true
     */
    @Test
    fun test_04_hasContentsTrueWhenAnyNotBlank() = runTest {
        /** Given **/
        repository.save(listOf(StudyPopupData.Contents(""), StudyPopupData.Contents("word")))

        /** When / Then **/
        assertTrue(repository.hasContents())
    }

    /**
     * 내용 파일에 저장하면 파일과 팝업 DB에 함께 반영되고 그 파일을 다시 읽을 수 있는지 검증
     *
     * Given: 앱 내부 저장소의 빈 파일 uri, 공백 내용이 섞인 내용 목록
     * When: writeFile 후 readFile
     * Then: 읽은 내용이 저장한 목록과 같음, 팝업 DB 내용 개수는 공백을 뺀 2, 마지막으로 연 파일로 기록
     */
    @Test
    fun test_05_writeFileUpdatesFileAndPopupDatabase() = runTest {
        /** Given **/
        val uri = Uri.fromFile(File(context.filesDir, "study_popup_test.xml").apply { writeText("") })
        val contents = listOf("a", " ", "b")

        /** When **/
        val writeResult = repository.writeFile(uri, contents)
        val readResult = repository.readFile(uri)

        /** Then **/
        assertTrue(writeResult.isSuccess)
        assertEquals(contents, readResult.getOrNull())
        assertEquals(2, repository.countContents())
        assertEquals(uri, repository.loadLatestUri())
    }

    /**
     * 읽을 수 없는 파일은 실패 결과를 돌려주고 마지막으로 연 파일 기록을 바꾸지 않는지 검증
     *
     * Given: 마지막으로 연 파일 기록 삭제, 존재하지 않는 파일 uri
     * When: readFile
     * Then: 실패 결과, 마지막으로 연 파일 기록 없음
     */
    @Test
    fun test_06_readMissingFileFails() = runTest {
        /** Given **/
        repository.removeLatestUri()
        val uri = Uri.fromFile(File(context.filesDir, "missing_study_popup.xml"))

        /** When **/
        val result = repository.readFile(uri)

        /** Then **/
        assertTrue(result.isFailure)
        assertNull(repository.loadLatestUri())
    }
}
