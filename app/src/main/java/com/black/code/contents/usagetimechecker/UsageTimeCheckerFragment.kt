package com.black.code.contents.usagetimechecker

import android.os.Bundle
import android.view.View
import com.black.code.R
import com.black.code.contents.ContentsFragment
import com.black.code.databinding.FragmentUsageTimeCheckerBinding
import com.black.code.util.Log

class UsageTimeCheckerFragment : ContentsFragment<FragmentUsageTimeCheckerBinding>() {
    override val layoutResId: Int = R.layout.fragment_usage_time_checker
    override val title: String = "UsageTimeChecker"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.fragment = this
    }

    fun onClickShow() {
        val view = UsageTimeCheckerView(requireContext())
        Log.d("view created")
        view.attachView()
    }
}