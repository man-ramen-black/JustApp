package com.black.app.ui.common.base

import androidx.databinding.ViewDataBinding
import com.black.core.component.BaseFragment

abstract class TitleFragment<T : ViewDataBinding> : BaseFragment<T>(){
    abstract val title: String
}