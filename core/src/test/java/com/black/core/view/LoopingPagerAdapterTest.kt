package com.black.core.view

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [LoopingPagerAdapter]의 목록 반영 알림 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LoopingPagerAdapterTest : BaseTest() {

    /**
     * 목록을 반영하면 보이는 홀더가 다시 바인딩되도록 전체 갱신을 알리고 완료 콜백을 부르는지 검증
     *
     * Given: 데이터 관찰자를 등록한 어댑터
     * When: 완료 콜백과 함께 목록 제출
     * Then: onChanged 1회, 완료 콜백 1회, 개수 Int.MAX_VALUE
     */
    @Test
    fun test_01_submitListNotifiesDataSetChanged() = runTest {
        /** Given **/
        val adapter = TestAdapter()
        var changedCount = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                changedCount++
            }
        })
        var committedCount = 0

        /** When **/
        adapter.submitList(listOf("a", "b")) { committedCount++ }

        /** Then **/
        assertEquals(1, changedCount)
        assertEquals(1, committedCount)
        assertEquals(Int.MAX_VALUE, adapter.itemCount)
    }

    private class TestAdapter : LoopingPagerAdapter<String>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<ViewDataBinding, String> =
            throw UnsupportedOperationException("바인딩 검증에 쓰지 않음")
    }
}
