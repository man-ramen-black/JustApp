package com.black.code.contents.service

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.black.code.R
import com.black.code.contents.ContentsFragment
import com.black.code.databinding.FragmentServiceBinding

/**
 * ForegroundService
 * https://developer.android.com/guide/components/foreground-services
 */
class ServiceFragment : ContentsFragment<FragmentServiceBinding>() {
    override val layoutResId: Int = R.layout.fragment_service
    override val title: String = "Service"
    private val viewModel : ServiceViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.fragment = this
        binding?.viewModel = viewModel
    }
}