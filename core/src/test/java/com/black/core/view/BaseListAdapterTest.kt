package com.black.core.view

import android.view.View
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [BaseListAdapter]가 받은 위치로 즉시 바인딩하고 바인딩 식을 바로 실행하는지 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BaseListAdapterTest : BaseTest() {

    /**
     * 뷰가 창에 붙기 전에도 받은 위치의 항목으로 바로 바인딩하는지 검증
     *
     * Given: 항목 3개를 제출한 어댑터, 창에 붙지 않은 ViewHolder
     * When: 위치 1로 onBindViewHolder 호출
     * Then: 부착을 기다리지 않고 위치 1의 항목 하나만 바인딩
     */
    @Test
    fun test_01_bindsImmediatelyWithGivenPosition() = runTest {
        /** Given **/
        val adapter = TestAdapter().apply { submitList(listOf("a", "b", "c")) }
        val holder = TestViewHolder()

        /** When **/
        adapter.onBindViewHolder(holder, 1)

        /** Then **/
        assertEquals(listOf("b"), holder.boundItems)
    }

    /**
     * 바인딩 변수를 바꾼 뒤 바인딩 식을 다음 프레임으로 미루지 않고 바로 실행하는지 검증
     *
     * Given: 항목 1개를 제출한 어댑터, bind에서 바인딩 변수를 바꿔 실행 대기가 생기는 ViewHolder
     * When: 위치 0으로 onBindViewHolder 호출
     * Then: 바인딩 식이 한 번 실행되어 실행 대기가 남지 않음
     */
    @Test
    fun test_02_executesPendingBindingsOnBind() = runTest {
        /** Given **/
        val adapter = TestAdapter().apply { submitList(listOf("a")) }
        val holder = TestViewHolder()

        /** When **/
        adapter.onBindViewHolder(holder, 0)

        /** Then **/
        assertEquals(1, holder.testBinding.executeCount)
        assertFalse(holder.testBinding.hasPendingBindings())
    }

    private class TestAdapter : BaseListAdapter<String>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<ViewDataBinding, String> =
            TestViewHolder()
    }

    private class TestViewHolder(
        val testBinding: TestBinding = TestBinding(View(RuntimeEnvironment.getApplication()))
    ) : BaseViewHolder<ViewDataBinding, String>(testBinding) {
        val boundItems = mutableListOf<String>()

        override fun bind(item: String) {
            boundItems += item
            // 생성된 바인딩의 변수 setter처럼 실행 대기 표시
            testBinding.isPending = true
        }
    }

    /** ViewDataBinding은 목업이 되지 않아 실행 대기와 실행 횟수만 기록하는 바인딩으로 대체 */
    private class TestBinding(root: View) : ViewDataBinding(null, root, 0) {
        var isPending = false
        var executeCount = 0
            private set

        override fun onFieldChange(localFieldId: Int, `object`: Any?, fieldId: Int): Boolean = false
        override fun setVariable(variableId: Int, value: Any?): Boolean = false
        override fun executeBindings() {
            executeCount++
            isPending = false
        }
        override fun invalidateAll() {}
        override fun hasPendingBindings(): Boolean = isPending
    }
}
