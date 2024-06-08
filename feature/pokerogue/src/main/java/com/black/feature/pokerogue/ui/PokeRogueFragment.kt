package com.black.feature.pokerogue.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.eventFlow
import androidx.lifecycle.lifecycleScope
import com.black.core.component.BaseFragment
import com.black.core.dialog.BKAlertDialog
import com.black.core.util.UiUtil
import com.black.core.webkit.BKWebView
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgFragmentPokeRogueBinding
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PokeRogueFragment: BaseFragment<PkrgFragmentPokeRogueBinding>() {

    private val typeList by lazy {
        PokeType.entries
            .map { it -> TypeUIState(it) { viewModel.onTypeClick(it.type) } }
    }

    private val viewModel: PokeRogueViewModel by viewModels()
    private lateinit var selectedTypeAdapter: TypeAdapter
    private lateinit var attackAdapter: AttackAdapter
    private lateinit var defenceListAdapter: DefenceListAdapter
    private lateinit var typeAdapter: TypeAdapter

    override val layoutResId: Int = R.layout.pkrg_fragment_poke_rogue

    override fun onBindVariable(binding: PkrgFragmentPokeRogueBinding) {
        binding.viewModel = viewModel

        selectedTypeAdapter = TypeAdapter()
        binding.selectedTypeAdapter = selectedTypeAdapter
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            viewModel.selectedTypes.collect { selectedTypeAdapter.submitList(it) }
        }

        attackAdapter = AttackAdapter()
        binding.attackAdapter = attackAdapter
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            viewModel.attackItemList.collect { attackAdapter.submitList(it) }
        }

        defenceListAdapter = DefenceListAdapter()
        binding.defenceListAdapter = defenceListAdapter
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            viewModel.defenceListItemList.collect { defenceListAdapter.submitList(it) }
        }

        typeAdapter = TypeAdapter().also { it.submitList(typeList) }
        binding.typeSelectAdapter = typeAdapter

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            viewModel.eventFlow.collect {
                when (it.action) {
                    PokeRogueViewModel.EVENT_RELOAD -> {
                        BKAlertDialog(requireActivity())
                            .setMessage(R.string.reload_confirm_message)
                            .setPositiveButton(R.string.ok) { dialog, _ ->
                                viewModel.webView.reload()
                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.cancel) { dialog, _ ->
                                dialog.cancel()
                            }
                            .show()
                    }

                    PokeRogueViewModel.EVENT_ROTATE -> {
                        val orientation = requireActivity().resources.configuration.orientation
                        requireActivity().requestedOrientation = if (orientation == ORIENTATION_PORTRAIT) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            viewModel.webView = BKWebView(requireActivity())
                .apply { addWebViewClientCallback(viewModel) }
        }
        lifecycleScope.launch(Dispatchers.Main) {
            delay(3000)
            viewModel.webView.invalidate()
        }

        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            val window = requireActivity().window
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (isRestoring) {
                        return@LifecycleEventObserver
                    }

                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    UiUtil.setImmersiveMode(window, true)
                    UiUtil.setCutout(window, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS)
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.webView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.webView.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    if (isRestoring) {
                        return@LifecycleEventObserver
                    }
                    viewModel.webView.clear()
                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                    UiUtil.setImmersiveMode(requireActivity().window, false)
                    UiUtil.setCutout(requireActivity().window, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER)
                }
                else -> {}
            }
        })
    }
}