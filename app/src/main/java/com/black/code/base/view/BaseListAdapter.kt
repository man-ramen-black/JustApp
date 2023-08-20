package com.black.code.base.view

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by jinhyuk.lee on 2022/05/09
 **/
/*
#RecyclerView #ListAdapter
https://youngest-programming.tistory.com/474
 */
abstract class BaseListAdapter<DATA>(itemCallback: DiffUtil.ItemCallback<DATA> = SimpleItemCallback())
    : ListAdapter<DATA, BaseListAdapter.BaseViewHolder<ViewDataBinding, DATA>>(itemCallback) {

    constructor(areItemTheSame:(old:DATA, new:DATA) -> Boolean) : this(SimpleItemCallback(areItemTheSame))

    override fun onBindViewHolder(holder: BaseViewHolder<ViewDataBinding, DATA>, position: Int) {
        holder.bind(getItem(position))
    }

    protected fun <BINDING : ViewDataBinding> inflateForViewHolder(parent: ViewGroup, layoutId: Int) : BINDING {
        return DataBindingUtil.inflate<BINDING>(LayoutInflater.from(parent.context), layoutId, parent, false)
            .apply {
                lifecycleOwner = parent.findViewTreeLifecycleOwner()
            }
    }

    /*
    submitList는 동일한 list 객체가 전달되는 경우 업데이트 하지 않기 때문에
    toMutalbeList로 MutableList로 객체를 새로 생성하여 처리
    #submitList
    https://stackoverflow.com/a/58080105
     */
    override fun submitList(list: List<DATA>?) {
        super.submitList(list?.toMutableList())
    }

    abstract class BaseViewHolder<out BINDING : ViewDataBinding, DATA>(protected val binding: BINDING)
        : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(item : DATA)
    }
}

/*
#DiffUtil
https://velog.io/@haero_kim/Android-DiffUtil-%EC%82%AC%EC%9A%A9%EB%B2%95-%EC%95%8C%EC%95%84%EB%B3%B4%EA%B8%B0
https://www.charlezz.com/?p=1363
 */
open class SimpleItemCallback<T>(private val areItemTheSame:(old:T, new:T) -> Boolean = {old, new -> old == new})
    : DiffUtil.ItemCallback<T>() {
    /**
     * 이전 객체와 새로운 객체가 같은 객체인지 비교 : 리스트의 구조 변경(추가, 삭제, 이동)이 필요한지 비교
     * 객체를 사용하는 Adapter에서 Override 필수
     */
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
        return areItemTheSame(oldItem, newItem)
    }

    /**
     * 두 객체가 같은 데이터인지를 비교 : 특정 항목을 업데이트해야 하는지 비교
     * areItemsTheSame이 true인 경우에 호출됨
     * 같은 객체가 아닌 경우 굳이 데이터가 같은지 비교할 필요가 없기 때문
     */
    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem == newItem
    }
}