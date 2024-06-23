package com.black.feature.pokerogue.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.fragment.findNavController
import com.black.core.component.BaseFragment
import com.black.core.dialog.BKAlertDialog
import com.black.core.util.BackPressHelper
import com.black.core.util.Cutout
import com.black.core.util.Extensions.collect
import com.black.core.util.FragmentExtension.viewLifecycleScope
import com.black.core.util.UiUtil
import com.black.core.webkit.BKWebView
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgFragmentPokeRogueBinding
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.SoftReference

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
        viewModel.selectedTypes.observe(viewLifecycleOwner) {
            selectedTypeAdapter.submitList(it)
        }

        attackAdapter = AttackAdapter()
        binding.attackAdapter = attackAdapter
        viewModel.attackItemList.observe(viewLifecycleOwner) {
            attackAdapter.submitList(it)
        }

        defenceListAdapter = DefenceListAdapter()
        binding.defenceListAdapter = defenceListAdapter
        viewModel.defenceListItemList.observe(viewLifecycleOwner) {
            defenceListAdapter.submitList(it)
        }

        typeAdapter = TypeAdapter().also { it.submitList(typeList) }
        binding.typeSelectAdapter = typeAdapter

        viewModel.eventFlow.collect(viewLifecycleScope) {
            when (it.action) {
                PokeRogueViewModel.EVENT_RELOAD -> {
                    BKAlertDialog(requireActivity())
                        .setMessage(R.string.reload_confirm_message)
                        .setPositiveButton(R.string.ok) { dialog, _ ->
                            viewModel.webView.get()?.reload()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            initViewModel()
        }

        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            val window = requireActivity().window
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    UiUtil.setImmersiveMode(window, true)
                    UiUtil.setCutout(window, Cutout.SHORT_EDGES)

                    if (isRestoring) {
                        return@LifecycleEventObserver
                    }

                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.webView.get()?.onResume()
                        // WebView 객체가 제거된 경우 ViewModel을 다시 초기화
                        ?: run { initViewModel() }

                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.webView.get()?.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    if (isRestoring) {
                        return@LifecycleEventObserver
                    }

                    viewModel.webView.get()?.clear()
                    viewModel.webView.clear()
                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                    UiUtil.setImmersiveMode(requireActivity().window, false)
                    UiUtil.setCutout(requireActivity().window, Cutout.DEFAULT)
                }
                else -> {}
            }
        })
    }

    private fun initViewModel() {
        val webView = BKWebView(requireContext().applicationContext)
            .apply { addWebViewClientCallback(viewModel) }
            .let { SoftReference(it) }
        viewModel.init(webView)
    }
}