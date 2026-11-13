package com.black.core.util

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [RecyclerViewBindingAdapter.setItemList]가 어댑터를 바뀔 때만 연결하고 목록을 제출하는지 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RecyclerViewBindingAdapterTest : BaseTest() {

    private lateinit var recyclerView: RecyclerView

    @Before
    fun setupRecyclerView() {
        val context = RuntimeEnvironment.getApplication()
        recyclerView = RecyclerView(context).apply { layoutManager = LinearLayoutManager(context) }
    }

    /**
     * 목록만 바뀌면 이미 연결된 어댑터를 다시 연결하지 않는지 검증
     *
     * Given: 항목 1개로 어댑터를 연결한 RecyclerView
     * When: 같은 어댑터와 항목 2개로 다시 호출
     * Then: 어댑터 연결은 처음 한 번뿐이고 해제되지 않음
     */
    @Test
    fun test_01_sameAdapterIsNotAttachedAgain() = runTest {
        /** Given **/
        val adapter = TestAdapter()
        RecyclerViewBindingAdapter.setItemList(recyclerView, adapter, listOf("a"))

        /** When **/
        RecyclerViewBindingAdapter.setItemList(recyclerView, adapter, listOf("a", "b"))

        /** Then **/
        assertSame(adapter, recyclerView.adapter)
        assertEquals(1, adapter.attachCount)
        assertEquals(0, adapter.detachCount)
    }

    /**
     * 이미 연결된 어댑터에 목록을 제출하는지 검증
     *
     * Given: 목록 없이 어댑터만 연결한 RecyclerView
     * When: 같은 어댑터와 항목 2개로 호출
     * Then: 어댑터의 현재 목록이 항목 2개이고 어댑터 연결은 한 번뿐
     */
    @Test
    fun test_02_submitsListToAttachedAdapter() = runTest {
        /** Given **/
        val adapter = TestAdapter()
        RecyclerViewBindingAdapter.setItemList(recyclerView, adapter, null)

        /** When **/
        RecyclerViewBindingAdapter.setItemList(recyclerView, adapter, listOf("a", "b"))

        /** Then **/
        assertEquals(listOf("a", "b"), adapter.currentList)
        assertEquals(1, adapter.attachCount)
    }

    /**
     * 다른 어댑터가 오면 새 어댑터로 교체하고 목록을 제출하는지 검증
     *
     * Given: 이전 어댑터를 연결한 RecyclerView
     * When: 새 어댑터와 항목 1개로 호출
     * Then: 새 어댑터가 연결되어 항목 1개를 갖고, 이전 어댑터는 연결 해제
     */
    @Test
    fun test_03_differentAdapterReplacesPrevious() = runTest {
        /** Given **/
        val previous = TestAdapter()
        RecyclerViewBindingAdapter.setItemList(recyclerView, previous, listOf("a"))
        val next = TestAdapter()

        /** When **/
        RecyclerViewBindingAdapter.setItemList(recyclerView, next, listOf("b"))

        /** Then **/
        assertSame(next, recyclerView.adapter)
        assertEquals(listOf("b"), next.currentList)
        assertEquals(1, previous.detachCount)
    }

    /** RecyclerView 연결·해제 횟수를 세는 문자열 어댑터 */
    private class TestAdapter : ListAdapter<String, RecyclerView.ViewHolder>(StringItemCallback) {
        var attachCount = 0
            private set
        var detachCount = 0
            private set

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            attachCount++
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            detachCount++
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(View(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
    }

    private object StringItemCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
