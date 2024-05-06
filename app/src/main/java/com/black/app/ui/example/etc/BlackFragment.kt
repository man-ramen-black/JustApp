package com.black.app.ui.example.etc

import androidx.appcompat.app.AppCompatActivity
import com.black.app.R
import com.black.app.databinding.FragmentBlackBinding
import com.black.app.ui.example.ExampleFragment
import com.black.core.util.UiUtil

class BlackFragment : ExampleFragment<FragmentBlackBinding>() {
    override val title: String = ""
    override val layoutResId: Int = R.layout.fragment_black
    override fun bindVariable(binding: FragmentBlackBinding) { }

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