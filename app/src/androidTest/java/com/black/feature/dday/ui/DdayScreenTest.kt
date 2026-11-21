package com.black.feature.dday.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import com.black.app.testutil.BaseUiTest
import com.black.core.system.DeviceCalendar
import com.black.core.ui.theme.BlackTheme
import com.black.feature.dday.data.usecase.DdayLoadResult
import com.black.feature.dday.model.DdayStyle
import com.black.feature.dday.model.DdayStyleField
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [DdayScreen]이 권한·캘린더 목록·미리보기·조절 항목을 표시하는지 검증하는 Compose UI 테스트
 *
 * 권한과 캘린더 목록은 기기 상태로 갈려 Screen으로는 한 분기만 실행되므로
 * [DdayContent]에 테스트가 고른 가짜 상태를 주입해 분기마다 고정된 기대값으로 검증.
 * Hilt 주입 경로는 [test_06_screenHostsWithHilt]가 Screen으로 확인
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DdayScreenTest : BaseUiTest() {

    /** [hostDdayContent]로 호스팅한 콘텐츠에 주입하는 화면 상태 */
    private val contentUiState = mutableStateOf(DdayUiState())

    private val personalCalendar = DeviceCalendar(id = 3, displayName = "개인 캘린더", accountName = "me@example.com", color = 0xFF3366CC.toInt())
    private val workCalendar = DeviceCalendar(id = 5, displayName = "회사 캘린더", accountName = "work@example.com", color = 0xFFCC6633.toInt())

    /** [contentUiState]를 주입한 DdayContent를 HiltTestActivity에 setContent로 호스팅 */
    private fun hostDdayContent(
        onCalendarIncludedChange: (Long, Boolean) -> Unit = { _, _ -> },
        onAdjustStyle: (DdayStyleField, Int) -> Unit = { _, _ -> },
    ) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    DdayContent(
                        uiState = contentUiState.value,
                        onNavigateUp = {},
                        onRequestPermission = {},
                        onCalendarIncludedChange = onCalendarIncludedChange,
                        onAdjustStyle = onAdjustStyle,
                    )
                }
            }
        }
    }

    /**
     * 권한이 없으면 권한 행만 보이고 캘린더 목록은 없는지 검증
     *
     * Given:
     * - permissionGranted false
     * When:
     * - DdayContent 호스팅
     * Then:
     * - permission_row 표시, calendar_list 없음
     */
    @Test
    fun test_01_noPermissionShowsPermissionRowOnly() {
        /** Given **/
        contentUiState.value = DdayUiState(loaded = true, permissionGranted = false)

        /** When **/
        hostDdayContent()

        /** Then **/
        assertTagDisplayed(DdayTestTags.PERMISSION_ROW)
        assertTagNotExists(DdayTestTags.CALENDAR_LIST)
    }

    /**
     * 캘린더가 스위치와 함께 나열되고 스위치를 누르면 콜백이 불리는지 검증
     *
     * Given:
     * - 권한 허용, 캘린더 2개(둘째는 제외 상태)
     * When:
     * - 둘째 캘린더 스위치 클릭
     * Then:
     * - 두 표시 이름 표시, onCalendarIncludedChange(둘째 id, true) 1회 호출
     */
    @Test
    fun test_02_calendarsListedWithSwitch() {
        /** Given **/
        val changes = mutableListOf<Pair<Long, Boolean>>()
        contentUiState.value = DdayUiState(
            loaded = true,
            permissionGranted = true,
            calendars = listOf(
                DdayCalendarOption(personalCalendar, included = true),
                DdayCalendarOption(workCalendar, included = false),
            ),
        )
        hostDdayContent(onCalendarIncludedChange = { id, included -> changes.add(id to included) })
        assertTagDisplayed(DdayTestTags.CALENDAR_LIST)
        assertTextDisplayed(personalCalendar.displayName)
        assertTextDisplayed(workCalendar.displayName)

        /** When **/
        clickTag(DdayTestTags.calendarSwitch(workCalendar.id), scrollTo = true)

        /** Then **/
        assertEquals(listOf(workCalendar.id to true), changes)
    }

    /**
     * 제목 글씨 늘리기 버튼이 +1 조절 콜백을 부르는지 검증
     *
     * Given:
     * - 기본 스타일
     * When:
     * - 제목 글씨 늘리기 버튼 클릭
     * Then:
     * - onAdjustStyle(TITLE_TEXT_SIZE, 1) 1회 호출
     */
    @Test
    fun test_03_styleIncreaseInvokesCallback() {
        /** Given **/
        val adjustments = mutableListOf<Pair<DdayStyleField, Int>>()
        contentUiState.value = DdayUiState(loaded = true)
        hostDdayContent(onAdjustStyle = { field, delta -> adjustments.add(field to delta) })

        /** When **/
        clickTag(DdayTestTags.styleIncrease(DdayStyleField.TITLE_TEXT_SIZE), scrollTo = true)

        /** Then **/
        assertEquals(listOf(DdayStyleField.TITLE_TEXT_SIZE to 1), adjustments)
    }

    /**
     * 최댓값이면 늘리기 버튼만 비활성화되는지 검증
     *
     * Given:
     * - 제목 글씨 48(최댓값)
     * When:
     * - DdayContent 호스팅
     * Then:
     * - 늘리기 버튼 비활성, 줄이기 버튼 활성, 값 문구 48sp
     */
    @Test
    fun test_04_maxValueDisablesIncrease() {
        /** Given **/
        contentUiState.value = DdayUiState(
            loaded = true,
            style = DdayStyle().with(DdayStyleField.TITLE_TEXT_SIZE, 48),
        )

        /** When **/
        hostDdayContent()

        /** Then **/
        assertTagMatches(DdayTestTags.styleIncrease(DdayStyleField.TITLE_TEXT_SIZE), isNotEnabled())
        assertTagMatches(DdayTestTags.styleDecrease(DdayStyleField.TITLE_TEXT_SIZE), isEnabled())
        assertTagText(DdayTestTags.styleValue(DdayStyleField.TITLE_TEXT_SIZE), "48sp")
    }

    /**
     * 일정이 없으면 미리보기에 예시 일정이 보이는지 검증
     *
     * Given:
     * - preview Loaded(빈 목록)
     * When:
     * - DdayContent 호스팅
     * Then:
     * - 미리보기에 D-Day, D-9, D-14 표시
     */
    @Test
    fun test_05_previewShowsSampleWhenEmpty() {
        /** Given **/
        contentUiState.value = DdayUiState(loaded = true, preview = DdayLoadResult.Loaded(emptyList()))

        /** When **/
        hostDdayContent()

        /** Then **/
        assertTagDisplayed(DdayTestTags.PREVIEW, scrollTo = true)
        assertTextDisplayed("D-Day", scrollTo = true)
        assertTextDisplayed("D-9", scrollTo = true)
        assertTextDisplayed("D-14", scrollTo = true)
    }

    /**
     * DdayScreen을 Hilt로 띄우면 미리보기가 보이는지 검증
     *
     * Given:
     * - HiltTestActivity
     * When:
     * - DdayScreen 호스팅
     * Then:
     * - preview 표시
     */
    @Test
    fun test_06_screenHostsWithHilt() {
        /** Given / When **/
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    DdayScreen(onNavigateUp = {})
                }
            }
        }

        /** Then **/
        assertTagDisplayed(DdayTestTags.PREVIEW, scrollTo = true)
    }

    /**
     * 일정 개수를 1로 두면 미리보기에 한 개만 보이고 줄이기가 비활성화되는지 검증
     *
     * Given:
     * - 일정 개수 1(최솟값), preview Loaded(빈 목록)
     * When:
     * - DdayContent 호스팅
     * Then:
     * - 미리보기에 D-Day만 있고 D-9·D-14는 없음
     * - 일정 개수 값 문구 1개, 줄이기 버튼 비활성
     */
    @Test
    fun test_07_itemCountLimitsPreview() {
        /** Given **/
        contentUiState.value = DdayUiState(
            loaded = true,
            style = DdayStyle().with(DdayStyleField.ITEM_COUNT, 1),
            preview = DdayLoadResult.Loaded(emptyList()),
        )

        /** When **/
        hostDdayContent()

        /** Then **/
        assertTextDisplayed("D-Day", scrollTo = true)
        assertTextNotExists("D-9")
        assertTextNotExists("D-14")
        assertTagText(DdayTestTags.styleValue(DdayStyleField.ITEM_COUNT), "1개")
        assertTagMatches(DdayTestTags.styleDecrease(DdayStyleField.ITEM_COUNT), isNotEnabled())
    }
}
