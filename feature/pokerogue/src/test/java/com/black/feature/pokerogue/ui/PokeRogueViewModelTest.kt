package com.black.feature.pokerogue.ui

import androidx.lifecycle.ViewModelStore
import com.black.core.webkit.BKWebView
import com.black.feature.pokerogue.data.PokeRepository
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [PokeRogueViewModel]의 WebView 보관·파기 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PokeRogueViewModelTest : BaseTest() {

    private val repository: PokeRepository = mockk(relaxed = true)

    /**
     * 보관 중인 WebView가 없을 때만 만들고 이후에는 같은 WebView를 돌려주는지 검증
     *
     * Given: WebView를 보관하지 않은 새 ViewModel(프로세스 종료 뒤 복원과 같은 상태)
     * When: obtainWebView를 두 번 호출
     * Then: 생성 함수는 한 번만 호출, 두 결과가 같은 WebView, 콜백 등록 한 번, 시작 전 상태
     */
    @Test
    fun test_01_obtainWebViewCreatesOnce() = runTest {
        /** Given **/
        val viewModel = PokeRogueViewModel(repository)
        val webView: BKWebView = mockk(relaxed = true)
        var createCount = 0

        /** When **/
        val first = viewModel.obtainWebView { createCount++; webView }
        val second = viewModel.obtainWebView { createCount++; mockk(relaxed = true) }

        /** Then **/
        assertEquals(1, createCount)
        assertSame(first, second)
        assertSame(webView, viewModel.webView.value)
        assertFalse(viewModel.isStarted.value)
        verify(exactly = 1) { webView.addWebViewClientCallback(viewModel) }
    }

    /**
     * ViewModel이 파기되면 보관한 WebView를 파기하고 전역 JS 타이머를 되돌리는지 검증
     *
     * Given: WebView를 보관한 ViewModel
     * When: ViewModelStore를 비워 onCleared 호출
     * Then: 콜백 해제, resumeTimers, clear 호출 후 보관 값이 null
     */
    @Test
    fun test_02_clearedViewModelDestroysWebView() = runTest {
        /** Given **/
        val viewModel = PokeRogueViewModel(repository)
        val webView: BKWebView = mockk(relaxed = true)
        viewModel.obtainWebView { webView }
        val store = ViewModelStore().apply { put("pokeRogue", viewModel) }

        /** When **/
        store.clear()

        /** Then **/
        verify {
            webView.removeWebViewClientCallback(viewModel)
            webView.resumeTimers()
            webView.clear()
        }
        assertNull(viewModel.webView.value)
    }

    /**
     * 다시 불러오기 요청과 게임 시작 전 회전 요청이 타입 이벤트로 전달되는지 검증
     *
     * Given: 페이지를 열지 않은 WebView를 보관한 ViewModel
     * When: requestReload, rotate 호출
     * Then: EventShowReloadConfirm, EventRotate 순서로 수신
     */
    @Test
    fun test_03_reloadAndRotateSendEvents() = runTest {
        /** Given **/
        val viewModel = PokeRogueViewModel(repository)
        val webView: BKWebView = mockk(relaxed = true) { every { url } returns null }
        viewModel.obtainWebView { webView }

        /** When **/
        viewModel.requestReload()
        viewModel.rotate()
        val events = viewModel.events.take(2).toList()

        /** Then **/
        assertEquals(
            listOf(PokeRogueViewModel.EventShowReloadConfirm, PokeRogueViewModel.EventRotate),
            events,
        )
    }

    /**
     * 게임 페이지를 연 뒤에는 회전 요청을 무시하는지 검증
     *
     * Given: 페이지 주소가 있는 WebView를 보관한 ViewModel
     * When: rotate 호출 뒤 requestReload 호출
     * Then: 첫 이벤트가 EventShowReloadConfirm(회전 이벤트 없음)
     */
    @Test
    fun test_04_rotateIgnoredAfterPageOpened() = runTest {
        /** Given **/
        val viewModel = PokeRogueViewModel(repository)
        val webView: BKWebView = mockk(relaxed = true) { every { url } returns "https://pokerogue.net/" }
        viewModel.obtainWebView { webView }

        /** When **/
        viewModel.rotate()
        viewModel.requestReload()

        /** Then **/
        assertEquals(PokeRogueViewModel.EventShowReloadConfirm, viewModel.events.first())
    }
}
