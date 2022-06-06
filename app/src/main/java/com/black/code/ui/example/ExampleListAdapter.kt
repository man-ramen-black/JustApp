package com.black.code.ui.example

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import com.black.code.R
import com.black.code.base.view.BaseListAdapter
import com.black.code.databinding.ItemExampleBinding

class ExampleListAdapter(private val viewModel: ExampleViewModel) : BaseListAdapter<NavDestination>() {

    class ViewHolder(binding: ItemExampleBinding, private val viewModel: ExampleViewModel) : BaseViewHolder<ItemExampleBinding, NavDestination>(binding) {
        override fun bind(item: NavDestination) {
            binding.data = item
            binding.viewModel = viewModel
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, NavDestination> {
        return ViewHolder(inflateForViewHolder(parent, R.layout.item_example), viewModel)
    }
}