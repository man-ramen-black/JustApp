package com.black.app.ui.maintab

import android.os.Bundle
import android.view.View
import androidx.navigation.ui.setupWithNavController
import com.black.app.R
import com.black.app.databinding.FragmentMainTabBinding
import com.black.core.util.FragmentExtension.findChildNavController
import com.black.core.component.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainTabFragment: BaseFragment<FragmentMainTabBinding>() {
    override val layoutResId: Int = R.layout.fragment_main_tab

    override fun onBindVariable(binding: FragmentMainTabBinding) { }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initNavBar()
    }

    private fun initNavBar() {
        binding.bottomNavigationView.setupWithNavController(findChildNavController())
    }
}