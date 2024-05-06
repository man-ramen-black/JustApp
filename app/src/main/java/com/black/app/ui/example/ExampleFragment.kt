package com.black.app.ui.example

import androidx.databinding.ViewDataBinding
import com.black.core.component.BaseFragment

abstract class ExampleFragment<T : ViewDataBinding> : com.black.core.component.BaseFragment<T>(){
    abstract val title: String
}