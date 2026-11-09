package com.black.app.ui.maintab.main.studypopup.popup

import com.black.app.model.StudyPopupRepository
import com.black.app.model.database.studypopup.StudyPopupData
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [StudyPopupViewModel]의 내용 표시·닫기 이벤트 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupViewModelTest : BaseTest() {

    private val repository: StudyPopupRepository = mockk()

    /**
     * 저장소가 돌려준 팝업 내용을 모두 담아 표시 이벤트를 보내는지 검증
     *
     * Given: 팝업 내용 [a, b]를 돌려주는 저장소
     * When: loadContents 호출
     * Then: EventShowContents의 내용 집합이 [a, b], 로딩 표시 해제
     */
    @Test
    fun test_01_loadContentsSendsShowContents() = runTest {
        /** Given **/
        val contents = listOf(StudyPopupData.Contents("a"), StudyPopupData.Contents("b"))
        coEvery { repository.loadPopupContents() } returns contents
        val viewModel = StudyPopupViewModel(repository)

        /** When **/
        viewModel.loadContents()
        val event = viewModel.events.first() as StudyPopupViewModel.EventShowContents

        /** Then **/
        assertEquals(setOf("a", "b"), event.contents.map { it.contents }.toSet())
        assertFalse(viewModel.isProgress.get()!!)
    }

    /**
     * 닫기 요청 시 닫기 이벤트를 보내는지 검증
     *
     * Given: 새 ViewModel
     * When: close 호출
     * Then: EventClose 수신
     */
    @Test
    fun test_02_closeSendsCloseEvent() = runTest {
        /** Given **/
        val viewModel = StudyPopupViewModel(repository)

        /** When **/
        viewModel.close()

        /** Then **/
        assertEquals(StudyPopupViewModel.EventClose, viewModel.events.first())
    }
}
