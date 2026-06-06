package com.black.app.ui.common.selectapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.black.app.R
import com.black.app.databinding.DialogSelectAppBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@AndroidEntryPoint
class SelectAppDialogFragment : com.black.core.component.BaseDialogFragment<DialogSelectAppBinding>(),
    com.black.core.viewmodel.EventObserver {
    companion object {
        const val TAG = "SelectAppDialog"
        private const val REQUEST_KEY_SELECT_APP = "selectApp"
        private const val RESULT_KEY_PACKAGE_NAME_LIST = "packageNameList"
        private const val ARGUMENT_KEY_CHECKED_PACKAGE_NAMES = "checkedPackageNames"

        fun newInstance(checkedPackageNames: List<String>): SelectAppDialogFragment {
            return SelectAppDialogFragment().apply {
                arguments = bundleOf(
                    ARGUMENT_KEY_CHECKED_PACKAGE_NAMES to ArrayList(checkedPackageNames)
                )
            }
        }

        /**
         * 앱 선택 완료 결과(List<packageName: String>) 관찰
         */
        fun observeSelectedApp(
            fragmentManager: FragmentManager,
            lifecycleOwner: LifecycleOwner,
            listener: (List<String>) -> Unit,
        ) {
            fragmentManager.setFragmentResultListener(REQUEST_KEY_SELECT_APP, lifecycleOwner) { _, result ->
                listener(result.getStringArrayList(RESULT_KEY_PACKAGE_NAME_LIST) ?: emptyList())
            }
        }
    }

    private val viewModel: SelectAppViewModel by viewModels()
    private val adapter by lazy { SelectAppAdapter(viewModel) }

    override val layoutResId: Int = R.layout.dialog_select_app

    override fun onBindVariable(binding: DialogSelectAppBinding) {
        binding.viewModel = viewModel
        binding.adapter = adapter
        viewModel.observeEvent(viewLifecycleOwner, this)
        viewModel.itemList.observe(viewLifecycleOwner) { adapter.submitList(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val checkedPackageNames =
            arguments?.getStringArrayList(ARGUMENT_KEY_CHECKED_PACKAGE_NAMES) ?: emptyList<String>()
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.init(generateSelectAppItemList(), checkedPackageNames.toList())
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            SelectAppViewModel.EVENT_APP_SELECTED -> {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY_SELECT_APP,
                    bundleOf(RESULT_KEY_PACKAGE_NAME_LIST to ArrayList(data as List<String>)),
                )
            }

            SelectAppViewModel.EVENT_CLOSE -> {
                dismiss()
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
