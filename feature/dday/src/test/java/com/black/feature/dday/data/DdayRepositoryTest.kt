package com.black.feature.dday.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.black.feature.dday.data.datastore.DdayDataStore
import com.black.feature.dday.data.datastore.ddayDataStore
import com.black.feature.dday.model.DdayStyle
import com.black.test.BaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [DdayRepository]의 스타일 저장·조회와 제외 캘린더 토글 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DdayRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = DdayRepository(context)

    /** 각 테스트 전 저장소 초기화(테스트 간 격리) */
    @Before
    fun clearStore() {
        runTest { DdayDataStore(context).clear() }
    }

    /**
     * 저장값이 없으면 기본 스타일을 돌려주는지 검증
     *
     * Given: 빈 저장소
     * When: 스타일 조회
     * Then: 기본값 [DdayStyle]
     */
    @Test
    fun test_01_styleDefaultsWhenEmpty() = runTest {
        /** Given / When / Then **/
        assertEquals(DdayStyle(), repository.getStyle())
    }

    /**
     * 저장한 스타일 7개 값이 그대로 읽히는지 검증
     *
     * Given: 7개 값을 모두 기본값과 다르게 바꾼 스타일
     * When: 저장
     * Then: getStyle과 style Flow가 같은 값
     */
    @Test
    fun test_02_saveStyleRoundTrips() = runTest {
        /** Given **/
        val style = DdayStyle(
            labelTextSize = 30,
            titleTextSize = 22,
            dateTextSize = 14,
            labelGap = 4,
            titleDateGap = 6,
            itemGap = 10,
            padding = 20,
        )

        /** When **/
        val result = repository.saveStyle(style)

        /** Then **/
        assertEquals(true, result.isSuccess)
        assertEquals(style, repository.getStyle())
        assertEquals(style, repository.style.first())
    }

    /**
     * 저장소에 범위 밖 값이 있어도 읽을 때 범위로 맞춰지는지 검증
     *
     * Given: 제목 글씨 크기 키에 500을 직접 저장
     * When: 스타일 조회
     * Then: 제목 글씨 크기 48
     */
    @Test
    fun test_03_storedOutOfRangeIsClamped() = runTest {
        /** Given **/
        context.ddayDataStore.edit { it[intPreferencesKey("title_text_size")] = 500 }

        /** When / Then **/
        assertEquals(48, repository.getStyle().titleTextSize)
        assertEquals(48, repository.style.first().titleTextSize)
    }

    /**
     * 제외 캘린더 추가·해제가 집합에 반영되는지 검증
     *
     * Given: 빈 저장소
     * When: 3 제외, 5 제외, 3 다시 포함
     * Then: 결과와 조회가 모두 `setOf(5L)`
     */
    @Test
    fun test_04_setCalendarExcludedTogglesSet() = runTest {
        /** Given / When **/
        repository.setCalendarExcluded(3L, true)
        repository.setCalendarExcluded(5L, true)
        val result = repository.setCalendarExcluded(3L, false)

        /** Then **/
        assertEquals(setOf(5L), result.getOrThrow())
        assertEquals(setOf(5L), repository.getExcludedCalendarIds())
        assertEquals(setOf(5L), repository.excludedCalendarIds.first())
    }
}
