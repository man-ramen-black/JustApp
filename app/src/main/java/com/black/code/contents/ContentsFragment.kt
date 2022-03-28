package com.black.code.contents

import androidx.databinding.ViewDataBinding
import com.black.code.base.component.BaseFragment

abstract class ContentsFragment<T : ViewDataBinding> : BaseFragment<T>(){
    abstract val title: String
}