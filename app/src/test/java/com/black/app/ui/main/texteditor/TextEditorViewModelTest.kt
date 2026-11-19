package com.black.app.ui.main.texteditor

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.black.app.R
import com.black.app.data.TextEditorRepository
import com.black.test.BaseTest
import com.black.test.collectDuring
import com.black.test.collectValues
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.FileNotFoundException
import java.io.IOException

/** [TextEditorViewModel]의 상태 관리·이벤트 발송 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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

        /** When **/
        viewModel.onClickLoad()
        val receivedEvents = viewModel.events.collectValues(count = 1)

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

        /** When **/
        viewModel.onClickSave()
        val receivedEvents = viewModel.events.collectValues(count = 1)

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
     * onConfirmReset 호출 시 repository.reset 실행, 텍스트 초기화, 다이얼로그 닫힘, 이후 화면 이탈에 저장하지 않음 검증
     *
     * Given: 문서("initial text")를 불러와 텍스트 변경, onClickNew로 showResetConfirm true, repository.reset 성공 반환
     * When: onConfirmReset 호출 후 코루틴 완료 대기, 이어서 onPause 호출
     * Then: repository.reset 호출 + uiState.text 빈 문자열 + showResetConfirm false + 파일 쓰기 없음 + 저장 결과 콜백 미호출
     */
    @Test
    fun test_04_onConfirmResetClearsTextAndResetsRepository() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val viewModel = createLoadedViewModel(uri, ViewModelStore())
        coEvery { repository.reset() } returns Result.success(Unit)
        viewModel.onTextChanged("draft")
        viewModel.onClickNew()
        assertTrue(viewModel.uiState.value.showResetConfirm)
        var savedMessageResId: Int? = null

        /** When **/
        viewModel.onConfirmReset()
        waitCoroutine()
        viewModel.onPause { savedMessageResId = it }
        waitCoroutine()

        /** Then **/
        coVerify { repository.reset() }
        assertEquals("", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.showResetConfirm)
        coVerify(exactly = 0) { repository.saveTextFile(any(), any()) }
        assertNull(savedMessageResId)
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
        // 불러오기 코루틴 완료 후 text 갱신 대기
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
        // init 코루틴 완료(originText·uiState.text = "initial text") 후 텍스트 변경
        viewModel.uiState.filter { it.text == "initial text" }.first()
        viewModel.onTextChanged("changed text")

        /** When **/
        viewModel.onClickSave()
        val receivedEvents = viewModel.events.collectValues(count = 1)

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

        /** When **/
        viewModel.onClickSave()
        val receivedEvents = viewModel.events.collectValues(count = 1)

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
        coEvery { repository.reset() } returns Result.success(Unit)

        /** When **/
        val viewModel = createViewModel()
        // init 로드 실패 후 상태 갱신 대기
        val requestState = viewModel.uiState.filter { it.permissionRequiredUri != null }.first()

        /** Then **/
        assertEquals(uri, requestState.permissionRequiredUri)
        coVerify { repository.reset() }
    }

    /**
     * 권한 외 사유(파일 삭제 등)로 init 로드가 실패하면 권한 요청 없이 최근 문서 로드 실패 토스트가 발송되는지 검증
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 FileNotFoundException 실패 반환(스트림을 열 수 없음)
     * When: ViewModel 생성(init 로드)
     * Then: EventShowToast(text_editor_load_failed_latest_file) 수신 + permissionRequiredUri null
     */
    @Test
    fun test_09_initLoadFailureWithoutPermissionIssueSendsToastEvent() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } returns Result.failure(FileNotFoundException("openInputStream failed"))
        coEvery { repository.reset() } returns Result.success(Unit)

        /** When **/
        val viewModel = createViewModel()
        val receivedEvents = viewModel.events.collectValues(count = 1)

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

        /** When **/
        viewModel.onConfirmPermissionRequest()
        val receivedEvents = viewModel.events.collectValues(count = 1)

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

    /**
     * 최근 문서 기록을 지우지 못하면 텍스트를 비우지 않고 실패 안내를 보내며, 로드가 끝난 뒤라 다시 불러오지 않는지 검증
     *
     * 비우면 원본 문서와 최근 문서 기록이 남은 채 새 문서처럼 보여, 이후 입력이 화면 이탈 자동 저장으로 옛 문서를 덮어씀.
     * 다시 불러오면 실패 전에 입력한 내용이 옛 문서 내용으로 바뀜.
     *
     * Given: 문서("initial text")를 불러온 뒤 "draft" 입력, repository.reset 실패 반환
     * When: onConfirmReset 호출
     * Then: EventShowToast(text_editor_new_failed) 수신 + uiState.text "draft" 유지 + loadTextFile 한 번만 호출 + showResetConfirm false
     */
    @Test
    fun test_15_onConfirmResetFailureKeepsTextAndSendsToastEvent() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val viewModel = createLoadedViewModel(uri, ViewModelStore())
        coEvery { repository.reset() } returns Result.failure(IOException("disk full"))
        viewModel.onTextChanged("draft")
        viewModel.onClickNew()

        /** When **/
        viewModel.onConfirmReset()
        val receivedEvents = viewModel.events.collectValues(count = 1)
        waitCoroutine()

        /** Then **/
        assertEquals(TextEditorViewModel.EventShowToast(R.string.text_editor_new_failed), receivedEvents[0])
        assertEquals("draft", viewModel.uiState.value.text)
        coVerify(exactly = 1) { repository.loadTextFile(uri) }
        assertFalse(viewModel.uiState.value.showResetConfirm)
    }

    /**
     * 시작 로드가 끝나기 전에 새 문서로 초기화하면 로드를 취소해, 늦게 끝난 로드가 옛 문서 내용을 되살리지 않는지 검증
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 [gate]가 열릴 때까지 대기, repository.reset 성공 반환
     * When: 로드 대기 중 onConfirmReset 호출, 이어서 [gate]를 열어 "old text" 반환
     * Then: uiState.text 빈 문자열 유지 + repository.reset 호출
     */
    @Test
    fun test_16_onConfirmResetCancelsPendingLatestFileLoad() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val gate = CompletableDeferred<Result<String>>()
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } coAnswers { gate.await() }
        coEvery { repository.reset() } returns Result.success(Unit)
        val viewModel = createViewModel()

        /** When **/
        viewModel.onConfirmReset()
        waitCoroutine()
        gate.complete(Result.success("old text"))
        waitCoroutine()

        /** Then **/
        assertEquals("", viewModel.uiState.value.text)
        coVerify { repository.reset() }
    }

    /**
     * 시작 로드를 취소한 뒤 초기화가 실패하면 최근 문서를 다시 불러오는지 검증
     *
     * 최근 문서 기록이 남은 채 내용 없이 두면 이후 입력이 화면 이탈 자동 저장으로 불러오지 않은 옛 문서를 덮어씀.
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 [gate]가 열릴 때까지 대기, repository.reset 실패 반환
     * When: 로드 대기 중 onConfirmReset 호출, 이어서 [gate]를 열어 "old text" 반환
     * Then: EventShowToast(text_editor_new_failed) 수신 + loadTextFile 두 번 호출 + uiState.text "old text"
     */
    @Test
    fun test_17_onConfirmResetFailureRestartsCancelledLatestFileLoad() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val gate = CompletableDeferred<Result<String>>()
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } coAnswers { gate.await() }
        coEvery { repository.reset() } returns Result.failure(IOException("disk full"))
        val viewModel = createViewModel()

        /** When **/
        viewModel.onConfirmReset()
        val receivedEvents = viewModel.events.collectValues(count = 1)
        gate.complete(Result.success("old text"))
        waitCoroutine()

        /** Then **/
        assertEquals(TextEditorViewModel.EventShowToast(R.string.text_editor_new_failed), receivedEvents[0])
        coVerify(exactly = 2) { repository.loadTextFile(uri) }
        assertEquals("old text", viewModel.uiState.value.text)
    }

    /**
     * 시작 로드가 파일을 읽는 동안 새 문서로 초기화하고 입력한 뒤 화면을 이탈해도 불러오지 않은 옛 문서에 저장하지 않는지 검증
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 취소돼도 [gate]가 열릴 때까지 돌아오지 않음(블로킹 읽기), repository.reset 성공 반환
     * When: 읽는 중 onConfirmReset 호출, "draft" 입력 후 onPause 호출, 이어서 [gate]를 열어 "old text" 반환
     * Then: 파일 쓰기 없음 + 저장 결과 콜백 미호출 + repository.reset 한 번 호출 + uiState.text 빈 문자열
     */
    @Test
    fun test_18_onPauseDuringBlockingLatestFileLoadAfterResetSkipsOldFile() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val gate = CompletableDeferred<Result<String>>()
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } coAnswers { awaitBlockingIo(gate) }
        coEvery { repository.reset() } returns Result.success(Unit)
        coEvery { repository.saveTextFile(any(), any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        var savedMessageResId: Int? = null

        /** When **/
        viewModel.onConfirmReset()
        waitCoroutine()
        viewModel.onTextChanged("draft")
        viewModel.onPause { savedMessageResId = it }
        waitCoroutine()
        gate.complete(Result.success("old text"))
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 0) { repository.saveTextFile(any(), any()) }
        assertNull(savedMessageResId)
        coVerify(exactly = 1) { repository.reset() }
        assertEquals("", viewModel.uiState.value.text)
    }

    /**
     * 초기화 실패 뒤 다시 시작한 시작 로드가 파일을 읽는 동안 입력하고 화면을 이탈해도 불러오지 않은 옛 문서에 저장하지 않는지 검증
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 호출마다 [firstGate]·[secondGate]가 열릴 때까지 돌아오지 않음(블로킹 읽기), repository.reset 실패 반환
     * When: 첫 읽기 중 onConfirmReset 호출 후 [firstGate]를 열고, 다시 시작한 읽기 중 "draft" 입력 후 onPause 호출, 이어서 [secondGate]를 열어 "old text" 반환
     * Then: 파일 쓰기 없음 + 저장 결과 콜백 미호출 + loadTextFile 두 번 호출 + uiState.text "old text"
     */
    @Test
    fun test_19_onPauseDuringRestartedLatestFileLoadSkipsOldFile() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val firstGate = CompletableDeferred<Result<String>>()
        val secondGate = CompletableDeferred<Result<String>>()
        val gates = ArrayDeque(listOf(firstGate, secondGate))
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } coAnswers { awaitBlockingIo(gates.removeFirst()) }
        coEvery { repository.reset() } returns Result.failure(IOException("disk full"))
        coEvery { repository.saveTextFile(any(), any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        var savedMessageResId: Int? = null

        /** When **/
        viewModel.onConfirmReset()
        waitCoroutine()
        firstGate.complete(Result.success("old text"))
        waitCoroutine()
        viewModel.onTextChanged("draft")
        viewModel.onPause { savedMessageResId = it }
        waitCoroutine()
        secondGate.complete(Result.success("old text"))
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 0) { repository.saveTextFile(any(), any()) }
        assertNull(savedMessageResId)
        coVerify(exactly = 2) { repository.loadTextFile(uri) }
        assertEquals("old text", viewModel.uiState.value.text)
    }

    /**
     * 저장 버튼 저장이 진행 중일 때 새 문서로 초기화하면 저장이 끝난 뒤 초기화해, 이후 화면 이탈이 옛 문서를 빈 내용으로 덮어쓰지 않는지 검증
     *
     * Given: 문서("initial text")를 불러와 "edited" 입력, 최근 문서 기록은 reset이 지우고 saveTextFile이 다시 씀, saveTextFile이 [saveGate]가 열릴 때까지 돌아오지 않음(블로킹 쓰기)
     * When: onClickSave로 저장 중 onConfirmReset 호출, [saveGate]를 연 뒤 onPause 호출
     * Then: "edited" 한 번만 저장 + 빈 내용 저장 없음 + 저장 결과 콜백 미호출 + uiState.text 빈 문자열
     */
    @Test
    fun test_20_onConfirmResetDuringSaveDoesNotSaveEmptyTextToOldFile() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val viewModel = createLoadedViewModel(uri, ViewModelStore())
        val latestFileUri = MutableStateFlow<Uri?>(uri)
        val saveGate = CompletableDeferred<Unit>()
        coEvery { repository.currentFileUriFlow } returns latestFileUri
        coEvery { repository.reset() } coAnswers {
            latestFileUri.value = null
            Result.success(Unit)
        }
        coEvery { repository.saveTextFile(uri, any()) } coAnswers {
            awaitBlockingIo(saveGate)
            latestFileUri.value = uri
            Result.success(Unit)
        }
        viewModel.onTextChanged("edited")
        var savedMessageResId: Int? = null

        /** When **/
        viewModel.onClickSave()
        waitCoroutine()
        viewModel.onConfirmReset()
        waitCoroutine()
        saveGate.complete(Unit)
        waitCoroutine()
        viewModel.onPause { savedMessageResId = it }
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { repository.saveTextFile(uri, "edited") }
        coVerify(exactly = 0) { repository.saveTextFile(any(), "") }
        assertNull(savedMessageResId)
        assertEquals("", viewModel.uiState.value.text)
    }

    /**
     * 불러오기 로드가 진행 중일 때 새 문서로 초기화하면 로드가 끝난 뒤 초기화해 최종 상태가 새 문서인지 검증
     *
     * Given: 최근 문서 없음, loadTextFile이 [gate]가 열릴 때까지 돌아오지 않음(블로킹 읽기), repository.reset 성공 반환
     * When: onDocumentOpened로 읽는 중 onConfirmReset 호출, [gate]를 열어 "opened text" 반환, 이어서 "draft" 입력 후 onPause 호출
     * Then: 초기화 뒤 uiState.text 빈 문자열 + 파일 쓰기 없음 + 저장 결과 콜백 미호출
     */
    @Test
    fun test_21_onConfirmResetDuringDocumentOpenEndsWithNewDocument() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val gate = CompletableDeferred<Result<String>>()
        coEvery { repository.loadTextFile(uri) } coAnswers { awaitBlockingIo(gate) }
        coEvery { repository.reset() } returns Result.success(Unit)
        coEvery { repository.saveTextFile(any(), any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        var savedMessageResId: Int? = null

        /** When **/
        viewModel.onDocumentOpened(uri)
        waitCoroutine()
        viewModel.onConfirmReset()
        waitCoroutine()
        gate.complete(Result.success("opened text"))
        waitCoroutine()
        val textAfterReset = viewModel.uiState.value.text
        viewModel.onTextChanged("draft")
        viewModel.onPause { savedMessageResId = it }
        waitCoroutine()

        /** Then **/
        assertEquals("", textAfterReset)
        coVerify(exactly = 0) { repository.saveTextFile(any(), any()) }
        assertNull(savedMessageResId)
    }

    /**
     * 시작 로드가 실패하고 최근 문서 기록 삭제까지 실패해도 이후 입력을 불러오지 못한 옛 문서에 저장하지 않는지 검증
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 FileNotFoundException 실패 반환, repository.reset 실패 반환
     * When: "draft" 입력 후 onPause 호출, 이어서 onClickSave 호출
     * Then: 파일 쓰기 없음 + 저장 결과 콜백 미호출 + 최근 문서 로드 실패 토스트 뒤 EventCreateDocument 수신
     */
    @Test
    fun test_22_initLoadAndResetFailureDoesNotSaveToOldFile() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } returns Result.failure(FileNotFoundException("openInputStream failed"))
        coEvery { repository.reset() } returns Result.failure(IOException("disk full"))
        coEvery { repository.saveTextFile(any(), any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        var savedMessageResId: Int? = null

        /** When **/
        viewModel.onTextChanged("draft")
        viewModel.onPause { savedMessageResId = it }
        waitCoroutine()
        viewModel.onClickSave()
        val receivedEvents = viewModel.events.collectValues(count = 2)

        /** Then **/
        coVerify(exactly = 0) { repository.saveTextFile(any(), any()) }
        assertNull(savedMessageResId)
        assertEquals(
            listOf(
                TextEditorViewModel.EventShowToast(R.string.text_editor_load_failed_latest_file),
                TextEditorViewModel.EventCreateDocument,
            ),
            receivedEvents,
        )
    }

    /**
     * 앞 확인의 초기화가 끝나기 전에 새 문서를 다시 확인하면 초기화를 한 번만 실행해 실패 안내도 한 번만 보내는지 검증
     *
     * Given: 마지막 문서 uri 저장, loadTextFile이 취소돼도 [gate]가 열릴 때까지 돌아오지 않음(블로킹 읽기), repository.reset 실패 반환
     * When: 읽는 중 onClickNew·onConfirmReset을 두 번 호출, 이어서 [gate]를 열어 "old text" 반환
     * Then: repository.reset 한 번 호출 + 이벤트는 EventShowToast(text_editor_new_failed) 하나 + 다시 불러온 uiState.text "old text"
     */
    @Test
    fun test_23_overlappingConfirmResetRunsResetOnce() = runTest {
        /** Given **/
        val uri = mockk<Uri>()
        val gate = CompletableDeferred<Result<String>>()
        coEvery { repository.currentFileUriFlow } returns flowOf(uri)
        coEvery { repository.loadTextFile(uri) } coAnswers { awaitBlockingIo(gate) }
        coEvery { repository.reset() } returns Result.failure(IOException("disk full"))
        val viewModel = createViewModel()

        /** When **/
        viewModel.onClickNew()
        viewModel.onConfirmReset()
        waitCoroutine()
        viewModel.onClickNew()
        viewModel.onConfirmReset()
        waitCoroutine()
        gate.complete(Result.success("old text"))
        val receivedEvents = collectDuring(viewModel.events)

        /** Then **/
        coVerify(exactly = 1) { repository.reset() }
        assertEquals(listOf(TextEditorViewModel.EventShowToast(R.string.text_editor_new_failed)), receivedEvents)
        assertEquals("old text", viewModel.uiState.value.text)
    }

    /**
     * IO 스레드 블로킹 읽기·쓰기 흉내
     *
     * 취소돼도 [gate]가 열릴 때까지 돌아오지 않고, 돌아올 때 취소됐으면 결과를 버림(Dispatchers.IO에서 메인으로 돌아올 때의 즉시 취소)
     */
    private suspend fun <T> awaitBlockingIo(gate: CompletableDeferred<T>): T =
        withContext(NonCancellable) { gate.await() }.also { currentCoroutineContext().ensureActive() }

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
        coEvery { repository.reset() } returns Result.success(Unit)
        return createViewModel().also { viewModel ->
            viewModel.uiState.filter { it.permissionRequiredUri != null }.first()
        }
    }
}
