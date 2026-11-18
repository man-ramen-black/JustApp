package com.black.core.model

import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.test.BaseTest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [OverlayPosition]의 JSON 변환·저장, 복원 실패 시 기본값 반환, 저장값 Flow 변환 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OverlayPositionTest : BaseTest() {

    private val defaultPosition = OverlayPosition(HorizontalAnchor.LEFT, VerticalAnchor.CENTER)

    /**
     * toJson 결과를 fromJson으로 복원하면 원래 값과 같은지 검증
     *
     * Given: 오른쪽·아래, 여백 1·2·3·4인 위치
     * When: toJson 후 fromJson 호출
     * Then: 원래 값과 같음
     */
    @Test
    fun test_01_toJsonAndFromJsonRoundTrip() = runTest {
        /** Given **/
        val position = OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.BOTTOM, 1, 2, 3, 4)

        /** When **/
        val json = position.toJson()
        val restored = OverlayPosition.fromJson(json, OverlayPosition())

        /** Then **/
        assertEquals(position, restored)
    }

    /**
     * 저장된 값이 없으면 기본값을 반환하는지 검증
     *
     * Given: 기본 위치
     * When: fromJson(null) 호출
     * Then: 기본 위치와 같음
     */
    @Test
    fun test_02_fromJsonReturnsDefaultWhenNull() = runTest {
        /** Given / When **/
        val restored = OverlayPosition.fromJson(null, defaultPosition)

        /** Then **/
        assertEquals(defaultPosition, restored)
    }

    /**
     * 깨진 JSON이면 기본값을 반환하는지 검증
     *
     * Given: 기본 위치
     * When: fromJson("{broken") 호출
     * Then: 기본 위치와 같음
     */
    @Test
    fun test_03_fromJsonReturnsDefaultWhenBroken() = runTest {
        /** Given / When **/
        val restored = OverlayPosition.fromJson("{broken", defaultPosition)

        /** Then **/
        assertEquals(defaultPosition, restored)
    }

    /**
     * saveAsJson이 JSON 문자열을 저장 함수에 넘기고 그 결과를 돌려주는지 검증
     *
     * Given: 오른쪽·위, 여백 5·6·7·8인 위치, 받은 문자열을 기록하고 성공을 돌려주는 저장 함수
     * When: saveAsJson 호출
     * Then: 성공 결과, 저장 함수가 받은 문자열을 복원하면 원래 위치
     */
    @Test
    fun test_04_saveAsJsonPassesJsonToSave() = runTest {
        /** Given **/
        val position = OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.TOP, 5, 6, 7, 8)
        var savedJson: String? = null

        /** When **/
        val result = position.saveAsJson { json ->
            savedJson = json
            Result.success(Unit)
        }

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(position, OverlayPosition.fromJson(savedJson, defaultPosition))
    }

    /**
     * saveAsJson이 저장 함수의 실패 결과를 그대로 돌려주는지 검증
     *
     * Given: 실패 결과를 돌려주는 저장 함수
     * When: saveAsJson 호출
     * Then: 저장 함수가 돌려준 예외를 담은 실패 결과
     */
    @Test
    fun test_05_saveAsJsonReturnsSaveFailure() = runTest {
        /** Given **/
        val error = IOException("disk full")

        /** When **/
        val result = defaultPosition.saveAsJson { Result.failure(error) }

        /** Then **/
        assertSame(error, result.exceptionOrNull())
    }

    /**
     * fromJsonFlow가 저장값이 없거나 깨지면 기본값을, 저장값이 있으면 복원한 위치를 내보내는지 검증
     *
     * Given: null, 깨진 JSON, 오른쪽·아래 위치 JSON을 차례로 내보내는 Flow
     * When: fromJsonFlow로 변환해 모두 수집
     * Then: 기본 위치(null과 깨진 JSON은 같은 기본값이라 한 번), 오른쪽·아래 위치
     */
    @Test
    fun test_06_fromJsonFlowRestoresOrFallsBackToDefault() = runTest {
        /** Given **/
        val position = OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.BOTTOM, 1, 2, 3, 4)
        val jsonFlow = flowOf(null, "{broken", position.toJson())

        /** When **/
        val values = OverlayPosition.fromJsonFlow(jsonFlow, defaultPosition).toList()

        /** Then **/
        assertEquals(listOf(defaultPosition, position), values)
    }

    /**
     * fromJsonFlow가 같은 위치를 연달아 받으면 다시 내보내지 않는지 검증
     *
     * 같은 DataStore의 다른 키가 저장돼 같은 JSON이 다시 방출되는 경우
     *
     * Given: 위치 A, A, B, B의 JSON을 차례로 내보내는 Flow
     * When: fromJsonFlow로 변환해 모두 수집
     * Then: A, B만 수집
     */
    @Test
    fun test_07_fromJsonFlowSkipsSamePosition() = runTest {
        /** Given **/
        val first = OverlayPosition(HorizontalAnchor.LEFT, VerticalAnchor.TOP, marginTopDp = 10)
        val second = OverlayPosition(HorizontalAnchor.CENTER, VerticalAnchor.BOTTOM, marginBottomDp = 20)
        val jsonFlow = flowOf(first.toJson(), first.toJson(), second.toJson(), second.toJson())

        /** When **/
        val values = OverlayPosition.fromJsonFlow(jsonFlow, defaultPosition).toList()

        /** Then **/
        assertEquals(listOf(first, second), values)
    }
}
