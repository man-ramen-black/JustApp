package com.black.study.contents.contentsSample

import com.black.study.R
import com.black.study.contents.ContentsFragment
import com.black.study.databinding.FragmentContentsSampleBinding

class ContentsSampleFragment : ContentsFragment<FragmentContentsSampleBinding>() {
    override val title: String = "Contents Smaple"
    override val layoutResId: Int = R.layout.fragment_contents_sample
}