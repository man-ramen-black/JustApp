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
    private val viewModel : ViewModel by viewModels()

    override fun bindVariable(binding: FragmentUsageTimeCheckerBinding) {
        binding.fragment = this
        binding.viewModel = viewModel
        viewModel.event.observe(this, this)
    }

    override fun onReceivedEvent(action: String, data: Any?) {
        val view = UsageTimeCheckerView(requireContext())
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
        view.attachView()
    }

    class ViewModel : EventViewModel() {
        fun onClickShow() {
            event.send()
        }
    }
}

