package com.black.code.ui.example.launcher

import android.content.Intent
import android.view.View
import com.black.code.R
import com.black.code.ui.example.launcher.internal.LauncherActivity
import com.black.code.databinding.FragmentLauncherBinding


class LauncherFragment : com.black.code.ui.example.ExampleFragment<FragmentLauncherBinding>() {
    override val title: String = "Launcher"
    override val layoutResId: Int = R.layout.fragment_launcher

    override fun bindVariable(binding: FragmentLauncherBinding) {
        binding.fragment = this
    }

    fun onClickShowLauncher(view: View?) {
        activity?.startActivity(Intent(context, LauncherActivity::class.java))
    }
}