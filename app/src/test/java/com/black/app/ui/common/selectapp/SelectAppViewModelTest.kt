package com.black.app.ui.common.selectapp

import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.SpannableStringBuilder
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [SelectAppViewModel]의 초기 선택 상태 반영 검증
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SelectAppViewModelTest : BaseTest() {

    private val viewModel = SelectAppViewModel()

    private fun item(packageName: String) = SelectAppItem(packageName, packageName, ColorDrawable())

    private fun editable(text: String): Editable = SpannableStringBuilder(text)

    /**
     * EVENT_APP_SELECTED 이벤트로 전달되는 선택 결과 수집
     */
    private fun observeSelectedPackages(): () -> List<String>? {
        var selected: List<String>? = null
        viewModel.observeEventForever { action, data ->
            if (action == SelectAppViewModel.EVENT_APP_SELECTED) {
                @Suppress("UNCHECKED_CAST")
                selected = data as List<String>
            }
        }
        return { selected }
    }

    /**
     * 초기 선택 packageName 목록에 포함된 항목만 체크되는지 검증
     *
     * Given: 앱 3개와 초기 선택 [com.a, com.c]
     * When: init 호출
     * Then: com.a·com.c는 체크, com.b는 미체크
     */
    @Test
    fun test_01_initMarksCheckedPackages() {
        /** Given **/
        val items = listOf(item("com.a"), item("com.b"), item("com.c"))

        /** When **/
        viewModel.init(items, listOf("com.a", "com.c"))
        waitCoroutine()

        /** Then **/
        assertTrue(items.first { it.packageName == "com.a" }.isChecked.value!!)
        assertFalse(items.first { it.packageName == "com.b" }.isChecked.value!!)
        assertTrue(items.first { it.packageName == "com.c" }.isChecked.value!!)
    }

    /**
     * 초기 선택이 비어있으면 전부 미체크 상태인지 검증
     *
     * Given: 앱 2개, 초기 선택 없음
     * When: init 호출
     * Then: 모두 미체크
     */
    @Test
    fun test_02_initWithNoCheckedLeavesAllUnchecked() {
        /** Given **/
        val items = listOf(item("com.a"), item("com.b"))

        /** When **/
        viewModel.init(items)
        waitCoroutine()

        /** Then **/
        assertFalse(items[0].isChecked.value!!)
        assertFalse(items[1].isChecked.value!!)
    }

    /**
     * 여러 항목을 각각 클릭하면 독립적으로 다중 선택되는지 검증
     *
     * Given: 앱 3개
     * When: com.a, com.b를 각각 클릭
     * Then: com.a·com.b는 체크, com.c는 미체크 (다중 선택 유지)
     */
    @Test
    fun test_03_onItemClickTogglesMultipleIndependently() {
        /** Given **/
        val appA = item("com.a")
        val appB = item("com.b")
        val appC = item("com.c")

        /** When **/
        viewModel.onItemClick(appA)
        viewModel.onItemClick(appB)

        /** Then **/
        assertTrue(appA.isChecked.value!!)
        assertTrue(appB.isChecked.value!!)
        assertFalse(appC.isChecked.value!!)
    }

    /**
     * 필터로 가려진 기존 선택이 완료 결과에 유지되는지 검증
     *
     * Given: 앱 2개 init, 필터 없이 com.alpha 선택
     * When: com.alpha가 가려지는 필터(beta) 적용 후 완료
     * Then: 완료 결과에 com.alpha가 유지됨
     */
    @Test
    fun test_04_completeKeepsSelectionHiddenByFilter() {
        /** Given **/
        val items = listOf(item("com.alpha"), item("com.beta"))
        viewModel.init(items)
        waitCoroutine()
        val selected = observeSelectedPackages()
        viewModel.onItemClick(items.first { it.packageName == "com.alpha" })

        /** When **/
        viewModel.onTextChangedFilter(editable("beta"))
        viewModel.onClickComplete()

        /** Then **/
        assertEquals(listOf("com.alpha"), selected())
    }

    /**
     * 서로 다른 필터에서 선택한 항목이 완료 결과에 모두 누적되는지 검증
     *
     * Given: 앱 3개 init
     * When: alpha 필터로 com.alpha 선택, beta 필터로 com.beta 선택 후 완료
     * Then: 완료 결과에 com.alpha·com.beta가 모두 포함됨
     */
    @Test
    fun test_05_completeAccumulatesSelectionAcrossFilters() {
        /** Given **/
        val items = listOf(item("com.alpha"), item("com.beta"), item("com.gamma"))
        viewModel.init(items)
        waitCoroutine()
        val selected = observeSelectedPackages()

        /** When **/
        viewModel.onTextChangedFilter(editable("alpha"))
        viewModel.onItemClick(items.first { it.packageName == "com.alpha" })
        viewModel.onTextChangedFilter(editable("beta"))
        viewModel.onItemClick(items.first { it.packageName == "com.beta" })
        viewModel.onClickComplete()

        /** Then **/
        assertEquals(setOf("com.alpha", "com.beta"), selected()?.toSet())
    }
}
