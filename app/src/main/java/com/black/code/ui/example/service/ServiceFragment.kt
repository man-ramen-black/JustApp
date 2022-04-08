package com.black.code.ui.example.service

import com.black.code.R
import com.black.code.databinding.FragmentServiceBinding
import com.black.code.service.ForegroundService

/**
 * ForegroundService
 * https://developer.android.com/guide/components/foreground-services
 */
class ServiceFragment : com.black.code.ui.example.ExampleFragment<FragmentServiceBinding>() {
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