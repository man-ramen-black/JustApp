package com.black.app.ui.main.launcher

import android.content.Intent
import android.view.View
import com.black.app.R
import com.black.app.ui.main.launcher.internal.LauncherActivity
import com.black.app.databinding.FragmentLauncherBinding
import com.black.app.ui.common.base.TitleFragment


class LauncherFragment : TitleFragment<FragmentLauncherBinding>() {
    override val title: String = "Launcher"
    override val layoutResId: Int = R.layout.fragment_launcher

    override fun bindVariable(binding: FragmentLauncherBinding) {
        binding.fragment = this
    }

    fun onClickShowLauncher(view: View?) {
        activity?.startActivity(Intent(context, LauncherActivity::class.java))
    }
}