package com.black.app.ui.example

import androidx.databinding.ViewDataBinding
import com.black.app.base.component.BaseFragment

abstract class ExampleFragment<T : ViewDataBinding> : BaseFragment<T>(){
    abstract val title: String
}