package com.black.app.ui.common.selectapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.black.app.R
import com.black.app.databinding.DialogSelectAppBinding
import com.black.core.util.FragmentExtension.observePopBackStackArgsWithResumed
import com.black.core.util.FragmentExtension.setPopBackStackArgs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@AndroidEntryPoint
class SelectAppDialogFragment : com.black.core.component.BaseDialogFragment<DialogSelectAppBinding>(),
    com.black.core.viewmodel.EventObserver {
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
        // 런처에서 실행 가능한 앱만 조회 (사전 설치된 YouTube, Instagram 등도 포함되도록 FLAG_SYSTEM 필터 대신 사용)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map {
                val applicationInfo = it.activityInfo.applicationInfo
                SelectAppItem(
                    applicationInfo.packageName,
                    pm.getApplicationLabel(applicationInfo),
                    pm.getApplicationIcon(applicationInfo)
                )
            }
    }
}