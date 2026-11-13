package com.black.app.ui.common.selectapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.black.app.R
import com.black.app.databinding.DialogSelectAppBinding
import com.black.app.util.InstalledAppResolver
import com.black.core.component.BaseDialogFragment
import com.black.core.viewmodel.ViewModelEvent
import com.black.core.viewmodel.collectEvents
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class SelectAppDialogFragment : BaseDialogFragment<DialogSelectAppBinding>() {
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

        /** 앱 선택 완료 결과(List<packageName: String>) 관찰 */
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

    @Inject
    lateinit var installedAppResolver: InstalledAppResolver

    private val viewModel: SelectAppViewModel by viewModels()
    private val adapter by lazy { SelectAppAdapter(viewModel) }

    override val layoutResId: Int = R.layout.dialog_select_app

    override fun onBindVariable(binding: DialogSelectAppBinding) {
        binding.viewModel = viewModel
        binding.adapter = adapter
        collectEvents(viewModel) { handleEvent(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 회전으로 뷰만 다시 만들어지면 ViewModel의 목록과 바꾼 체크를 그대로 사용
        if (viewModel.isInitialized) {
            return
        }
        val checkedPackageNames =
            arguments?.getStringArrayList(ARGUMENT_KEY_CHECKED_PACKAGE_NAMES) ?: emptyList<String>()
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.init(generateSelectAppItemList(), checkedPackageNames.toList())
        }
    }

    private fun handleEvent(event: ViewModelEvent) {
        when (event) {
            is SelectAppViewModel.EventAppSelected -> {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY_SELECT_APP,
                    bundleOf(RESULT_KEY_PACKAGE_NAME_LIST to ArrayList(event.packageNames)),
                )
            }

            is SelectAppViewModel.EventClose -> {
                dismiss()
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun generateSelectAppItemList() : List<SelectAppItem> {
        val packageManager = requireContext().packageManager
        // 런처에서 실행 가능한 앱만 조회 (사전 설치된 YouTube, Instagram 등도 포함되도록 FLAG_SYSTEM 필터 대신 사용)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map {
                // 조회 결과의 ApplicationInfo를 그대로 넘겨 앱마다 패키지 정보를 다시 조회하지 않음
                val installedApp = installedAppResolver.find(it.activityInfo.applicationInfo)
                SelectAppItem(installedApp.packageName, installedApp.label, installedApp.icon)
            }
    }
}
