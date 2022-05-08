package com.black.code.ui.example.usagetimechecker

import android.widget.Toast
import androidx.fragment.app.viewModels
import com.black.code.R
import com.black.code.base.view.MovableOverlayView
import com.black.code.base.viewmodel.EventObserver
import com.black.code.base.viewmodel.EventViewModel
import com.black.code.databinding.FragmentUsageTimeCheckerBinding

class UsageTimeCheckerFragment : com.black.code.ui.example.ExampleFragment<FragmentUsageTimeCheckerBinding>(), EventObserver {
    override val layoutResId: Int = R.layout.fragment_usage_time_checker
    override val title: String = "UsageTimeChecker"
    private val viewModel : UsageTimeCheckerViewModel by viewModels()

    override fun bindVariable(binding: FragmentUsageTimeCheckerBinding) {
        binding.fragment = this
        binding.viewModel = viewModel.apply {
            setModel(UsageTimerModel(requireContext()))
            observeEvent(viewLifecycleOwner, this@UsageTimeCheckerFragment)
        }
        viewModel.init()
    }

    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            UsageTimeCheckerViewModel.EVENT_SHOW -> {
                showUsageTimeCheckerView()
            }
            UsageTimeCheckerViewModel.EVENT_TOAST -> {
                Toast.makeText(requireContext(), data?.toString() ?: return, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun showUsageTimeCheckerView() {
        UsageTimeCheckerView(requireContext())
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

