package com.black.core.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
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

/** [BaseDataStore] 하위 DataStore의 손상 파일 복구와 저장 실패 전달 검증 */
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
}
