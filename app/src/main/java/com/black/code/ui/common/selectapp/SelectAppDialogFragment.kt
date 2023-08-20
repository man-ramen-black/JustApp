package com.black.code.ui.common.selectapp

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.black.code.R
import com.black.code.base.component.BaseDialogFragment
import com.black.code.base.viewmodel.EventObserver
import com.black.code.databinding.DialogSelectAppBinding
import com.black.code.util.FragmentExtension.observePopBackStackArgsWithResumed
import com.black.code.util.FragmentExtension.setPopBackStackArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class SelectAppDialogFragment : BaseDialogFragment<DialogSelectAppBinding>(), EventObserver {
    companion object {
        private const val KEY_PACKAGE_NAME_LIST = "packageNameList"
        /**
         * @param observer List<packageName: String>
         */
        fun observeSelectedApp(fragment: Fragment, observer: Observer<List<String>>) {
            fragment.findNavController()
                .observePopBackStackArgsWithResumed(fragment.viewLifecycleOwner, KEY_PACKAGE_NAME_LIST, observer)
        }
    }

    private val viewModel: SelectAppViewModel by viewModels()
    private val adapter by lazy { SelectAppAdapter(viewModel) }
    private val navController by lazy { findNavController() }

    override val layoutResId: Int = R.layout.dialog_select_app

    override fun onBindVariable(binding: DialogSelectAppBinding) {
        binding.viewModel = viewModel
        binding.adapter = adapter
        viewModel.observeEvent(viewLifecycleOwner, this)
        viewModel.itemList.observe(viewLifecycleOwner) { adapter.submitList(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.init(generateSelectAppItemList())
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            SelectAppViewModel.EVENT_APP_SELECTED -> {
                navController.setPopBackStackArgs(KEY_PACKAGE_NAME_LIST, data as List<String>)
            }

            SelectAppViewModel.EVENT_CLOSE -> {
                navController.popBackStack()
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun generateSelectAppItemList() : List<SelectAppItem> {
        val pm = requireContext().packageManager
        // 실행할 수 있는 앱 조회 (PackageManager.GET_ACTIVITIES)
        val infoList = pm.getInstalledApplications(PackageManager.GET_ACTIVITIES)
        return infoList
            // 시스템 앱 제외
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map {
                SelectAppItem(it.packageName, pm.getApplicationLabel(it), pm.getApplicationIcon(it))
            }
    }
}