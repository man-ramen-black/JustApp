package com.black.feature.dday.widget

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.ui.unit.dp
import androidx.glance.action.ActionModifier
import androidx.glance.appwidget.action.StartActivityIntentAction
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.text.EmittableText
import androidx.glance.text.TextAlign
import androidx.test.core.app.ApplicationProvider
import com.black.feature.dday.data.usecase.DdayLoadResult
import com.black.feature.dday.model.DdayItem
import com.black.feature.dday.model.DdayStyle
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [DdayWidgetContent]의 상태별 화면 문구와 클릭 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DdayWidgetContentTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val style = DdayStyle()

    private val removalItem = DdayItem(
        eventId = 11L,
        title = "제모",
        label = "D-Day",
        dateText = "9/4 Sun",
        begin = 1_000L,
        end = 2_000L,
    )
    private val phishingItem = DdayItem(
        eventId = 12L,
        title = "피싱 3달",
        label = "D-9",
        dateText = "9/13 Tue PM 3:00",
        begin = 3_000L,
        end = 4_000L,
    )

    /**
     * 클릭 동작 중 [StartActivityIntentAction]의 인텐트 중 하나가 [predicate]를 만족하는 노드 조건
     *
     * `hasStartActivityClickAction(Intent)`는 `filterEquals`로 비교해 extra를 보지 않으므로, extra 확인용으로 직접 만듦.
     */
    private fun hasStartActivityIntent(predicate: (Intent) -> Boolean) =
        GlanceNodeMatcher<MappedNode>("hasStartActivityIntent") { node ->
            node.value.emittable.modifier.any { element ->
                val action = (element as? ActionModifier)?.action as? StartActivityIntentAction
                action != null && predicate(action.intent)
            }
        }

    /**
     * 불러온 항목의 남은 날짜·제목·날짜 문구가 모두 표시되는지 검증
     *
     * Given: D-Day 제모와 D-9 피싱 3달 항목이 담긴 Loaded 결과
     * When: 위젯 본문을 그림
     * Then: 라벨·제목·날짜 노드가 각각 존재
     */
    @Test
    fun test_01_loadedShowsLabelTitleDate() = runGlanceAppWidgetUnitTest {
        /** Given **/
        val result = DdayLoadResult.Loaded(listOf(removalItem, phishingItem))
        setContext(context)

        /** When **/
        provideComposable { DdayWidgetContent(style, result, 40.dp) }

        /** Then **/
        onNode(hasText("D-Day")).assertExists()
        onNode(hasText("제모")).assertExists()
        onNode(hasText("9/4 Sun")).assertExists()
        onNode(hasText("D-9")).assertExists()
        onNode(hasText("피싱 3달")).assertExists()
        onNode(hasText("9/13 Tue PM 3:00")).assertExists()
    }

    /**
     * 캘린더 권한이 없을 때 안내 문구와 설정 화면 딥링크 클릭이 있는지 검증
     *
     * Given: NoPermission 결과
     * When: 위젯 본문을 그림
     * Then: 안내 문구 노드가 있고, 그 문구를 품은 클릭 영역이 black://navigate/dday 로 액티비티를 엶
     */
    @Test
    fun test_02_noPermissionShowsGuide() = runGlanceAppWidgetUnitTest {
        /** Given **/
        setContext(context)
        val expectedIntent = Intent(Intent.ACTION_VIEW, DDAY_SETTINGS_DEEPLINK_URI).setPackage(context.packageName)

        /** When **/
        provideComposable { DdayWidgetContent(style, DdayLoadResult.NoPermission, 0.dp) }

        /** Then **/
        onNode(hasText("캘린더 권한이 필요합니다.")).assertExists()
        onNode(hasClickAction().and(hasAnyDescendant(hasText("캘린더 권한이 필요합니다."))))
            .assertHasStartActivityClickAction(expectedIntent)
        assertEquals("black://navigate/dday", DDAY_SETTINGS_DEEPLINK_URI.toString())
    }

    /**
     * 일정이 없을 때 빈 상태 문구가 표시되는지 검증
     *
     * Given: 빈 목록의 Loaded 결과
     * When: 위젯 본문을 그림
     * Then: 빈 상태 문구 노드가 존재
     */
    @Test
    fun test_03_emptyShowsEmptyMessage() = runGlanceAppWidgetUnitTest {
        /** Given **/
        setContext(context)

        /** When **/
        provideComposable { DdayWidgetContent(style, DdayLoadResult.Loaded(emptyList()), 0.dp) }

        /** Then **/
        onNode(hasText("다가오는 일정이 없습니다.")).assertExists()
    }

    /**
     * 조회 실패 때 실패 문구가 표시되는지 검증
     *
     * Given: Failed 결과
     * When: 위젯 본문을 그림
     * Then: 실패 문구 노드가 존재
     */
    @Test
    fun test_04_failedShowsFailedMessage() = runGlanceAppWidgetUnitTest {
        /** Given **/
        setContext(context)

        /** When **/
        provideComposable { DdayWidgetContent(style, DdayLoadResult.Failed, 0.dp) }

        /** Then **/
        onNode(hasText("일정을 불러오지 못했습니다.")).assertExists()
    }

    /**
     * 제목이 null인 항목이 제목 없음 문구로 표시되는지 검증
     *
     * Given: title이 null인 항목
     * When: 위젯 본문을 그림
     * Then: "(제목 없음)" 노드가 존재
     */
    @Test
    fun test_05_untitledShowsPlaceholder() = runGlanceAppWidgetUnitTest {
        /** Given **/
        val result = DdayLoadResult.Loaded(listOf(removalItem.copy(title = null)))
        setContext(context)

        /** When **/
        provideComposable { DdayWidgetContent(style, result, 40.dp) }

        /** Then **/
        onNode(hasText("(제목 없음)")).assertExists()
    }

    /**
     * 항목을 누르면 캘린더 일정 상세가 열리는 인텐트인지 검증
     *
     * Given: eventId 11, begin 1000, end 2000인 항목
     * When: 위젯 본문을 그림
     * Then: 항목 클릭 영역이 ACTION_VIEW, content://com.android.calendar/events/11, 시작·종료 시각 extra를 가진 인텐트를 엶
     */
    @Test
    fun test_06_itemClickOpensCalendarEvent() = runGlanceAppWidgetUnitTest {
        /** Given **/
        val result = DdayLoadResult.Loaded(listOf(removalItem, phishingItem))
        setContext(context)

        /** When **/
        provideComposable { DdayWidgetContent(style, result, 40.dp) }

        /** Then **/
        onNode(hasClickAction().and(hasAnyDescendant(hasText("제모"))))
            .assert(
                hasStartActivityIntent { intent ->
                    intent.action == Intent.ACTION_VIEW &&
                        intent.data.toString() == "content://com.android.calendar/events/11" &&
                        intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L) == 1_000L &&
                        intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L) == 2_000L
                },
            ) { "제모 항목 클릭이 캘린더 일정 상세 인텐트가 아님" }
    }

    /**
     * 남은 날짜 라벨이 칸 안에서 가운데 정렬되는지 검증
     *
     * Given: D-Day 제모와 D-9 피싱 3달 항목이 담긴 Loaded 결과
     * When: 위젯 본문을 그림
     * Then: D-Day·D-9 라벨 노드의 글자 정렬이 가운데
     */
    @Test
    fun test_07_labelIsCenterAligned() = runGlanceAppWidgetUnitTest {
        /** Given **/
        val result = DdayLoadResult.Loaded(listOf(removalItem, phishingItem))
        setContext(context)
        val centerAligned = GlanceNodeMatcher<MappedNode>("centerAligned") { node ->
            (node.value.emittable as? EmittableText)?.style?.textAlign == TextAlign.Center
        }

        /** When **/
        provideComposable { DdayWidgetContent(style, result, 40.dp) }

        /** Then **/
        onNode(hasText("D-Day")).assert(centerAligned) { "D-Day 라벨이 가운데 정렬이 아님" }
        onNode(hasText("D-9")).assert(centerAligned) { "D-9 라벨이 가운데 정렬이 아님" }
    }
}
