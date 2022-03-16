package com.black.code.contents.usagetimechecker

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.black.code.R
import com.black.code.base.MovableOverlayView
import com.black.code.contents.ContentsFragment
import com.black.code.databinding.FragmentUsageTimeCheckerBinding

class UsageTimeCheckerFragment : ContentsFragment<FragmentUsageTimeCheckerBinding>() {
    override val layoutResId: Int = R.layout.fragment_usage_time_checker
    override val title: String = "UsageTimeChecker"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.fragment = this
    }

    fun onClickShow() {
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
}