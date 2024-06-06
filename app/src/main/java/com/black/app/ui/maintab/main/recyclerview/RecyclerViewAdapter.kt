package com.black.app.ui.maintab.main.recyclerview

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.app.R
import com.black.app.databinding.ItemRecyclerviewDialogBinding
import com.black.app.databinding.ItemRecyclerviewToastBinding
import com.black.core.view.BaseViewHolder

/*
android #recyclerview multiple data type
https://class-programming.tistory.com/139

#Viewmodels for #RecyclerView #items
https://stackoverflow.com/questions/61364874/view-models-for-recyclerview-items
 */
class RecyclerViewAdapter(private val viewModel: RecyclerViewViewModel) : com.black.core.view.BaseListAdapter<RecyclerViewData>() {
    companion object {
        private const val VIEW_TYPE_TOAST = 1
        private const val VIEW_TYPE_DIALOG = 2
    }

    class ToastViewHolder(binding: ItemRecyclerviewToastBinding, private val viewModel: RecyclerViewViewModel)
        : BaseViewHolder<ItemRecyclerviewToastBinding, RecyclerViewData>(binding) {

        override fun bind(item : RecyclerViewData) {
            binding.viewHolder = this
            binding.data = item as RecyclerViewData.Toast
            binding.viewModel = viewModel
        }
    }

    class DialogViewHolder(binding: ItemRecyclerviewDialogBinding, private val viewModel: RecyclerViewViewModel)
        : BaseViewHolder<ItemRecyclerviewDialogBinding, RecyclerViewData>(binding) {

        override fun bind(item : RecyclerViewData) {
            binding.viewHolder = this
            binding.data = item as RecyclerViewData.Dialog
            binding.viewModel = viewModel
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RecyclerViewData.Toast -> VIEW_TYPE_TOAST
            is RecyclerViewData.Dialog -> VIEW_TYPE_DIALOG
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, RecyclerViewData> {
        return when(viewType) {
            VIEW_TYPE_TOAST -> {
                ToastViewHolder(inflateForViewHolder(parent, R.layout.item_recyclerview_toast), viewModel)
            }

            VIEW_TYPE_DIALOG -> {
                DialogViewHolder(inflateForViewHolder(parent, R.layout.item_recyclerview_dialog), viewModel)
            }

            else -> {
                throw IllegalArgumentException("Invalid viewType")
            }
        }
    }
}
/*
View마다 ViewModel은 1개이다
RecyclerView 안의 아이템은 하나의 View이니까 ViewModel을 따로 가져가는게 맞지 않을까?

=> https://stackoverflow.com/questions/61364874/view-models-for-recyclerview-items
AAC ViewModel은 activity, fragment 별로 ViewModel을 재활용하기 때문에,
각 item 마다 ViewModel을 만들어주더라도 결국 activity 내에 있기 때문에 각자 만들어지지 않고 재활용되고,
결과적으로 비정상적으로 동작하게됨
 */