package com.black.code.ui.example.usagetimer

import android.widget.Toast
import androidx.fragment.app.viewModels
import com.black.code.R
import com.black.code.base.view.MovableOverlayView
import com.black.code.base.viewmodel.EventObserver
import com.black.code.databinding.FragmentUsageTimerBinding

class UsageTimerFragment : com.black.code.ui.example.ExampleFragment<FragmentUsageTimerBinding>(), EventObserver {
    override val layoutResId: Int = R.layout.fragment_usage_timer
    override val title: String = "UsageTimer"
    private val viewModel : UsageTimerViewModel by viewModels()

    override fun bindVariable(binding: FragmentUsageTimerBinding) {
        binding.fragment = this
        binding.viewModel = viewModel.apply {
            setModel(UsageTimerModel(requireContext()))
            observeEvent(viewLifecycleOwner, this@UsageTimerFragment)
        }
        viewModel.init()
    }

    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            UsageTimerViewModel.EVENT_SHOW -> {
                showUsageTimerView()
            }
            UsageTimerViewModel.EVENT_TOAST -> {
                Toast.makeText(requireContext(), data?.toString() ?: return, Toast.LENGTH_SHORT)
                    .show()
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

