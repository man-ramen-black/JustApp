package com.black.code.contents.sample

import android.content.Intent
import com.black.code.R
import com.black.code.contents.ContentsFragment
import com.black.code.databinding.FragmentEtcBinding

class ETCFragment : ContentsFragment<FragmentEtcBinding>() {
    override val title: String = "ETC"
    override val layoutResId: Int = R.layout.fragment_etc

    override fun bindVariable(binding: FragmentEtcBinding) {
        binding.fragment = this
    }

    fun onClickShowEtcActivity() {
        startActivity(Intent(requireActivity(), ETCActivity::class.java))
    }
}