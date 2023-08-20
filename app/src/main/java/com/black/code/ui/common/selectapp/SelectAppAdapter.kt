package com.black.code.ui.common.selectapp

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.MutableLiveData
import com.black.code.R
import com.black.code.base.view.BaseListAdapter
import com.black.code.databinding.ItemSelectAppBinding

data class SelectAppItem(val packageName: String, val appLabel: CharSequence, val appIcon: Drawable) {
    val isChecked = MutableLiveData(false)
}

class SelectAppAdapter(private val viewModel: SelectAppViewModel)
    : BaseListAdapter<SelectAppItem>({old, new -> old.packageName == new.packageName})
{
    private class ViewHolder(binding: ItemSelectAppBinding, private val viewModel: SelectAppViewModel) : BaseViewHolder<ItemSelectAppBinding, SelectAppItem>(binding) {
        override fun bind(item: SelectAppItem) {
            binding.item = item
            binding.viewModel = viewModel
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, SelectAppItem> {
        return ViewHolder(inflateForViewHolder(parent, R.layout.item_select_app), viewModel)
    }
}