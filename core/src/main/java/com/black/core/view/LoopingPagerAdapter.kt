package com.black.core.view

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

    final override fun getItemCount(): Int {
        return if (currentList.isEmpty()) {
            0
        } else {
            Int.MAX_VALUE
        }
    }
}