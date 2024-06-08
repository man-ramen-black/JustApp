package com.black.feature.pokerogue.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.black.core.component.BaseFragment
import com.black.core.dialog.BKAlertDialog
import com.black.core.util.BackPressHelper
import com.black.core.util.Log
import com.black.core.util.UiUtil
import com.black.core.webkit.BKWebView
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgFragmentPokeRogueBinding
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setOnBackPressed {
            if (viewModel.onBackPressed()) {
                return@setOnBackPressed
            }

            BackPressHelper.onBackPressed(
                requireActivity(),
                getString(R.string.poke_rogue_back_pressed_message)
            ) {
                findNavController().popBackStack()
            }
        }
    }

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
                            viewModel.isLandscapeButtonShowing.value = true
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            viewModel.webView = BKWebView(requireActivity())
                .apply { addWebViewClientCallback(viewModel) }
        }

        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            val window = requireActivity().window
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    UiUtil.setImmersiveMode(window, true)
                    UiUtil.setCutout(window, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS)

                    if (isRestoring) {
                        return@LifecycleEventObserver
                    }

                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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