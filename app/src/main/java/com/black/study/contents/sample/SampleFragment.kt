package com.black.study.contents.sample

import com.black.study.R
import com.black.study.contents.ContentsFragment
import com.black.study.databinding.FragmentSampleBinding

class SampleFragment : ContentsFragment<FragmentSampleBinding>() {
    override val title: String = "Contents Sample"
    override val layoutResId: Int = R.layout.fragment_sample
}