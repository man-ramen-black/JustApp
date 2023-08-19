package com.black.code.ui.example.etc

import androidx.appcompat.app.AppCompatActivity
import com.black.code.R
import com.black.code.ui.example.ExampleFragment
import com.black.code.databinding.FragmentBlackBinding
import com.black.code.util.Util

class BlackFragment : ExampleFragment<FragmentBlackBinding>() {
    override val title: String = ""
    override val layoutResId: Int = R.layout.fragment_black
    override fun bindVariable(binding: FragmentBlackBinding) { }

    override fun onResume() {
        super.onResume()
        Util.setImmersiveMode(requireActivity().window, true)
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    }

    override fun onPause() {
        Util.setImmersiveMode(requireActivity().window, false)
        (requireActivity() as AppCompatActivity).supportActionBar?.show()
        super.onPause()
    }
}