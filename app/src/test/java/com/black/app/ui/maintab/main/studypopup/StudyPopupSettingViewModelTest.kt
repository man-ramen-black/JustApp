package com.black.app.ui.maintab.main.studypopup

import android.net.Uri
import com.black.app.R
import com.black.app.model.StudyPopupRepository
import com.black.app.service.ForegroundServiceController
import com.black.core.viewmodel.ViewModelEvent
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import java.io.IOException
import org.junit.runners.MethodSorters

/** [StudyPopupSettingViewModel]의 표시 설정·권한 안내·파일 불러오기와 저장·내용 편집 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupSettingViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: StudyPopupRepository

    @MockK
    lateinit var status: StudyPopupStatus

    @MockK
    lateinit var foregroundServiceController: ForegroundServiceController

    /** 저장된 표시 설정. setEnabled 저장값이 enabledFlow로 다시 흘러오도록 연결 */
    private val savedEnabled = MutableStateFlow(false)

    private val serviceRunning = MutableStateFlow(true)

    private val fileUri = Uri.parse("content://documents/study_popup.xml")

    @Before
    fun setupMocks() {
        every { repository.enabledFlow } returns savedEnabled
        coEvery { repository.setEnabled(any()) } answers {
            savedEnabled.value = firstArg()
            Result.success(Unit)
        }
        coEvery { repository.countContents() } returns 0
        coEvery { repository.hasContents() } returns false
        every { repository.loadLatestUri() } returns null
        every { repository.removeLatestUri() } just Runs
        coEvery { repository.getFileName(any()) } returns "study_popup.xml"
        coEvery { repository.keepPermission(any()) } returns true
        every { status.canDrawOverlays() } returns true
        every { foregroundServiceController.running } returns serviceRunning
    }

    /** ViewModel 생성 후 init의 비동기 초기 로드 완료까지 대기 */
    private fun createViewModel() = StudyPopupSettingViewModel(repository, status, foregroundServiceController)
        .also { waitCoroutine() }

    /**
     * 지금까지 쌓인 일회성 이벤트를 모두 수집(구독 전 누적분은 첫 구독자에게 전달)
     *
     * backgroundScope 작업은 waitCoroutine(advanceUntilIdle)이 실행하지 않으므로 일반 작업으로 구독한 뒤 취소
     */
    private fun TestScope.collectPendingEvents(viewModel: StudyPopupSettingViewModel): List<ViewModelEvent> {
        val receivedEvents = mutableListOf<ViewModelEvent>()
        val job = launch { viewModel.events.collect { receivedEvents.add(it) } }
        waitCoroutine()
        job.cancel()
        return receivedEvents
    }

    /**
     * 생성 시 표시 설정·서비스 실행·권한·팝업에 저장된 내용 개수를 상태에 반영하는지 검증
     *
     * Given: 표시 설정 켜짐, 서비스 중지, 권한 없음, 팝업에 저장된 내용 3개
     * When: ViewModel 생성
     * Then: uiState에 각 값 반영
     */
    @Test
    fun test_01_initReflectsStatus() = runTest {
        /** Given **/
        savedEnabled.value = true
        serviceRunning.value = false
        every { status.canDrawOverlays() } returns false
        coEvery { repository.countContents() } returns 3

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val uiState = viewModel.uiState.value
        assertTrue(uiState.enabled)
        assertFalse(uiState.serviceRunning)
        assertFalse(uiState.overlayPermissionGranted)
        assertEquals(3, uiState.savedContentsCount)
    }

    /**
     * 권한 없이 표시 설정을 켜면 저장하고 바로 권한 안내 다이얼로그를 띄우는지 검증
     *
     * Given: 다른 앱 위에 표시 권한 없음
     * When: setEnabled(true)
     * Then: repository.setEnabled(true) 호출, enabled true, 권한 안내 다이얼로그 표시
     */
    @Test
    fun test_02_enableWithoutPermissionShowsPermissionDialog() = runTest {
        /** Given **/
        every { status.canDrawOverlays() } returns false
        val viewModel = createViewModel()

        /** When **/
        viewModel.setEnabled(true)
        waitCoroutine()

        /** Then **/
        coVerify { repository.setEnabled(true) }
        assertTrue(viewModel.uiState.value.enabled)
        assertTrue(viewModel.uiState.value.showOverlayPermissionDialog)
    }

    /**
     * 권한이 있으면 표시 설정을 켜도 권한 안내 다이얼로그를 띄우지 않는지 검증
     *
     * Given: 다른 앱 위에 표시 권한 있음
     * When: setEnabled(true)
     * Then: enabled true, 권한 안내 다이얼로그 숨김
     */
    @Test
    fun test_03_enableWithPermissionSkipsDialog() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.setEnabled(true)
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.enabled)
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
    }

    /**
     * 팝업에 저장된 내용이 없으면 미리보기 대신 안내 토스트를 보내는지 검증
     *
     * Given: 팝업에 저장된 내용 없음
     * When: showPopupPreview
     * Then: 내용 없음 토스트 이벤트만 수신
     */
    @Test
    fun test_04_previewWithoutContentsShowsToast() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.showPopupPreview()
        waitCoroutine()

        /** Then **/
        assertEquals(
            listOf(StudyPopupSettingViewModel.EventShowToast(R.string.study_popup_no_contents)),
            collectPendingEvents(viewModel),
        )
    }

    /**
     * 권한 없이 미리보기를 누르면 오버레이를 띄우지 않고 권한 안내 다이얼로그를 띄우는지 검증
     *
     * Given: 팝업에 저장된 내용 있음, 다른 앱 위에 표시 권한 없음
     * When: showPopupPreview
     * Then: 권한 안내 다이얼로그 표시, 일회성 이벤트 없음
     */
    @Test
    fun test_05_previewWithoutPermissionShowsPermissionDialog() = runTest {
        /** Given **/
        coEvery { repository.hasContents() } returns true
        every { status.canDrawOverlays() } returns false
        val viewModel = createViewModel()

        /** When **/
        viewModel.showPopupPreview()
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.showOverlayPermissionDialog)
        assertTrue(collectPendingEvents(viewModel).isEmpty())
    }

    /**
     * 내용과 권한이 있으면 미리보기 오버레이 표시 이벤트를 보내는지 검증
     *
     * Given: 팝업에 저장된 내용 있음, 다른 앱 위에 표시 권한 있음
     * When: showPopupPreview
     * Then: ShowPopupPreview 이벤트 수신
     */
    @Test
    fun test_06_previewSendsShowPopupPreview() = runTest {
        /** Given **/
        coEvery { repository.hasContents() } returns true
        val viewModel = createViewModel()

        /** When **/
        viewModel.showPopupPreview()
        waitCoroutine()

        /** Then **/
        assertEquals(StudyPopupSettingViewModel.EventShowPopupPreview, viewModel.events.first())
    }

    /**
     * 권한 안내 다이얼로그에서 확인하면 다이얼로그를 닫고 권한 설정 화면으로 이동하는지 검증
     *
     * Given: 권한 안내 다이얼로그 표시
     * When: openOverlaySettings
     * Then: 다이얼로그 숨김, OpenOverlaySettings 이벤트 수신
     */
    @Test
    fun test_07_openOverlaySettingsClosesDialog() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.showOverlayPermissionDialog()

        /** When **/
        viewModel.openOverlaySettings()
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
        assertEquals(StudyPopupSettingViewModel.EventOpenOverlaySettings, viewModel.events.first())
    }

    /**
     * 화면을 열면 마지막으로 연 파일을 토스트 없이 자동으로 불러오는지 검증
     *
     * Given: 마지막으로 연 파일 uri와 그 파일의 내용 [a, b]
     * When: ViewModel 생성
     * Then: 내용 [a, b], 파일 이름 표시, 변경 없음, 일회성 이벤트 없음
     */
    @Test
    fun test_08_initLoadsLatestFile() = runTest {
        /** Given **/
        every { repository.loadLatestUri() } returns fileUri
        coEvery { repository.readFile(fileUri) } returns Result.success(listOf("a", "b"))

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val uiState = viewModel.uiState.value
        assertEquals(listOf("a", "b"), uiState.contents)
        assertEquals("study_popup.xml", uiState.fileName)
        assertFalse(uiState.modified)
        assertTrue(collectPendingEvents(viewModel).isEmpty())
    }

    /**
     * 마지막으로 연 파일을 읽지 못하면 기록을 지우고 다시 불러오도록 안내하는지 검증
     *
     * Given: 마지막으로 연 파일 uri, 읽기 실패(재설치로 권한 사라짐)
     * When: ViewModel 생성
     * Then: removeLatestUri 호출, 최근 파일 실패 토스트 수신
     */
    @Test
    fun test_09_initLatestFileFailureRemovesRecord() = runTest {
        /** Given **/
        every { repository.loadLatestUri() } returns fileUri
        coEvery { repository.readFile(fileUri) } returns Result.failure(SecurityException())

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        verify { repository.removeLatestUri() }
        assertEquals(
            listOf(StudyPopupSettingViewModel.EventShowToast(R.string.study_popup_load_failed_latest_file)),
            collectPendingEvents(viewModel),
        )
    }

    /**
     * 연 파일 없이 저장하면 새 파일 만들기 선택창을 띄우는지 검증
     *
     * Given: 연 파일 없음
     * When: saveFile
     * Then: CreateDocument 이벤트 수신, writeFile 미호출
     */
    @Test
    fun test_10_saveWithoutFileCreatesDocument() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.saveFile()
        waitCoroutine()

        /** Then **/
        assertEquals(StudyPopupSettingViewModel.EventCreateDocument, viewModel.events.first())
        coVerify(exactly = 0) { repository.writeFile(any(), any()) }
    }

    /**
     * 새 파일에 저장하면 그 파일이 연 파일이 되어 다음 저장부터 같은 파일에 덮어쓰는지 검증
     *
     * Given: 내용 [a] 추가, 저장 성공, 저장 후 팝업에 저장된 내용 1개
     * When: saveFileTo(uri) 후 saveFile
     * Then: writeFile(uri, [a]) 2회, 변경 없음, 팝업에 저장된 내용 1, 저장 완료 토스트 2회
     */
    @Test
    fun test_11_saveFileToKeepsFileForNextSave() = runTest {
        /** Given **/
        coEvery { repository.writeFile(any(), any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        viewModel.showAddDialog()
        viewModel.applyEdit("a")
        coEvery { repository.countContents() } returns 1

        /** When **/
        viewModel.saveFileTo(fileUri)
        waitCoroutine()
        viewModel.saveFile()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 2) { repository.writeFile(fileUri, listOf("a")) }
        val uiState = viewModel.uiState.value
        assertFalse(uiState.modified)
        assertEquals(1, uiState.savedContentsCount)
        assertEquals("study_popup.xml", uiState.fileName)
        val savedToast = StudyPopupSettingViewModel.EventShowToast(R.string.study_popup_saved)
        assertEquals(listOf(savedToast, savedToast), collectPendingEvents(viewModel))
    }

    /**
     * 내용 추가·수정·삭제가 목록과 변경 여부에 반영되는지 검증
     *
     * Given: 빈 목록
     * When: a 추가 → b 추가 → 첫 항목을 c로 수정 → 둘째 항목 삭제
     * Then: 각 단계 목록 [a] → [a, b] → [c, b] → [c], 변경 있음, 다이얼로그 모두 닫힘
     */
    @Test
    fun test_12_addEditDeleteContents() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When / Then **/
        viewModel.showAddDialog()
        assertEquals(0, viewModel.uiState.value.editingIndex)
        viewModel.applyEdit("a")
        assertEquals(listOf("a"), viewModel.uiState.value.contents)

        viewModel.showAddDialog()
        viewModel.applyEdit("b")
        assertEquals(listOf("a", "b"), viewModel.uiState.value.contents)

        viewModel.showEditDialog(0)
        viewModel.applyEdit("c")
        assertEquals(listOf("c", "b"), viewModel.uiState.value.contents)

        viewModel.showDeleteDialog(1)
        viewModel.deleteContents()
        val uiState = viewModel.uiState.value
        assertEquals(listOf("c"), uiState.contents)
        assertTrue(uiState.modified)
        assertNull(uiState.editingIndex)
        assertNull(uiState.deletingIndex)
    }

    /**
     * 다른 파일을 불러오면 편집 중인 내용을 파일 내용으로 바꾸고 불러오기 토스트를 보내는지 검증
     *
     * Given: 내용 [x] 편집 중(변경 있음), 불러올 파일 내용 [a, b]
     * When: loadFile(uri)
     * Then: 고른 파일의 영구 권한 요청, 내용 [a, b], 변경 없음, 불러오기 완료 토스트 수신
     */
    @Test
    fun test_13_loadFileReplacesContents() = runTest {
        /** Given **/
        coEvery { repository.readFile(fileUri) } returns Result.success(listOf("a", "b"))
        val viewModel = createViewModel()
        viewModel.showAddDialog()
        viewModel.applyEdit("x")

        /** When **/
        viewModel.loadFile(fileUri)
        waitCoroutine()

        /** Then **/
        coVerify { repository.keepPermission(fileUri) }
        assertEquals(listOf("a", "b"), viewModel.uiState.value.contents)
        assertFalse(viewModel.uiState.value.modified)
        assertEquals(
            listOf(StudyPopupSettingViewModel.EventShowToast(R.string.study_popup_loaded)),
            collectPendingEvents(viewModel),
        )
    }

    /**
     * 파일을 읽지 못하면 편집 중인 내용을 유지하고 실패 토스트를 보내는지 검증
     *
     * Given: 내용 [x] 편집 중, 파일 읽기 실패
     * When: loadFile(uri)
     * Then: 내용 [x] 유지, 불러오기 실패 토스트 수신
     */
    @Test
    fun test_14_loadFileFailureKeepsContents() = runTest {
        /** Given **/
        coEvery { repository.readFile(fileUri) } returns Result.failure(IllegalStateException())
        val viewModel = createViewModel()
        viewModel.showAddDialog()
        viewModel.applyEdit("x")

        /** When **/
        viewModel.loadFile(fileUri)
        waitCoroutine()

        /** Then **/
        assertEquals(listOf("x"), viewModel.uiState.value.contents)
        assertEquals(
            listOf(StudyPopupSettingViewModel.EventShowToast(R.string.study_popup_load_failed)),
            collectPendingEvents(viewModel),
        )
    }

    /**
     * 표시 설정 저장에 실패하면 실패 토스트를 보내고 권한 안내를 띄우지 않는지 검증
     *
     * Given: 저장이 실패하는 저장소, 다른 앱 위에 표시 권한 없음
     * When: setEnabled(true)
     * Then: 저장 실패 토스트 수신, 권한 다이얼로그 미표시
     */
    @Test
    fun test_15_setEnabledFailureShowsToast() = runTest {
        /** Given **/
        coEvery { repository.setEnabled(any()) } returns Result.failure(IOException("disk full"))
        every { status.canDrawOverlays() } returns false
        val viewModel = createViewModel()

        /** When **/
        viewModel.setEnabled(true)
        waitCoroutine()

        /** Then **/
        assertEquals(
            listOf(StudyPopupSettingViewModel.EventShowToast(R.string.study_popup_save_failed)),
            collectPendingEvents(viewModel),
        )
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
    }
}
