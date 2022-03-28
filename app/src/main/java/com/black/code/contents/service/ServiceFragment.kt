package com.black.code.contents.service

import com.black.code.R
import com.black.code.contents.ContentsFragment
import com.black.code.databinding.FragmentServiceBinding

/**
 * ForegroundService
 * https://developer.android.com/guide/components/foreground-services
 */
class ServiceFragment : ContentsFragment<FragmentServiceBinding>() {
    override val layoutResId: Int = R.layout.fragment_service
    override val title: String = "Service"

    override fun bindVariable(binding: FragmentServiceBinding) {
        binding.fragment = this
    }

    fun onClickStartForegroundService() {
        ForegroundService.start(requireContext().applicationContext)
    }

    fun onClickStopForegroundService() {
        ForegroundService.stop(requireContext().applicationContext)
    }
}