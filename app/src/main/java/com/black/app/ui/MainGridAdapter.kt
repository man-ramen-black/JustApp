package com.black.app.ui

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.databinding.ViewDataBinding
import androidx.navigation.NavDirections
import com.black.app.R
import com.black.app.databinding.ItemMainBinding
import com.black.core.view.BaseListAdapter

data class MainItem(@StringRes val nameResId: Int, @DrawableRes val iconResId: Int, val navDirection: NavDirections)

class MainGridAdapter(private val viewModel: MainFragmentViewModel) : BaseListAdapter<MainItem>() {
    class ViewHolder(binding: ItemMainBinding, private val viewModel: MainFragmentViewModel) : BaseViewHolder<ItemMainBinding, MainItem>(binding) {
        override fun bind(item: MainItem) {
            binding.data = item
            binding.viewModel = viewModel
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, MainItem> {
        return ViewHolder(inflateForViewHolder(parent, R.layout.item_main), viewModel)
    }
}