package com.black.core.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import java.io.IOException

/** [BaseDataStore] 하위 DataStore의 손상 파일 복구, 저장 실패 전달, 읽고 고쳐 쓰기, 여러 키 함께 저장 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BaseDataStoreTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /** 손상 파일 복구 검증용 DataStore */
    private class CorruptionTestDataStore(context: Context) : BaseDataStore(context) {
        companion object {
            const val NAME = "corruptionTest"

            private val Context.corruptionTestDataStore by blackDataStore(NAME)

            private val KEY_TEXT = stringPreferencesKey("text")
        }

        override fun getDataStore(context: Context): DataStore<Preferences> = context.corruptionTestDataStore

        suspend fun updateText(text: String): Result<Unit> = update(KEY_TEXT, text)

        suspend fun getText(): String = get(KEY_TEXT, "")
    }

    /**
     * 파일이 손상돼 있어도 손상 파일을 빈 값으로 바꾸고 이후 저장·조회가 되는지 검증
     *
     * Given: protobuf로 읽을 수 없는 DataStore 파일
     * When: 값 저장 후 조회
     * Then: 저장 오류 없음 + 저장한 값 조회
     */
    @Test
    fun test_01_corruptedFileIsReplacedAndWritable() = runTest {
        /** Given **/
        // 길이 127인 필드를 선언하고 내용이 없어 읽는 도중 끝나는 protobuf
        context.preferencesDataStoreFile(CorruptionTestDataStore.NAME)
            .apply { parentFile?.mkdirs() }
            .writeBytes(byteArrayOf(0x0A, 0x7F))
        val dataStore = CorruptionTestDataStore(context)

        /** When **/
        val result = dataStore.updateText("saved")

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals("saved", dataStore.getText())
    }

    /** 저장이 항상 [error]로 실패하는 DataStore를 쓰는 저장소 */
    private class FailingDataStore(context: Context, error: Throwable) : BaseDataStore(context) {
        private val dataStore: DataStore<Preferences> = mockk {
            coEvery { updateData(any()) } throws error
        }

        override fun getDataStore(context: Context): DataStore<Preferences> = dataStore

        suspend fun updateText(text: String): Result<Unit> = update(stringPreferencesKey("text"), text)
    }

    /**
     * 디스크 쓰기 실패는 예외 대신 실패 결과로 돌려주는지 검증
     *
     * Given: 저장 시 IOException을 던지는 DataStore
     * When: 값 저장
     * Then: 실패 결과, 원인은 IOException
     */
    @Test
    fun test_02_writeFailureReturnsFailure() = runTest {
        /** Given **/
        val dataStore = FailingDataStore(context, IOException("disk full"))

        /** When **/
        val result = dataStore.updateText("saved")

        /** Then **/
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    /**
     * 코루틴 취소는 실패 결과로 바꾸지 않고 다시 던지는지 검증
     *
     * Given: 저장 시 CancellationException을 던지는 DataStore
     * When: 값 저장
     * Then: CancellationException 전파
     */
    @Test
    fun test_03_cancellationIsRethrown() = runTest {
        /** Given **/
        val dataStore = FailingDataStore(context, CancellationException("cancelled"))

        /** When **/
        val thrown = runCatching { dataStore.updateText("saved") }.exceptionOrNull()

        /** Then **/
        assertTrue(thrown is CancellationException)
    }

    /** 읽고 고쳐 쓰기 검증용 DataStore */
    private class UpdateWithTestDataStore(context: Context) : BaseDataStore(context) {
        companion object {
            private val Context.updateWithTestDataStore by blackDataStore("updateWithTest")

            private val KEY_SET = stringSetPreferencesKey("set")
        }

        override fun getDataStore(context: Context): DataStore<Preferences> = context.updateWithTestDataStore

        suspend fun add(value: String): Result<Set<String>> = updateWith(KEY_SET, emptySet()) { it + value }

        suspend fun getSet(): Set<String> = get(KEY_SET, emptySet())
    }

    /**
     * 읽고 고쳐 쓰기가 저장된 값에 변환을 이어 적용하는지 검증
     *
     * Given: 저장값 없음
     * When: 값 두 개를 차례로 추가
     * Then: 첫 결과는 기본값에서 시작한 집합, 둘째 결과와 저장값은 둘 다 포함
     */
    @Test
    fun test_04_updateWithAppliesTransformToStoredValue() = runTest {
        /** Given **/
        val dataStore = UpdateWithTestDataStore(context)

        /** When **/
        val first = dataStore.add("a")
        val second = dataStore.add("b")

        /** Then **/
        assertEquals(setOf("a"), first.getOrNull())
        assertEquals(setOf("a", "b"), second.getOrNull())
        assertEquals(setOf("a", "b"), dataStore.getSet())
    }

    /**
     * 읽고 고쳐 쓰기의 저장 실패가 실패 결과로, 취소가 재던짐으로 처리되는지 검증
     *
     * Given: 저장 시 IOException, CancellationException을 던지는 DataStore
     * When: updateWith 호출
     * Then: 실패 결과(IOException), 취소는 전파
     */
    @Test
    fun test_05_updateWithFailureAndCancellation() = runTest {
        /** Given **/
        class FailingWithDataStore(context: Context, error: Throwable) : BaseDataStore(context) {
            private val dataStore: DataStore<Preferences> = mockk {
                coEvery { updateData(any()) } throws error
            }

            override fun getDataStore(context: Context): DataStore<Preferences> = dataStore

            suspend fun add(): Result<String> = updateWith(stringPreferencesKey("text"), "") { it + "x" }
        }

        /** When **/
        val failure = FailingWithDataStore(context, IOException("disk full")).add()
        val thrown = runCatching { FailingWithDataStore(context, CancellationException("cancelled")).add() }
            .exceptionOrNull()

        /** Then **/
        assertTrue(failure.exceptionOrNull() is IOException)
        assertTrue(thrown is CancellationException)
    }

    /** 여러 키 함께 저장 검증용 DataStore */
    private class UpdateAllTestDataStore(context: Context) : BaseDataStore(context) {
        companion object {
            private val Context.updateAllTestDataStore by blackDataStore("updateAllTest")

            private val KEY_TEXT = stringPreferencesKey("text")
            private val KEY_NUMBER = longPreferencesKey("number")
        }

        override fun getDataStore(context: Context): DataStore<Preferences> = context.updateAllTestDataStore

        suspend fun updateBoth(text: String?, number: Long): Result<Unit> =
            updateAll(PreferenceUpdate(KEY_TEXT, text), PreferenceUpdate(KEY_NUMBER, number))

        suspend fun getText(): String = get(KEY_TEXT, "")

        suspend fun getNumber(): Long = get(KEY_NUMBER, 0L)
    }

    /**
     * 여러 키를 한 번에 저장하고 null 값의 키는 지우는지 검증
     *
     * Given: 저장값 없음
     * When: ("a", 1) 저장 후 (null, 2) 저장
     * Then: 첫 저장 뒤 "a"와 1, 둘째 저장 뒤 text는 지워져 기본값 ""이고 number는 2
     */
    @Test
    fun test_06_updateAllSavesTogetherAndRemovesNull() = runTest {
        /** Given **/
        val dataStore = UpdateAllTestDataStore(context)

        /** When **/
        val first = dataStore.updateBoth("a", 1L)
        val firstText = dataStore.getText()
        val firstNumber = dataStore.getNumber()
        val second = dataStore.updateBoth(null, 2L)

        /** Then **/
        assertTrue(first.isSuccess)
        assertEquals("a", firstText)
        assertEquals(1L, firstNumber)
        assertTrue(second.isSuccess)
        assertEquals("", dataStore.getText())
        assertEquals(2L, dataStore.getNumber())
    }

    /**
     * 여러 키 저장의 실패가 실패 결과로, 취소가 재던짐으로 처리되는지 검증
     *
     * Given: 저장 시 IOException, CancellationException을 던지는 DataStore
     * When: updateAll 호출
     * Then: 실패 결과(IOException), 취소는 전파
     */
    @Test
    fun test_07_updateAllFailureAndCancellation() = runTest {
        /** Given **/
        class FailingAllDataStore(context: Context, error: Throwable) : BaseDataStore(context) {
            private val dataStore: DataStore<Preferences> = mockk {
                coEvery { updateData(any()) } throws error
            }

            override fun getDataStore(context: Context): DataStore<Preferences> = dataStore

            suspend fun updateBoth(): Result<Unit> = updateAll(
                PreferenceUpdate(stringPreferencesKey("text"), "a"),
                PreferenceUpdate(longPreferencesKey("number"), 1L),
            )
        }

        /** When **/
        val failure = FailingAllDataStore(context, IOException("disk full")).updateBoth()
        val thrown = runCatching { FailingAllDataStore(context, CancellationException("cancelled")).updateBoth() }
            .exceptionOrNull()

        /** Then **/
        assertTrue(failure.exceptionOrNull() is IOException)
        assertTrue(thrown is CancellationException)
    }
}
