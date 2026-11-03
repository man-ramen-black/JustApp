package com.black.app.ui.maintab.main.studypopup

import com.black.app.model.StudyPopupRepository
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [StudyPopupGlobal]의 화면 켜짐 시 팝업 표시 여부 판단 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupGlobalTest : BaseTest() {

    @MockK
    lateinit var repository: StudyPopupRepository

    @Before
    fun setupModel() {
        coEvery { repository.isEnabled() } returns true
        coEvery { repository.hasContents() } returns true
    }

    /**
     * 등록된 내용이 없으면 빈 팝업을 띄우지 않는지 검증
     *
     * Given: 표시 설정 켜짐, 등록된 내용 없음
     * When: 표시 여부 판단
     * Then: false
     */
    @Test
    fun test_01_canShowFalseWithoutContents() = runTest {
        /** Given **/
        coEvery { repository.hasContents() } returns false

        /** When / Then **/
        assertFalse(StudyPopupGlobal.canShow(repository))
    }

    /**
     * 표시 설정이 켜져 있고 등록된 내용이 있으면 팝업을 띄우는지 검증
     *
     * Given: 표시 설정 켜짐, 등록된 내용 있음
     * When: 표시 여부 판단
     * Then: true
     */
    @Test
    fun test_02_canShowTrueWhenEnabledWithContents() = runTest {
        /** Given **/
        // setupModel의 기본 응답(켜짐, 내용 있음) 사용

        /** When / Then **/
        assertTrue(StudyPopupGlobal.canShow(repository))
    }

    /**
     * 표시 설정이 꺼져 있으면 내용이 있어도 팝업을 띄우지 않는지 검증
     *
     * Given: 표시 설정 꺼짐, 등록된 내용 있음
     * When: 표시 여부 판단
     * Then: false, 내용 조회(DB) 생략
     */
    @Test
    fun test_03_canShowFalseWhenDisabled() = runTest {
        /** Given **/
        coEvery { repository.isEnabled() } returns false

        /** When / Then **/
        assertFalse(StudyPopupGlobal.canShow(repository))
        coVerify(exactly = 0) { repository.hasContents() }
    }
}
