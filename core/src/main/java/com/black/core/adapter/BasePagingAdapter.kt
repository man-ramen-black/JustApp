package com.black.core.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.black.core.view.BaseViewHolder
import com.black.core.view.SimpleItemCallback

/*
#RecyclerView #ListAdapter
https://youngest-programming.tistory.com/474
 */
abstract class BasePagingAdapter<DATA : Any>(itemCallback: DiffUtil.ItemCallback<DATA> = SimpleItemCallback())
    : PagingDataAdapter<DATA, BaseViewHolder<ViewDataBinding, DATA>>(itemCallback) {

    constructor(areItemsTheSame : (oldItem: DATA, newItem: DATA) -> Boolean)
            : this(SimpleItemCallback<DATA>(areItemsTheSame))

    protected fun <BINDING : ViewDataBinding> inflateForViewHolder(parent: ViewGroup, layoutId: Int) : BINDING {
        return DataBindingUtil.inflate<BINDING>(LayoutInflater.from(parent.context), layoutId, parent, false)
            .apply {
                lifecycleOwner = parent.findViewTreeLifecycleOwner()
            }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<ViewDataBinding, DATA>,
        position: Int
    ) {
        holder.bind(getItem(position) ?: return)
    }
}