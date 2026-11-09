package com.black.core.view

import android.view.View
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [BaseListAdapter]가 받은 위치로 즉시 바인딩하는지 검증 */
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

    private class TestAdapter : BaseListAdapter<String>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<ViewDataBinding, String> =
            TestViewHolder()
    }

    private class TestViewHolder : BaseViewHolder<ViewDataBinding, String>(
        TestBinding(View(RuntimeEnvironment.getApplication()))
    ) {
        val boundItems = mutableListOf<String>()

        override fun bind(item: String) {
            boundItems += item
        }
    }

    /** ViewDataBinding은 목업이 되지 않아 빈 바인딩으로 대체 */
    private class TestBinding(root: View) : ViewDataBinding(null, root, 0) {
        override fun onFieldChange(localFieldId: Int, `object`: Any?, fieldId: Int): Boolean = false
        override fun setVariable(variableId: Int, value: Any?): Boolean = false
        override fun executeBindings() {}
        override fun invalidateAll() {}
        override fun hasPendingBindings(): Boolean = false
    }
}
