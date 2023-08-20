package com.black.code.ui.example.usagetimer

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.black.code.R
import com.black.code.base.view.MovableOverlayView
import com.black.code.base.viewmodel.EventObserver
import com.black.code.databinding.FragmentUsageTimerBinding
import com.black.code.model.UsageTimerModel
import com.black.code.ui.common.selectapp.SelectAppDialogFragment
import com.black.code.ui.example.ExampleFragment
import com.black.code.ui.example.usagetimer.view.UsageTimerView
import com.black.code.util.FragmentExtension.navigateSafety
import com.google.android.material.snackbar.Snackbar

class UsageTimerFragment : ExampleFragment<FragmentUsageTimerBinding>(), EventObserver {
    override val layoutResId: Int = R.layout.fragment_usage_timer
    override val title: String = "UsageTimer"
    private val viewModel : UsageTimerFragmentViewModel by viewModels()

    override fun bindVariable(binding: FragmentUsageTimerBinding) {
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
            .apply {
                setOnMoveListener { view, action, x, y ->
                    when(action) {
                        MovableOverlayView.ACTION_MOVE_STARTED -> {
                            Toast.makeText(requireContext(), "Move start", Toast.LENGTH_SHORT).show()
                        }
                        MovableOverlayView.ACTION_MOVE_ENDED -> {
                            Toast.makeText(requireContext(), "Move end", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .attachView()
    }
}

