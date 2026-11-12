package com.black.app.ui.main.etc

import androidx.appcompat.app.AppCompatActivity
import com.black.app.R
import com.black.app.databinding.FragmentBlackBinding
import com.black.core.component.BaseFragment
import com.black.core.util.UiUtil

class BlackFragment : BaseFragment<FragmentBlackBinding>() {
    override val layoutResId: Int = R.layout.fragment_black
    override fun onBindVariable(binding: FragmentBlackBinding) { }

    override fun onResume() {
        super.onResume()
        UiUtil.setImmersiveMode(requireActivity().window, true)
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    }

    override fun onPause() {
        UiUtil.setImmersiveMode(requireActivity().window, false)
        (requireActivity() as AppCompatActivity).supportActionBar?.show()
        super.onPause()
    }
}