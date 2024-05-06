package com.black.app.ui.example.launcher

import android.content.Intent
import android.view.View
import com.black.app.R
import com.black.app.ui.example.launcher.internal.LauncherActivity
import com.black.app.databinding.FragmentLauncherBinding
import com.black.app.ui.example.ExampleFragment


class LauncherFragment : ExampleFragment<FragmentLauncherBinding>() {
    override val title: String = "Launcher"
    override val layoutResId: Int = R.layout.fragment_launcher

    override fun bindVariable(binding: FragmentLauncherBinding) {
        binding.fragment = this
    }

    fun onClickShowLauncher(view: View?) {
        activity?.startActivity(Intent(context, LauncherActivity::class.java))
    }
}