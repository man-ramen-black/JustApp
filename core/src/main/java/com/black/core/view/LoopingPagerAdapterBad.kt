package com.black.core.view

import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.widget.ViewPager2
import com.black.core.util.Log

/**
 * Created by jinhyuk.lee on 2022/05/09
 **/
/*
처음, 마지막 아이템에서 자동 이동 시에 깜빡이는 이슈 발생
https://medium.com/@ronybrosh/endless-viewpager2-b546b0b1e0a9
 */
abstract class LoopingPagerAdapterBad<DATA>(itemCallback: DiffUtil.ItemCallback<DATA> = SimpleItemCallback())
    : BaseListAdapter<DATA>(itemCallback) {
    companion object {
        const val INITIAL_POSITION = 1
    }

    /**
     * 첫번째에 마지막 데이터
     * 마지막에 첫번째 데이터를 추가
     * {C, A, B, C, A}
     */
    override fun submitList(list: List<DATA>?) {
        val mutableList = list?.toMutableList()?.apply {
            if (size >= 2) {
                val first = first()
                val last = last()
                add(0, last)
                add(first)
            }
        }
        super.submitList(mutableList)
    }
}

fun <DATA> ViewPager2.setLoopingAdapter(adapter: LoopingPagerAdapterBad<DATA>) {
    Log.d("")
    this.adapter = adapter
    /*
    첫번째 페이지로 이동하면 마지막 -1로 페이지 이동
    마지막 페이지로 이동하면 첫번째 +1로 페이지 이동
    list : {C, A, B, C, A}
    => 무한 스크롤
     */
    this.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager2.SCROLL_STATE_IDLE || state == ViewPager2.SCROLL_STATE_DRAGGING) {
                if (currentItem == 0) {
                    setCurrentItem(adapter.itemCount - 2, false)
                } else if (currentItem == adapter.itemCount -1) {
                    setCurrentItem(1, false)
                }
            }
        }
    })
}