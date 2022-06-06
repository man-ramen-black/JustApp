package com.black.code.ui.example.service

import androidx.fragment.app.viewModels
import com.black.code.R
import com.black.code.databinding.FragmentServiceBinding
import com.black.code.base.component.ExampleFragment

/**
 * ForegroundService
 * https://developer.android.com/guide/components/foreground-services
 */
class ServiceFragment : ExampleFragment<FragmentServiceBinding>() {
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