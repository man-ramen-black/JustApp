package com.black.code.contents.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.black.code.R
import com.black.code.contents.ContentsFragment
import com.black.code.contents.launcher.internal.LauncherActivity
import com.black.code.databinding.FragmentLauncherBinding


class LauncherFragment : ContentsFragment<FragmentLauncherBinding>() {
    override val title: String = "Launcher"
    override val layoutResId: Int = R.layout.fragment_launcher

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.fragment = this
    }

    fun onClickShowLauncher(view: View?) {
        activity?.startActivity(Intent(context, LauncherActivity::class.java))
    }
}