package com.black.core.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.black.core.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * set 한 값을 Observer마다 한 번만 전달하는 LiveData Wrapper
 * Created by jinhyuk.lee on 2022/06/14
 **/
class SingleLiveData<T> : MutableLiveData<T> {
    companion object {
        private const val DEBUG = false
    }

    // 데이터를 이미 통지했는지를 관리하는 Map
    // <Observer.hashCode, isNotified>
    private val isNotifiedMap = ConcurrentHashMap<Int, Boolean>()

    // LifecycleOwner과 연결된 Observe의 hashCode를 관리하는 Map
    // <LifecycleOwner.hashCode, List<Observer.hashCode>>
    private val ownerObserveHashCodeMap = ConcurrentHashMap<Int, MutableList<Int>>()

    // observeForever를 통해 등록된 Observer를 관리하는 Map
    // <Observer.hashCode, Observer>
    private val foreverObserverMap = ConcurrentHashMap<Int, Observer<T>>()

    constructor() : super()

    constructor(value: T) : super(value)

    override fun postValue(value: T) {
        isNotifiedMap.forEach {
            if (DEBUG) {
                Log.e("$value : ${it.key}")
            }
            isNotifiedMap[it.key] = false
        }
        super.postValue(value)
    }

    @MainThread
    override fun setValue(value: T?) {
        isNotifiedMap.forEach {
            if (DEBUG) {
                Log.e("$value : ${it.key}")
            }
            isNotifiedMap[it.key] = false
        }
        super.setValue(value)
    }

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        val key = observer.hashCode()
        if (DEBUG) {
            Log.e(key)
        }

        // observe 이전에 setValue 했던 데이터를 notify 하지 않도록 true로 설정
        isNotifiedMap[key] = true

        // removeObservers 처리를 위해 owner와 연결된 observer의 hashCode를 저장
        ownerObserveHashCodeMap.getOrPut(owner.hashCode()) { mutableListOf() }
            .add(key)

        super.observe(owner, object: Observer<T> {
            override fun onChanged(value: T) {
                if (DEBUG) {
                    Log.e("$value : $key, ${isNotifiedMap[key]}")
                }

                // Notify 하지 않은 경우에만 값 전달
                if (isNotifiedMap[key] == false) {
                    observer.onChanged(value)
                    isNotifiedMap[key] = true
                }
            }

            override fun hashCode(): Int {
                return observer.hashCode()
            }
        })
    }

    override fun removeObservers(owner: LifecycleOwner) {
        super.removeObservers(owner)

        // isNotifiedMap이 무한히 쌓이지 않도록 remove 시 해당되는 데이터를 삭제
        isNotifiedMap -= ownerObserveHashCodeMap[owner.hashCode()]?.toSet()
            ?: run {
                if (DEBUG) {
                    Log.e("ownerObserveHashCodeTable[${owner.hashCode()}] is null")
                }
                return
            }
    }

    override fun observeForever(observer: Observer<in T>) {
        val key = observer.hashCode()
        if (DEBUG) {
            Log.e(key)
        }

        // observe 이전에 setValue 했던 데이터를 notify 하지 않도록 true로 설정
        isNotifiedMap[key] = true

        super.observeForever(object: Observer<T> {
            override fun onChanged(value: T) {
                // 값이 설정되었고, 최초 observe가 아닐 때만 전달
                if (isNotifiedMap[key] == false) {
                    observer.onChanged(value)
                    isNotifiedMap[key] = true
                }
            }

            override fun hashCode(): Int {
                return observer.hashCode()
            }
        }.also { foreverObserverMap[key] = it })
    }

    override fun removeObserver(observer: Observer<in T>) {
        val key = observer.hashCode()
        val realObserver = foreverObserverMap[key]
        if (DEBUG) {
            Log.e("$key : $realObserver")
        }

        super.removeObserver(realObserver ?: return)

        // isNotifiedMap이 무한히 쌓이지 않도록 remove 시 해당되는 데이터를 삭제
        isNotifiedMap.remove(key)
        foreverObserverMap.remove(key)
    }
}