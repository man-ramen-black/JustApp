package com.black.app.model.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.black.core.model.blackDataStore
import com.black.test.BaseTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File

private const val CORRUPTION_TEST_NAME = "foregroundServiceCorruptionTest"

/** 손상 파일 복구 검증용 DataStore(실제 저장소와 같은 blackDataStore·이관, 다른 파일) */
private val Context.corruptionTestDataStore by blackDataStore(
    name = CORRUPTION_TEST_NAME,
    produceMigrations = { ForegroundServiceDataStore.createMigrations(it) },
)

/** [ForegroundServiceDataStore]의 실행 설정 저장·조회와 기존 SharedPreferences 이관 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ForegroundServiceDataStoreTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStore = ForegroundServiceDataStore(context)

    /** 각 테스트 전 실행 설정 초기화(실제 DataStore가 프로세스 단일 인스턴스라 테스트 간 공유) */
    @Before
    fun clearActivated() {
        runTest { dataStore.updateActivated(false) }
    }

    /**
     * 저장한 실행 설정을 값과 Flow로 그대로 다시 읽어오는지 검증
     *
     * Given: 실행 설정 false
     * When: true 저장
     * Then: getActivated, getActivatedFlow 모두 true
     */
    @Test
    fun test_01_updateAndGetActivatedRoundTrip() = runTest {
        /** Given / When **/
        dataStore.updateActivated(true)

        /** Then **/
        assertTrue(dataStore.getActivated())
        assertTrue(dataStore.getActivatedFlow().first())
    }

    /**
     * 기존 SharedPreferences의 실행 설정을 복사하고 원본 키·파일은 그대로 두는지 검증
     *
     * Given: SharedPreferences(Foreground)에 ForegroundServiceActivated true만 저장
     * When: 이관 목록을 넣은 새 DataStore 첫 조회
     * Then: DataStore 실행 설정 true, SharedPreferences 실행 설정 true 유지(SharedPreferences를 읽는 다른 빌드 호환)
     */
    @Test
    fun test_02_migrationCopiesActivatedAndKeepsSource() = runTest {
        /** Given **/
        val sharedPreferences = context.getSharedPreferences("${context.packageName}.Foreground", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .clear()
            .putBoolean("ForegroundServiceActivated", true)
            .commit()
        // 프로세스 단일 인스턴스는 이미 이관을 마쳤을 수 있어 같은 이관 목록으로 별도 파일 DataStore 생성
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val migrationDataStore = PreferenceDataStoreFactory.create(
            migrations = ForegroundServiceDataStore.createMigrations(context),
            scope = scope,
            produceFile = { File(context.filesDir, "datastore/foregroundServiceMigrationTest.preferences_pb") },
        )

        /** When **/
        val preferences = migrationDataStore.data.first()
        scope.cancel()

        /** Then **/
        assertEquals(true, preferences[booleanPreferencesKey("ForegroundServiceActivated")])
        assertTrue(sharedPreferences.getBoolean("ForegroundServiceActivated", false))
    }

    /**
     * DataStore에 실행 설정이 이미 있으면 다른 빌드가 SharedPreferences에 쓴 값으로 덮어쓰지 않는지 검증
     *
     * Given: SharedPreferences 실행 설정 true, DataStore 실행 설정 false
     * When: 이관 필요 여부 확인
     * Then: 이관하지 않음
     */
    @Test
    fun test_03_migrationSkipsWhenDataStoreHasActivated() = runTest {
        /** Given **/
        context.getSharedPreferences("${context.packageName}.Foreground", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("ForegroundServiceActivated", true)
            .commit()
        val migration = ForegroundServiceDataStore.createMigrations(context).single()
        val currentData = preferencesOf(booleanPreferencesKey("ForegroundServiceActivated") to false)

        /** When **/
        val shouldMigrate = migration.shouldMigrate(currentData)

        /** Then **/
        assertFalse(shouldMigrate)
    }

    /**
     * 파일이 손상돼도 빈 값으로 교체한 뒤 SharedPreferences 값으로 다시 채우고 저장할 수 있는지 검증
     *
     * Given: SharedPreferences 실행 설정 true, DataStore 파일 내용이 protobuf가 아닌 손상 데이터
     * When: 같은 blackDataStore·이관으로 만든 DataStore 조회 후 false 저장
     * Then: 첫 조회 true(이관으로 복원), 저장 후 조회 false
     */
    @Test
    fun test_04_corruptedFileIsReplacedAndWritable() = runTest {
        /** Given **/
        context.getSharedPreferences("${context.packageName}.Foreground", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("ForegroundServiceActivated", true)
            .commit()
        // 길이 127인 필드를 선언하고 내용이 없어 읽는 도중 끝나는 protobuf
        context.preferencesDataStoreFile(CORRUPTION_TEST_NAME)
            .apply { parentFile?.mkdirs() }
            .writeBytes(byteArrayOf(0x0A, 0x7F))
        val key = booleanPreferencesKey("ForegroundServiceActivated")
        val corruptedDataStore = context.corruptionTestDataStore

        /** When **/
        val restored = corruptedDataStore.data.first()[key]
        corruptedDataStore.edit { it[key] = false }
        val saved = corruptedDataStore.data.first()[key]

        /** Then **/
        assertEquals(true, restored)
        assertEquals(false, saved)
    }
}
