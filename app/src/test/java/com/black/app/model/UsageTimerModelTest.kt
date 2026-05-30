package com.black.app.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [UsageTimerModel]의 선택 앱 저장/조회 동작 검증
 */
class UsageTimerModelTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val model = UsageTimerModel(context)

    /**
     * 각 테스트 전 선택 앱 저장소 초기화(테스트 간 격리)
     */
    @Before
    fun clearSelectedApps() {
        model.saveSelectedApps(emptyList())
    }

    /**
     * 저장한 선택 앱 목록을 그대로 다시 읽어오는지 검증
     *
     * Given: 앱 두 개를 선택 목록으로 저장
     * When: 선택 목록 조회
     * Then: 저장한 순서·내용 그대로 반환
     */
    @Test
    fun test_01_saveAndGetSelectedAppsRoundTrip() {
        /** Given / When **/
        model.saveSelectedApps(listOf("com.a", "com.b"))

        /** Then **/
        assertEquals(listOf("com.a", "com.b"), model.getSelectedApps())
    }

    /**
     * 빈 목록 저장 시 빈 목록으로 조회되는지 검증
     *
     * Given: 빈 선택 목록 저장
     * When: 선택 목록 조회
     * Then: 빈 목록 반환(빈 문자열 split로 [""]가 되지 않음)
     */
    @Test
    fun test_02_getSelectedAppsReturnsEmptyWhenEmptySaved() {
        /** Given / When **/
        model.saveSelectedApps(emptyList())

        /** Then **/
        assertEquals(emptyList<String>(), model.getSelectedApps())
    }

    /**
     * 단일 앱 저장/조회 검증
     *
     * Given: 앱 한 개 저장
     * When: 선택 목록 조회
     * Then: 단일 항목 목록 반환
     */
    @Test
    fun test_03_saveSingleApp() {
        /** Given / When **/
        model.saveSelectedApps(listOf("com.only"))

        /** Then **/
        assertEquals(listOf("com.only"), model.getSelectedApps())
    }
}
