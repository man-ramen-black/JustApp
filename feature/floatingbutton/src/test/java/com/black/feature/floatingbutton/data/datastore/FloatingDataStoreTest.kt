package com.black.feature.floatingbutton.data.datastore

import com.black.core.model.OverlayPosition
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.test.BaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [FloatingDataStore]의 표시 위치 기본값과 저장·조회 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FloatingDataStoreTest : BaseTest() {

    private val dataStore = FloatingDataStore(RuntimeEnvironment.getApplication())

    /** DataStore 파일이 테스트 사이에 남지 않도록 비움 */
    @Before
    fun clearDataStore() = runTest {
        dataStore.clear()
    }

    /**
     * 저장된 위치가 없으면 기본 위치를 내보내는지 검증
     *
     * Given: 저장값 없음
     * When: getPositionFlow 첫 값 조회
     * Then: DEFAULT_POSITION
     */
    @Test
    fun test_01_defaultPositionWhenNothingSaved() = runTest {
        /** Given **/
        // 저장값 없음

        /** When **/
        val position = dataStore.getPositionFlow().first()

        /** Then **/
        assertEquals(FloatingDataStore.DEFAULT_POSITION, position)
    }

    /**
     * 저장한 위치를 그대로 조회하는지 검증
     *
     * Given: 오른쪽 위 40dp 위치
     * When: putPosition 후 getPositionFlow 첫 값 조회
     * Then: 저장 성공 + 같은 위치
     */
    @Test
    fun test_02_putPositionThenGet() = runTest {
        /** Given **/
        val position = OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.TOP, marginTopDp = 40)

        /** When **/
        val result = dataStore.putPosition(position)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(position, dataStore.getPositionFlow().first())
    }
}
