package com.black.code.contents.sample

import com.black.code.R
import com.black.code.contents.ContentsFragment
import com.black.code.databinding.FragmentSampleBinding

class SampleFragment : ContentsFragment<FragmentSampleBinding>() {
    override val title: String = "Contents Sample"
    override val layoutResId: Int = R.layout.fragment_sample
}