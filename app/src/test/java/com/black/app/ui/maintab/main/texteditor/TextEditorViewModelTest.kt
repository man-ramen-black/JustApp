package com.black.app.ui.maintab.main.texteditor

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.black.app.R
import com.black.app.model.database.studypopup.TextEditorRepository
import com.black.core.viewmodel.ViewModelEvent
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** [TextEditorViewModel]의 상태 관리·이벤트 발송 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextEditorViewModelTest : BaseTest() {

    private lateinit var repository: TextEditorRepository

    @Before
    fun setupRepository() {
        repository = mockk()
        coEvery { repository.fileNameFlow } returns flowOf("")
        coEvery { repository.currentFileUriFlow } returns flowOf(null)
        coEvery { repository.keepPermission(any()) } returns true
    }

    /** ViewModel 생성 및 init 코루틴 완료 대기 */
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
    fun test_03_onTextChangedUpdatesUiState() = runTest {
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
    fun test_04_onConfirmResetClearsTextAndResetsRepository() = runTest {
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
     * Then: 고른 문서의 영구 권한 요청, uiState.text == "loaded"
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
        coVerify { repository.keepPermission(uri) }
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

    /**
     * 마지막 문서의 영구 권한이 없어 init 로드가 실패하면 크래시나 빈 화면 대신 권한 요청 상태가 되는지 검증
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 SecurityException 실패 반환(재설치로 권한 소실)
     * When: ViewModel 생성(init 로드)
     * Then: uiState.permissionRequiredUri == 마지막 문서 uri + 권한 없는 uri로 자동 저장되지 않도록 repository.reset 호출
     */
    @Test
    fun test_08_initPermissionDeniedRequestsPermission() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } returns
            Result.failure(SecurityException("No persistable permission grants found"))
        coEvery { repository.reset() } just Runs

        /** When **/
        val viewModel = createViewModel()
        // IO 스레드 로드 실패 후 상태 갱신 대기
        val requestState = viewModel.uiState.filter { it.permissionRequiredUri != null }.first()

        /** Then **/
        assertEquals(uri, requestState.permissionRequiredUri)
        coVerify { repository.reset() }
    }

    /**
     * 권한 외 사유(파일 삭제 등)로 init 로드가 실패하면 권한 요청 없이 최근 문서 로드 실패 토스트가 발송되는지 검증
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 IllegalStateException 실패 반환
     * When: ViewModel 생성(init 로드)
     * Then: EventShowToast(text_editor_load_failed_latest_file) 수신 + permissionRequiredUri null
     */
    @Test
    fun test_09_initLoadFailureWithoutPermissionIssueSendsToastEvent() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } returns Result.failure(IllegalStateException("openInputStream failed"))
        coEvery { repository.reset() } just Runs
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        val viewModel = createViewModel()
        viewModel.events.collectEvents(receivedEvents, count = 1)

        /** Then **/
        assertEquals(
            TextEditorViewModel.EventShowToast(R.string.text_editor_load_failed_latest_file),
            receivedEvents[0],
        )
        assertEquals(null, viewModel.uiState.value.permissionRequiredUri)
    }

    /**
     * 권한 요청 다이얼로그 확인 시 같은 문서 위치로 선택창 실행 이벤트가 발송되고 다이얼로그가 닫히는지 검증
     *
     * Given: init 로드가 SecurityException으로 실패해 권한 요청 상태
     * When: onConfirmPermissionRequest 호출
     * Then: EventReopenDocument(마지막 문서 uri) 수신 + permissionRequiredUri null
     */
    @Test
    fun test_10_onConfirmPermissionRequestSendsReopenDocumentEvent() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val viewModel = createPermissionRequestedViewModel(uri)
        val receivedEvents = mutableListOf<ViewModelEvent>()

        /** When **/
        viewModel.onConfirmPermissionRequest()
        viewModel.events.collectEvents(receivedEvents, count = 1)

        /** Then **/
        assertEquals(TextEditorViewModel.EventReopenDocument(uri), receivedEvents[0])
        assertEquals(null, viewModel.uiState.value.permissionRequiredUri)
    }

    /**
     * 권한 요청 다이얼로그 취소 시 다이얼로그만 닫히는지 검증
     *
     * Given: init 로드가 SecurityException으로 실패해 권한 요청 상태
     * When: onDismissPermissionRequest 호출
     * Then: permissionRequiredUri null
     */
    @Test
    fun test_11_onDismissPermissionRequestClosesDialog() = runTest {
        /** Given **/
        val viewModel = createPermissionRequestedViewModel(mockk())

        /** When **/
        viewModel.onDismissPermissionRequest()

        /** Then **/
        assertEquals(null, viewModel.uiState.value.permissionRequiredUri)
    }

    /**
     * 화면 이탈 자동 저장이 ViewModel 정리 뒤에 끝나도 저장 완료 메시지가 전달되는지 검증(뒤로가기 이탈)
     *
     * Given: 문서를 불러와 텍스트 변경, 저장 성공 반환
     * When: onPause 호출 직후 ViewModelStore 정리(화면 이탈로 ViewModel 소멸) 후 저장 완료
     * Then: 변경 텍스트 저장 + 저장 결과 콜백으로 text_editor_save_completed 전달
     */
    @Test
    fun test_12_onPauseDeliversSaveCompletedAfterViewModelCleared() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val viewModelStore = ViewModelStore()
        val viewModel = createLoadedViewModel(uri, viewModelStore)
        coEvery { repository.saveTextFile(uri, any()) } returns Result.success(Unit)
        viewModel.onTextChanged("changed text")
        val savedMessageResId = CompletableDeferred<Int>()

        /** When **/
        viewModel.onPause { savedMessageResId.complete(it) }
        viewModelStore.clear()

        /** Then **/
        assertEquals(R.string.text_editor_save_completed, savedMessageResId.await())
        coVerify { repository.saveTextFile(uri, "changed text") }
    }

    /**
     * 화면 이탈 자동 저장이 실패하면 저장 실패 메시지가 전달되는지 검증
     *
     * Given: 문서를 불러와 텍스트 변경, 저장 실패 반환
     * When: onPause 호출 후 ViewModelStore 정리
     * Then: 저장 결과 콜백으로 text_editor_save_failed 전달
     */
    @Test
    fun test_13_onPauseDeliversSaveFailedAfterViewModelCleared() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val viewModelStore = ViewModelStore()
        val viewModel = createLoadedViewModel(uri, viewModelStore)
        coEvery { repository.saveTextFile(uri, any()) } returns Result.failure(Exception("save error"))
        viewModel.onTextChanged("changed text")
        val savedMessageResId = CompletableDeferred<Int>()

        /** When **/
        viewModel.onPause { savedMessageResId.complete(it) }
        viewModelStore.clear()

        /** Then **/
        assertEquals(R.string.text_editor_save_failed, savedMessageResId.await())
    }

    /**
     * 변경 사항 없이 화면을 이탈하면 저장하지 않고 메시지도 전달하지 않는지 검증
     *
     * Given: 문서를 불러온 뒤 텍스트 미변경
     * When: onPause 호출
     * Then: 파일 쓰기 없음 + 저장 결과 콜백 미호출
     */
    @Test
    fun test_14_onPauseWithoutChangesSkipsSaveAndMessage() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val viewModel = createLoadedViewModel(uri, ViewModelStore())
        var savedMessageResId: Int? = null

        /** When **/
        viewModel.onPause { savedMessageResId = it }
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 0) { repository.saveTextFile(any(), any()) }
        assertNull(savedMessageResId)
    }

    /** [viewModelStore]에 ViewModel을 만들고 init에서 [uri] 문서("initial text")를 불러올 때까지 대기 */
    private suspend fun createLoadedViewModel(uri: Uri, viewModelStore: ViewModelStore): TextEditorViewModel {
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } returns Result.success("initial text")
        val mainScope = TestScope(testDispatcherRule.testDispatcher)
        val factory = viewModelFactory { initializer { TextEditorViewModel(repository, mainScope) } }
        return ViewModelProvider.create(viewModelStore, factory)[TextEditorViewModel::class].also { viewModel ->
            waitCoroutine()
            viewModel.uiState.filter { it.text == "initial text" }.first()
        }
    }

    /** init 로드가 SecurityException으로 실패해 [uri] 권한 요청 상태가 된 ViewModel 생성 */
    private suspend fun createPermissionRequestedViewModel(uri: Uri): TextEditorViewModel {
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } returns
            Result.failure(SecurityException("No persistable permission grants found"))
        coEvery { repository.reset() } just Runs
        return createViewModel().also { viewModel ->
            viewModel.uiState.filter { it.permissionRequiredUri != null }.first()
        }
    }
}

/** Flow에서 [count]개 이벤트를 수집하는 테스트 헬퍼 */
private suspend fun Flow<ViewModelEvent>.collectEvents(
    into: MutableList<ViewModelEvent>,
    count: Int,
) {
    take(count).toList(into)
}
