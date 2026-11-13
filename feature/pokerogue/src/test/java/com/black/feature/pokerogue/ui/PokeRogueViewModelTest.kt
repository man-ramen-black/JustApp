package com.black.feature.pokerogue.ui

import android.webkit.WebView
import com.black.feature.pokerogue.data.PokeRepository
import com.black.test.BaseTest
import com.black.test.collectValues
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [PokeRogueViewModel]의 게임 시작, 시작 상태 초기화, 이벤트 발송 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PokeRogueViewModelTest : BaseTest() {

    private val repository: PokeRepository = mockk(relaxed = true)

    /**
     * 게임 시작 시 시작 상태가 되고 게임 페이지 열기 이벤트가 전달되는지 검증
     *
     * Given: 게임을 시작하지 않은 ViewModel
     * When: play 호출
     * Then: isStarted가 true, 게임 주소의 EventLoadUrl 수신
     */
    @Test
    fun test_01_playStartsAndSendsLoadUrl() = runTest {
        /** Given **/
        val viewModel = PokeRogueViewModel(repository)

        /** When **/
        viewModel.play()

        /** Then **/
        assertTrue(viewModel.isStarted.value)
        assertEquals(PokeRogueViewModel.EventLoadUrl("https://pokerogue.net/"), viewModel.events.first())
    }

    /**
     * 새 WebView에 맞춰 시작·로딩 상태를 되돌리는지 검증
     *
     * Given: 게임을 시작하고 페이지를 불러오는 중인 ViewModel(Activity 재생성 뒤 남은 ViewModel과 같은 상태)
     * When: resetGameState 호출
     * Then: isStarted, isLoading 모두 false
     */
    @Test
    fun test_02_resetGameStateClearsStartAndLoading() = runTest {
        /** Given **/
        val viewModel = PokeRogueViewModel(repository)
        val webView: WebView = mockk(relaxed = true)
        viewModel.play()
        viewModel.onPageStarted(webView, "https://pokerogue.net/", null)

        /** When **/
        viewModel.resetGameState()

        /** Then **/
        assertFalse(viewModel.isStarted.value)
        assertFalse(viewModel.isLoading.value)
    }

    /**
     * 다시 불러오기 요청과 게임 시작 전 회전 요청이 타입 이벤트로 전달되는지 검증
     *
     * Given: 게임을 시작하지 않은 ViewModel
     * When: requestReload, rotate 호출
     * Then: EventShowReloadConfirm, EventRotate 순서로 수신
     */
    @Test
    fun test_03_reloadAndRotateSendEvents() = runTest {
        /** Given **/
        val viewModel = PokeRogueViewModel(repository)

        /** When **/
        viewModel.requestReload()
        viewModel.rotate()
        val events = viewModel.events.collectValues(count = 2)

        /** Then **/
        assertEquals(
            listOf(PokeRogueViewModel.EventShowReloadConfirm, PokeRogueViewModel.EventRotate),
            events,
        )
    }

    /**
     * 게임을 시작한 뒤에는 회전 요청을 무시하는지 검증
     *
     * Given: play로 게임을 시작한 ViewModel
     * When: rotate 호출 뒤 requestReload 호출
     * Then: EventLoadUrl 다음 이벤트가 EventShowReloadConfirm(회전 이벤트 없음)
     */
    @Test
    fun test_04_rotateIgnoredAfterPlay() = runTest {
        /** Given **/
        val viewModel = PokeRogueViewModel(repository)
        viewModel.play()

        /** When **/
        viewModel.rotate()
        viewModel.requestReload()
        val events = viewModel.events.collectValues(count = 2)

        /** Then **/
        assertEquals(
            listOf(PokeRogueViewModel.EventLoadUrl("https://pokerogue.net/"), PokeRogueViewModel.EventShowReloadConfirm),
            events,
        )
    }
}
