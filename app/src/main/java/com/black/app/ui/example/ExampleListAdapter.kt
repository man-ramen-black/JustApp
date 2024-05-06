package com.black.app.ui.example

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.databinding.ViewDataBinding
import androidx.navigation.NavDirections
import com.black.app.R
import com.black.app.base.view.BaseListAdapter
import com.black.app.databinding.ItemExampleBinding

class ExampleListAdapter(private val viewModel: ExampleViewModel) : BaseListAdapter<ExampleListAdapter.Item>() {
    data class Item(@StringRes val nameResId: Int, @DrawableRes val iconResId: Int, val navDirection: NavDirections)

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