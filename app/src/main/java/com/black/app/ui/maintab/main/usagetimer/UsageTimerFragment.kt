package com.black.app.ui.maintab.main.usagetimer

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.black.app.R
import com.black.app.databinding.FragmentUsageTimerBinding
import com.black.app.model.UsageTimerModel
import com.black.app.ui.common.selectapp.SelectAppDialogFragment
import com.black.app.ui.common.base.TitleFragment
import com.black.app.ui.maintab.main.usagetimer.view.UsageTimerView
import com.black.core.util.FragmentExtension.navigateSafety
import com.google.android.material.snackbar.Snackbar

class UsageTimerFragment : TitleFragment<FragmentUsageTimerBinding>(),
    com.black.core.viewmodel.EventObserver {
    override val layoutResId: Int = R.layout.fragment_usage_timer
    override val title: String = "UsageTimer"
    private val viewModel : UsageTimerFragmentViewModel by viewModels()

    override fun onBindVariable(binding: FragmentUsageTimerBinding) {
        binding.fragment = this
        binding.viewModel = viewModel.apply {
            setModel(UsageTimerModel(requireContext()))
            observeEvent(viewLifecycleOwner, this@UsageTimerFragment)
        }
        viewModel.init()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SelectAppDialogFragment.observeSelectedApp(this) {
            Snackbar.make(binding.root, it.joinToString(", "), Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            UsageTimerFragmentViewModel.EVENT_SHOW -> {
                showUsageTimerView()
            }
            UsageTimerFragmentViewModel.EVENT_TOAST -> {
                Toast.makeText(requireContext(), data?.toString() ?: return, Toast.LENGTH_SHORT)
                    .show()
            }
            UsageTimerFragmentViewModel.EVENT_DETACH_VIEW_IN_SERVICE -> {
                UsageTimerGlobal.detachView()
            }
            UsageTimerFragmentViewModel.EVENT_SHOW_ACCESSIBILITY -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            UsageTimerFragmentViewModel.EVENT_SHOW_SELECT_APP -> {
                findNavController().navigateSafety(UsageTimerFragmentDirections.actionSelectApp())
            }
        }
    }

    private fun showUsageTimerView() {
        UsageTimerView(requireContext())
            .attachView()
    }
}

