package com.black.study.contents.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.black.study.R
import com.black.study.contents.ContentsFragment
import com.black.study.contents.launcher.internal.LauncherActivity
import com.black.study.databinding.FragmentLauncherBinding


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