package com.black.code.contents.sample

import android.os.Bundle
import android.view.View
import com.black.code.R
import com.black.code.contents.ContentsFragment
import com.black.code.databinding.FragmentEtcBinding

class ETCFragment : ContentsFragment<FragmentEtcBinding>() {
    override val title: String = "ETC"
    override val layoutResId: Int = R.layout.fragment_etc

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.fragment = this
    }
}