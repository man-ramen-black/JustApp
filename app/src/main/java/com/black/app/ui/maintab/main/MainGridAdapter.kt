package com.black.app.ui.maintab.main

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.databinding.ViewDataBinding
import com.black.app.R
import com.black.app.databinding.ItemMainBinding
import com.black.core.view.BaseListAdapter
import com.black.core.view.BaseViewHolder

data class MainItem(val name: String, @DrawableRes val iconResId: Int, val onClick: () -> Unit)

class MainGridAdapter : BaseListAdapter<MainItem>() {
    class ViewHolder(binding: ItemMainBinding) : BaseViewHolder<ItemMainBinding, MainItem>(binding) {
        override fun bind(item: MainItem) {
            binding.data = item
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, MainItem> {
        return ViewHolder(inflateForViewHolder(parent, R.layout.item_main))
    }
}