package com.black.feature.dday.widget

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.black.feature.dday.R
import com.black.feature.dday.model.DdayItem
import com.black.feature.dday.model.DdayStyle
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

/**
 * [measureLabelWidth]의 측정과 [findWidestText]의 가장 넓은 제목·날짜 고르기 검증
 *
 * 기본(레거시) 그래픽 모드의 `measureText`는 글꼴 폭이 아닌 상수에 가까운 값을 줘서, 네이티브 그래픽 모드로 실제 글꼴 폭을 씀.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DdayLabelWidthTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun regularWidthDp(label: String, textSizeSp: Int): Float {
        val displayMetrics = context.resources.displayMetrics
        val paint = TextPaint().apply {
            typeface = Typeface.DEFAULT
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat(), displayMetrics)
        }
        return ceil(paint.measureText(label) / displayMetrics.density)
    }

    /**
     * 라벨이 없으면 0dp인지 검증
     *
     * Given: 빈 라벨 목록
     * When: measureLabelWidth 호출
     * Then: 0.dp
     */
    @Test
    fun test_01_emptyLabelsReturnZero() = runTest {
        /** Given / When **/
        val width = measureLabelWidth(context, emptyList(), 20)

        /** Then **/
        assertEquals(0.dp, width)
    }

    /**
     * 가장 긴 라벨 기준으로 너비가 정해지는지 검증
     *
     * Given: D-9와 D-Day 라벨
     * When: 둘을 함께, 각각 따로 측정
     * Then: 함께 잰 값이 D-Day 단독 값과 같고 D-9 단독 값보다 큼
     */
    @Test
    fun test_02_widestLabelDecidesWidth() = runTest {
        /** Given / When **/
        val together = measureLabelWidth(context, listOf("D-9", "D-Day"), 20)
        val longOnly = measureLabelWidth(context, listOf("D-Day"), 20)
        val shortOnly = measureLabelWidth(context, listOf("D-9"), 20)

        /** Then **/
        assertEquals(longOnly, together)
        assertTrue(together > shortOnly)
    }

    /**
     * 굵게 그리는 라벨이 잘리지 않도록 일반 굵기보다 넓게 재는지 검증
     *
     * Given: 같은 라벨 D-Day, 20sp
     * When: measureLabelWidth 호출
     * Then: 일반 굵기(Typeface.DEFAULT) 측정값보다 큼
     */
    @Test
    fun test_03_boldWidthIsWiderThanRegular() = runTest {
        /** Given **/
        val regular = regularWidthDp("D-Day", 20)

        /** When **/
        val bold = measureLabelWidth(context, listOf("D-Day"), 20)

        /** Then **/
        assertTrue(bold.value > 0f)
        assertTrue("bold=${bold.value}, regular=$regular", bold.value > regular)
    }

    private fun itemOf(title: String?, dateText: String) =
        DdayItem(eventId = 1L, title = title, label = "D-1", dateText = dateText, begin = 1L, end = 2L)

    /**
     * 항목이 없으면 빈 문구를 고르는지 검증
     *
     * Given: 빈 항목 목록
     * When: findWidestText 호출
     * Then: 제목·날짜 모두 빈 문구
     */
    @Test
    fun test_04_emptyItemsReturnEmptyText() = runTest {
        /** Given / When **/
        val widest = findWidestText(context, emptyList(), DdayStyle())

        /** Then **/
        assertEquals(DdayWidestText(title = "", date = ""), widest)
    }

    /**
     * 제목과 날짜를 각각 가장 넓은 것으로 고르는지 검증
     *
     * Given: 짧은 제목에 긴 날짜인 항목과 긴 제목에 짧은 날짜인 항목
     * When: findWidestText 호출
     * Then: 제목은 긴 제목, 날짜는 긴 날짜
     */
    @Test
    fun test_05_widestTitleAndDateArePickedSeparately() = runTest {
        /** Given **/
        val items = listOf(itemOf("제모", "11/23 Mon AM 10:00"), itemOf("SDK개발팀 업무 티켓팅", "11/22 Sun"))

        /** When **/
        val widest = findWidestText(context, items, DdayStyle())

        /** Then **/
        assertEquals(DdayWidestText(title = "SDK개발팀 업무 티켓팅", date = "11/23 Mon AM 10:00"), widest)
    }

    /**
     * 글자 수가 아니라 그려지는 너비로 고르는지 검증
     *
     * Given: 글자 수는 적지만 넓은 한글 제목 "가나다라"와 글자 수는 많지만 좁은 제목 "iiiiii"
     * When: findWidestText 호출
     * Then: 제목은 "가나다라"
     */
    @Test
    fun test_06_widestIsDecidedByRenderedWidth() = runTest {
        /** Given **/
        val items = listOf(itemOf("iiiiii", "1"), itemOf("가나다라", "1"))

        /** When **/
        val widest = findWidestText(context, items, DdayStyle())

        /** Then **/
        assertEquals("가나다라", widest.title)
        assertTrue(regularWidthDp("가나다라", 16) > regularWidthDp("iiiiii", 16))
    }

    /**
     * 제목이 없는 항목은 제목 없음 문구로 재는지 검증
     *
     * Given: 제목이 null인 항목과 제목이 "a"인 항목
     * When: findWidestText 호출
     * Then: 제목은 제목 없음 문구
     */
    @Test
    fun test_07_untitledUsesPlaceholder() = runTest {
        /** Given **/
        val items = listOf(itemOf(null, "1"), itemOf("a", "1"))

        /** When **/
        val widest = findWidestText(context, items, DdayStyle())

        /** Then **/
        assertEquals(context.getString(R.string.dday_untitled), widest.title)
    }
}
