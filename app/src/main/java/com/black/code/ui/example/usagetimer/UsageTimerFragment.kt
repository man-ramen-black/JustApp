package com.black.code.ui.example.usagetimer

import android.widget.Toast
import androidx.fragment.app.viewModels
import com.black.code.R
import com.black.code.base.view.MovableOverlayView
import com.black.code.base.viewmodel.EventObserver
import com.black.code.databinding.FragmentUsageTimerBinding
import com.black.code.model.UsageTimerModel
import com.black.code.ui.example.ExampleFragment
import com.black.code.ui.example.usagetimer.view.UsageTimerView

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

