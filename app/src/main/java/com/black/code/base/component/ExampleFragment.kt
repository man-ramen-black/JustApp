package com.black.code.base.component

import androidx.databinding.ViewDataBinding

abstract class ExampleFragment<T : ViewDataBinding> : BaseFragment<T>(){
    abstract val title: String
}