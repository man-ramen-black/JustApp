package com.black.code.ui.example

import androidx.databinding.ViewDataBinding
import com.black.code.base.component.BaseFragment

abstract class ExampleFragment<T : ViewDataBinding> : BaseFragment<T>(){
    abstract val title: String
}