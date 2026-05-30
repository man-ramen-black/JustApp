package com.black.app.ui.maintab.main.usagetimer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.black.app.R
import com.black.app.databinding.FragmentUsageTimerBinding
import com.black.app.ui.common.selectapp.SelectAppDialogFragment
import com.black.app.ui.common.selectapp.SelectAppItem
import com.black.app.ui.common.base.TitleFragment
import com.black.app.ui.maintab.main.usagetimer.view.UsageTimerView
import com.black.core.util.FragmentExtension.navigateSafety
import com.black.core.viewmodel.EventObserver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UsageTimerFragment : TitleFragment<FragmentUsageTimerBinding>(),
    EventObserver {
    override val layoutResId: Int = R.layout.fragment_usage_timer
    override val title: String = "UsageTimer"
    private val viewModel : UsageTimerFragmentViewModel by viewModels()
    private val selectedAppAdapter = SelectedAppAdapter()

    override fun onBindVariable(binding: FragmentUsageTimerBinding) {
        binding.fragment = this
        binding.viewModel = viewModel.apply {
            observeEvent(viewLifecycleOwner, this@UsageTimerFragment)
        }
        viewModel.init()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.selectedAppsRecycler.adapter = selectedAppAdapter

        SelectAppDialogFragment.observeSelectedApp(this) {
            viewModel.onAppsSelected(it)
        }
        viewModel.selectedApps.observe(viewLifecycleOwner) { updateSelectedApps(it) }
    }

    private fun updateSelectedApps(packageNames: List<String>) {
        val packageManager = requireContext().packageManager
        val items = packageNames.map { packageName ->
            try {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                SelectAppItem(
                    packageName,
                    packageManager.getApplicationLabel(applicationInfo),
                    packageManager.getApplicationIcon(applicationInfo),
                )
            } catch (notFound: PackageManager.NameNotFoundException) {
                SelectAppItem(packageName, packageName, packageManager.defaultActivityIcon)
            }
        }
        selectedAppAdapter.submitList(items)
    }

    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            UsageTimerFragmentViewModel.EVENT_SHOW -> {
                showUsageTimerView()
            }
            UsageTimerFragmentViewModel.EVENT_TOAST -> {
                Toast.makeText(requireContext(), data?.toString() ?: return, Toast.LENGTH_SHORT)
                    .show()
            }
            UsageTimerFragmentViewModel.EVENT_DETACH_VIEW_IN_SERVICE -> {
                UsageTimerGlobal.detachView()
            }
            UsageTimerFragmentViewModel.EVENT_SHOW_ACCESSIBILITY -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            UsageTimerFragmentViewModel.EVENT_SHOW_SELECT_APP -> {
                val checked = viewModel.selectedApps.value?.toTypedArray()
                findNavController().navigateSafety(UsageTimerFragmentDirections.actionSelectApp(checked))
            }
        }
    }

    private fun showUsageTimerView() {
        UsageTimerView(requireContext())
            .attachView()
    }
}

