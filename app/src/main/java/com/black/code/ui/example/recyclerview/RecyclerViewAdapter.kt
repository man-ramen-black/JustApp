package com.black.code.ui.example.recyclerview

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.black.code.R
import com.black.code.databinding.ItemRecyclerviewBinding

// https://www.charlezz.com/?p=1363
class BaseItemCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        // 이전 객체와 새로운 객체가 같은 객체인지 비교하기 위해 고유 식별자를 비교
        return oldItem === newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem::class != newItem::class) {
            return false
        }

        // 두 객체가 같은 데이터인지를 비교
        return oldItem == newItem
    }
}

// https://youngest-programming.tistory.com/474
class RecyclerViewAdapter(itemCallback: DiffUtil.ItemCallback<Any> = BaseItemCallback())
    : ListAdapter<Any, RecyclerViewAdapter.BaseViewHolder>(itemCallback) {

    abstract class BaseViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(item : Any)
    }

    class ViewHolder(private val binding: ItemRecyclerviewBinding) : BaseViewHolder(binding) {
        override fun bind(item : Any) {
            if (item !is String) {
                return
            }
            binding.data = item

            // 동일 바인더에서의 포지션
            // https://developer.android.com/reference/androidx/recyclerview/widget/RecyclerView.ViewHolder#getBindingAdapterPosition()
            bindingAdapterPosition

            // 전체 항목에서의 포지션
            // https://developer.android.com/reference/androidx/recyclerview/widget/RecyclerView.ViewHolder#getAbsoluteAdapterPosition()
            absoluteAdapterPosition
        }
    }

    override fun getItemViewType(position: Int): Int {
        return super.getItemViewType(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val binding : ItemRecyclerviewBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.item_recyclerview,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}