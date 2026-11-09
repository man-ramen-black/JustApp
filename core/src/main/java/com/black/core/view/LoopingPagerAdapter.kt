package com.black.core.view

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

/**
 * #Looping #Endless #무한스크롤 #infinite loop
 * Created by jinhyuk.lee on 2022/05/09
 **/
abstract class LoopingPagerAdapter<DATA: Any>(itemCallback: DiffUtil.ItemCallback<DATA> = SimpleItemCallback())
    : BaseListAdapter<DATA>(itemCallback) {
    companion object {
        const val INITIAL_POSITION : Int = Int.MAX_VALUE / 2
    }

    final override fun getItem(position: Int): DATA {
        return if (position == 0) {
            super.getItem(0)
        } else {
            return super.getItem(position % currentList.size)
        }
    }

    final override fun getItemId(position: Int): Long {
        return if (position == 0) {
            super.getItemId(0)
        } else {
            super.getItemId(position % currentList.size)
        }
    }

    /**
     * 개수가 [Int.MAX_VALUE]라 diff가 알리는 실제 위치(0부터 목록 크기까지)로는 보이는 홀더가 다시 바인딩되지 않으므로
     * 목록 반영 뒤 전체 갱신
     */
    @SuppressLint("NotifyDataSetChanged")
    final override fun submitList(list: List<DATA>?, commitCallback: Runnable?) {
        super.submitList(list?.toList()) {
            notifyDataSetChanged()
            commitCallback?.run()
        }
    }

    final override fun submitList(list: List<DATA>?) {
        submitList(list, null)
    }

    final override fun getItemCount(): Int {
        return if (currentList.isEmpty()) {
            0
        } else {
            Int.MAX_VALUE
        }
    }
}