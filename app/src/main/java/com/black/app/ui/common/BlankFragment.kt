package com.black.app.ui.common

import com.black.app.R
import com.black.app.databinding.ViewEmptyBinding
import com.black.core.component.BaseFragment

class BlankFragment: BaseFragment<ViewEmptyBinding>() {
    override val layoutResId: Int = R.layout.view_empty
    override fun onBindVariable(binding: ViewEmptyBinding) { }
}