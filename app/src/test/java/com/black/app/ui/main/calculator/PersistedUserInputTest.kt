package com.black.app.ui.main.calculator

import com.black.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [PersistedUserInput]의 저장값 복원, 사용자 변경 우선, 저장 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PersistedUserInputTest : BaseTest() {

    /** 상태에 반영된 값(반영 전이면 null) */
    private var state: String? = null

    /** save로 넘어온 값 목록 */
    private val savedValues = mutableListOf<String>()

    /** 테스트 스케줄러에 묶인 코루틴 실행 범위([waitCoroutine]으로 진행) */
    private val scope by lazy { TestScope(testDispatcherRule.testDispatcher) }

    private fun createInput(
        load: suspend () -> String,
        saveResult: Result<Unit> = Result.success(Unit),
    ) = PersistedUserInput(
        launcher = { block -> scope.launch { block() } },
        load = load,
        save = { value -> savedValues.add(value); saveResult },
        saveLogName = "saveTest",
        updateState = { value -> state = value },
    )

    /**
     * 사용자가 바꾸지 않으면 저장값이 상태에 반영되는지 검증
     *
     * Given: 저장값 "saved"
     * When: restore 호출
     * Then: 대기 전에는 상태 null, 대기 후 "saved"
     */
    @Test
    fun test_01_restoreAppliesSavedValue() = runTest {
        /** Given **/
        val input = createInput(load = { "saved" })

        /** When **/
        input.restore()

        /** Then **/
        assertNull(state)
        waitCoroutine()
        assertEquals("saved", state)
    }

    /**
     * 저장값을 읽는 중에 사용자가 바꾸면 읽은 값을 버리는지 검증
     *
     * Given: 완료되지 않은 저장값 조회로 restore 시작
     * When: change("user") 호출 후 조회가 "saved"로 완료
     * Then: 상태 "user" 유지
     */
    @Test
    fun test_02_changeDuringRestoreWins() = runTest {
        /** Given **/
        val loading = CompletableDeferred<String>()
        val input = createInput(load = { loading.await() })
        input.restore()
        waitCoroutine()

        /** When **/
        input.change("user")
        loading.complete("saved")
        waitCoroutine()

        /** Then **/
        assertEquals("user", state)
    }

    /**
     * 사용자 변경이 코루틴 대기 없이 상태에 반영되고 저장되는지 검증
     *
     * Given: 복원이 끝난 입력
     * When: change("first")·change("second") 연속 호출
     * Then: 대기 전에 상태 "second", 대기 후 save가 "first"·"second" 순서로 호출
     */
    @Test
    fun test_03_changeUpdatesImmediatelyAndSaves() = runTest {
        /** Given **/
        val input = createInput(load = { "saved" })
        input.restore()
        waitCoroutine()

        /** When **/
        input.change("first")
        input.change("second")

        /** Then **/
        assertEquals("second", state)
        waitCoroutine()
        assertEquals(listOf("first", "second"), savedValues)
    }

    /**
     * 저장이 실패해도 상태가 유지되는지 검증
     *
     * Given: save가 IOException 실패 반환
     * When: change("user") 호출 후 대기
     * Then: 상태 "user", save 호출 기록 "user"
     */
    @Test
    fun test_04_saveFailureKeepsState() = runTest {
        /** Given **/
        val input = createInput(load = { "saved" }, saveResult = Result.failure(IOException()))

        /** When **/
        input.change("user")
        waitCoroutine()

        /** Then **/
        assertEquals("user", state)
        assertEquals(listOf("user"), savedValues)
    }
}
