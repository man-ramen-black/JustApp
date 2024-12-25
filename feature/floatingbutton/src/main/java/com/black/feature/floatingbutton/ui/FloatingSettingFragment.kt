package com.black.feature.floatingbutton.ui

import android.content.Intent
import androidx.fragment.app.viewModels
import com.black.core.component.BaseFragment
import com.black.core.service.ForegroundService
import com.black.core.util.FragmentExtension.viewLifecycleScope
import com.black.core.viewmodel.EventCollector
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.databinding.FragmentFloatingSettingBinding
import com.black.feature.floatingbutton.service.FloatingForegroundService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FloatingSettingFragment: BaseFragment<FragmentFloatingSettingBinding>(), EventCollector {
    private val viewModel by viewModels<FloatingSettingViewModel>()

    override val layoutResId: Int = R.layout.fragment_floating_setting

    override fun onBindVariable(binding: FragmentFloatingSettingBinding) {
        binding.viewModel = viewModel
        viewModel.collectEvent(viewLifecycleScope, this)
    }

    override suspend fun onEventCollected(action: String, data: Any?) {
        when (action) {
            FloatingSettingViewModel.EVENT_START -> {
                requireContext().startService(Intent(requireContext(), FloatingForegroundService::class.java))
            }

            FloatingSettingViewModel.EVENT_STOP-> {
                ForegroundService.stop(requireContext(), FloatingForegroundService::class.java)
            }
        }
    }
}