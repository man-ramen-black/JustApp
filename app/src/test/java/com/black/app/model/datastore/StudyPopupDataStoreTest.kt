package com.black.app.model.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [StudyPopupDataStore]의 화면 켜짐 팝업 표시 설정 저장·조회 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupDataStoreTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStore = StudyPopupDataStore(context)

    /** 각 테스트 전 표시 설정 초기화(실제 DataStore가 프로세스 단일 인스턴스라 테스트 간 공유) */
    @Before
    fun clearEnabled() {
        runTest { dataStore.updateEnabled(false) }
    }

    /**
     * 켠 표시 설정을 값과 Flow로 그대로 다시 읽어오는지 검증
     *
     * Given: 표시 설정 꺼짐
     * When: true 저장
     * Then: getEnabled, getEnabledFlow 모두 true
     */
    @Test
    fun test_01_updateTrueAndGetRoundTrip() = runTest {
        /** Given / When **/
        dataStore.updateEnabled(true)

        /** Then **/
        assertTrue(dataStore.getEnabled())
        assertTrue(dataStore.getEnabledFlow().first())
    }

    /**
     * 켰다가 끈 표시 설정을 값과 Flow로 그대로 다시 읽어오는지 검증
     *
     * Given: 표시 설정 켜짐
     * When: false 저장
     * Then: getEnabled, getEnabledFlow 모두 false
     */
    @Test
    fun test_02_updateFalseAndGetRoundTrip() = runTest {
        /** Given **/
        dataStore.updateEnabled(true)

        /** When **/
        dataStore.updateEnabled(false)

        /** Then **/
        assertFalse(dataStore.getEnabled())
        assertFalse(dataStore.getEnabledFlow().first())
    }
}
