package com.black.core.viewmodel

import androidx.lifecycle.LiveData
import java.util.Collections

open class LiveList<T>(list: List<T> = listOf()) : LiveData<List<T>>() {
    private lateinit var temp : MutableList<T>

    init {
        value = list
    }

    override fun postValue(value: List<T>) {
        temp = Collections.synchronizedList(value.toMutableList())
        super.postValue(value)
    }

    override fun setValue(value: List<T>) {
        temp = Collections.synchronizedList(value.toMutableList())
        super.setValue(value)
    }

    override fun getValue(): List<T> {
        return super.getValue()!!
    }

    fun add(item: T) {
        temp.add(item)
        value = temp
    }

    fun add(position:Int, item: T) {
        temp.add(position, item)
        value = temp
    }

    fun addAll(items: List<T>) {
        temp.addAll(items)
        value = temp
    }

    fun remove(item: T) {
        temp.remove(item)
        value = temp
    }

    fun removeAt(position: Int) {
        temp.removeAt(position)
        value = temp
    }

    fun clear() {
        temp.clear()
        value = temp
    }

    fun notifyDataSetChanged() {
        value = temp.toMutableList()
    }

    fun find(predicate: (T) -> Boolean): T? = temp.find(predicate)

    fun filter(predicate: (T) -> Boolean): List<T> = temp.filter(predicate)

    fun <R> map(transform: (T) -> R): List<R> = temp.map(transform)
}

class MutableLiveList<T>(list: List<T> = listOf()) : LiveList<T>(list) {
    public override fun postValue(value: List<T>) {
        super.postValue(value)
    }

    public override fun setValue(value: List<T>) {
        super.setValue(value)
    }
}