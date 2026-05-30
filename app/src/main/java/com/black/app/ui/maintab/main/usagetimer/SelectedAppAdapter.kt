package com.black.app.ui.maintab.main.usagetimer

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.app.R
import com.black.app.databinding.ItemSelectedAppBinding
import com.black.app.ui.common.selectapp.SelectAppItem
import com.black.core.view.BaseListAdapter
import com.black.core.view.BaseViewHolder

/**
 * UsageTimerFragment에서 저장된 선택 앱을 아이콘·라벨로 표시하는 어댑터
 */
class SelectedAppAdapter
    : BaseListAdapter<SelectAppItem>({ old, new -> old.packageName == new.packageName }) {

    private class ViewHolder(binding: ItemSelectedAppBinding)
        : BaseViewHolder<ItemSelectedAppBinding, SelectAppItem>(binding) {
        override fun bind(item: SelectAppItem) {
            binding.item = item
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, SelectAppItem> {
        return ViewHolder(inflate(parent, R.layout.item_selected_app))
    }
}
