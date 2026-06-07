package com.black.app.ui.maintab.main.texteditor

import android.net.Uri
import com.black.app.R
import com.black.app.model.database.studypopup.TextEditorRepository
import com.black.core.viewmodel.ViewModelEvent
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [TextEditorViewModel]의 상태 관리·이벤트 발송 동작 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextEditorViewModelTest : BaseTest() {

    private lateinit var repository: TextEditorRepository

    @Before
    fun setupRepository() {
        repository = mockk()
        coEvery { repository.fileNameFlow } returns flowOf("")
        coEvery { repository.currentFileUriFlow } returns flowOf(null)
    }

    /**
     * ViewModel 생성 및 init 코루틴 완료 대기
     */
    private fun createViewModel(): TextEditorViewModel {
        val mainScope = TestScope(testDispatcherRule.testDispatcher)
        return TextEditorViewModel(repository, mainScope).also { waitCoroutine() }
    }

    /**
     * onClickLoad 호출 시 EventOpenDocument 이벤트가 발송되는지 검증
     *
     * Given: 기본 repository 설정
     * When: onClickLoad 호출
     * Then: events에서 EventOpenDocument 수신
     */
    @Test
    fun test_01_onClickLoadSendsOpenDocumentEvent() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onClickLoad()
        viewModel.events.collectEvents(receivedEvents, count = 1)

        /** Then **/
        assertEquals(TextEditorViewModel.EventOpenDocument, receivedEvents[0])
    }

    /**
     * 텍스트 변경 후 현재 파일 uri가 null일 때 onClickSave 시 EventCreateDocument가 발송되는지 검증
     *
     * Given: onTextChanged로 변경된 텍스트, currentFileUriFlow가 null 반환
     * When: onClickSave 호출
     * Then: EventCreateDocument 수신
     */
    @Test
    fun test_02_onClickSaveWithoutCurrentUriSendsCreateDocumentEvent() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.onTextChanged("changed text")
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onClickSave()
        viewModel.events.collectEvents(receivedEvents, count = 1)

        /** Then **/
        assertEquals(TextEditorViewModel.EventCreateDocument, receivedEvents[0])
    }

    /**
     * onTextChanged 호출 시 uiState.text가 갱신되는지 검증
     *
     * Given: 기본 repository 설정
     * When: onTextChanged("abc") 호출
     * Then: uiState.text == "abc"
     */
    @Test
    fun test_03_onTextChangedUpdatesUiState() {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.onTextChanged("abc")

        /** Then **/
        assertEquals("abc", viewModel.uiState.value.text)
    }

    /**
     * onConfirmReset 호출 시 repository.reset 실행, 텍스트 초기화, 다이얼로그 닫힘 검증
     *
     * Given: onClickNew로 showResetConfirm true, repository.reset 모킹
     * When: onConfirmReset 호출 후 코루틴 완료 대기
     * Then: repository.reset 호출 + uiState.text 빈 문자열 + showResetConfirm false
     */
    @Test
    fun test_04_onConfirmResetClearsTextAndResetsRepository() {
        /** Given **/
        coEvery { repository.reset() } just Runs
        val viewModel = createViewModel()
        viewModel.onClickNew()
        assertTrue(viewModel.uiState.value.showResetConfirm)

        /** When **/
        viewModel.onConfirmReset()
        waitCoroutine()

        /** Then **/
        coVerify { repository.reset() }
        assertEquals("", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.showResetConfirm)
    }

    /**
     * onDocumentOpened 호출 시 loadTextFile 성공 결과가 uiState.text에 반영되는지 검증
     *
     * Given: loadTextFile이 "loaded" 반환
     * When: onDocumentOpened(uri) 호출 후 코루틴 완료 대기
     * Then: uiState.text == "loaded"
     */
    @Test
    fun test_05_onDocumentOpenedLoadsTextIntoUiState() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        coEvery { repository.loadTextFile(uri) } returns Result.success("loaded")
        val viewModel = createViewModel()

        /** When **/
        viewModel.onDocumentOpened(uri)
        // IO 스레드 작업 완료 및 Main 코루틴 재개 후 text 갱신 대기
        val loadedState = viewModel.uiState.filter { it.text == "loaded" }.first()

        /** Then **/
        assertEquals("loaded", loadedState.text)
    }

    /**
     * 저장 실패 시 EventShowToast(text_editor_save_failed)가 발송되는지 검증
     *
     * Given: 변경 텍스트, 유효 uri, saveTextFile 실패 반환(init 시 loadTextFile도 stub)
     * When: onClickSave 호출
     * Then: EventShowToast(R.string.text_editor_save_failed) 수신
     */
    @Test
    fun test_06_saveFileFailureSendsFailureToastEvent() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val currentUriFlow = MutableStateFlow<Uri?>(uri)
        coEvery { repository.currentFileUriFlow } returns currentUriFlow
        // init 블록에서 uri non-null 시 loadTextFile 호출 — stub 필요
        coEvery { repository.loadTextFile(uri) } returns Result.success("initial text")
        coEvery { repository.saveTextFile(uri, any()) } returns Result.failure(Exception("save error"))
        val viewModel = createViewModel()
        // init IO 코루틴 완료(originText·uiState.text = "initial text") 후 텍스트 변경
        viewModel.uiState.filter { it.text == "initial text" }.first()
        viewModel.onTextChanged("changed text")
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onClickSave()
        viewModel.events.collectEvents(receivedEvents, count = 1)

        /** Then **/
        val event = receivedEvents[0] as TextEditorViewModel.EventShowToast
        assertEquals(R.string.text_editor_save_failed, event.messageResId)
    }

    /**
     * 텍스트 변경이 없을 때 onClickSave 시 저장 완료 토스트 이벤트가 발송되는지 검증
     *
     * Given: 텍스트 미변경(초기 상태) — saveCurrentFile이 실제 저장을 건너뜀
     * When: onClickSave 호출
     * Then: 파일 쓰기 없이 EventShowToast(R.string.text_editor_save_completed) 수신
     */
    @Test
    fun test_07_onClickSaveWithoutChangesSendsSaveCompletedToastEvent() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onClickSave()
        viewModel.events.collectEvents(receivedEvents, count = 1)

        /** Then **/
        val event = receivedEvents[0] as TextEditorViewModel.EventShowToast
        assertEquals(R.string.text_editor_save_completed, event.messageResId)
        coVerify(exactly = 0) { repository.saveTextFile(any(), any()) }
    }
}

/** Flow에서 [count]개 이벤트를 수집하는 테스트 헬퍼 */
private suspend fun Flow<ViewModelEvent>.collectEvents(
    into: MutableList<ViewModelEvent>,
    count: Int,
) {
    take(count).toList(into)
}
