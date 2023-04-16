package com.black.code.ui.example

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.databinding.ViewDataBinding
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import com.black.code.R
import com.black.code.base.view.BaseListAdapter
import com.black.code.databinding.ItemExampleBinding

class ExampleListAdapter(private val viewModel: ExampleViewModel) : BaseListAdapter<ExampleListAdapter.Item>() {
    data class Item(@StringRes val nameResId: Int, @IdRes val fragmentId: Int, @DrawableRes val iconResId: Int)

    class ViewHolder(binding: ItemExampleBinding, private val viewModel: ExampleViewModel) : BaseViewHolder<ItemExampleBinding, Item>(binding) {
        override fun bind(item: Item) {
            binding.data = item
            binding.viewModel = viewModel
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, Item> {
        return ViewHolder(inflateForViewHolder(parent, R.layout.item_example), viewModel)
    }
}