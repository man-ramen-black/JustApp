package com.black.study.contents

import androidx.databinding.ViewDataBinding
import com.black.study.base.BaseFragment

abstract class ContentsFragment<T : ViewDataBinding> : BaseFragment<T>(){
    abstract val title: String
}