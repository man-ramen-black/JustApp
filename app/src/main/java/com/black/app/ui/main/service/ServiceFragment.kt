package com.black.app.ui.main.service

import androidx.fragment.app.viewModels
import com.black.app.R
import com.black.app.databinding.FragmentServiceBinding
import com.black.app.ui.common.base.TitleFragment

/**
 * ForegroundService
 * https://developer.android.com/guide/components/foreground-services
 */
class ServiceFragment : TitleFragment<FragmentServiceBinding>() {
    override val layoutResId: Int = R.layout.fragment_service
    override val title: String = "Service"

    private val viewModel : ServiceViewModel by viewModels()

    override fun bindVariable(binding: FragmentServiceBinding) {
        binding.fragment = this
        binding.viewModel = viewModel.apply {
            setModel(ServiceModel(requireContext()))
        }
        viewModel.init()
    }
}