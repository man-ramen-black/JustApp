package com.black.feature.pokerogue.ui

import android.content.MutableContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.black.core.component.BaseFragment
import com.black.core.dialog.BKAlertDialogBuilder
import com.black.core.util.BackPressHelper
import com.black.core.util.Cutout
import com.black.core.util.FragmentExtension.viewLifecycleScope
import com.black.core.util.UiUtil
import com.black.core.viewmodel.ViewModelEvent
import com.black.core.webkit.BKWebView
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgFragmentPokeRogueBinding
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PokeRogueFragment: BaseFragment<PkrgFragmentPokeRogueBinding>() {

    private val typeList by lazy {
        PokeType.entries
            .map { it -> TypeUIState(it) { viewModel.toggleType(it.type) } }
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
            if (viewModel.handleBack()) {
                return@setOnBackPressed
            }

            BackPressHelper.onBackPressed(
                requireActivity(),
                getString(R.string.poke_rogue_back_pressed_message)
            ) {
                // Compose NavHost 호스팅으로 Fragment NavController 부재 — 콜백 해제 후 시스템 뒤로가기 위임
                setOnBackPressed(null)
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onBindVariable(binding: PkrgFragmentPokeRogueBinding) {
        // ViewModel이 보관한 WebView를 현재 Activity에 연결(JS 다이얼로그를 띄울 Activity)
        val webView = viewModel.obtainWebView { BKWebView(MutableContextWrapper(requireActivity())) }
        (webView.context as? MutableContextWrapper)?.baseContext = requireActivity()
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

        viewLifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handleEvent(it) }
            }
        }
    }

    private fun handleEvent(event: ViewModelEvent) {
        when (event) {
            is PokeRogueViewModel.EventShowReloadConfirm -> {
                BKAlertDialogBuilder(requireActivity())
                    .setMessage(R.string.reload_confirm_message)
                    .setPositiveButton(R.string.ok) { dialog, _ ->
                        viewModel.webView.value?.reload()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.cancel()
                    }
                    .show()
            }

            is PokeRogueViewModel.EventRotate -> {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                    viewModel.webView.value?.let {
                        it.onResume()
                        // 게임 화면만 쓰는 프로세스 전역 JS 타이머 재개
                        it.resumeTimers()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.webView.value?.let {
                        it.onPause()
                        // 백그라운드에서 게임 JS가 돌지 않도록 프로세스 전역 JS 타이머 정지
                        it.pauseTimers()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // 회전 등 Activity 재생성 동안 파기된 Activity를 붙잡지 않도록 WebView 분리
                    viewModel.webView.value?.let {
                        (it.parent as? ViewGroup)?.removeView(it)
                        (it.context as? MutableContextWrapper)?.baseContext = requireContext().applicationContext
                    }

                    // 화면 이탈로 Fragment가 제거될 때만 복구(회전 등 Activity 재생성 시 유지), WebView 파기는 ViewModel 담당
                    // AndroidFragment는 제거 직전에 상태를 저장해 isRestoring이 true가 되므로 isRemoving으로 구분
                    if (!isRemoving) {
                        return@LifecycleEventObserver
                    }

                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                    UiUtil.setImmersiveMode(requireActivity().window, false)
                    UiUtil.setCutout(requireActivity().window, Cutout.DEFAULT)
                }
                else -> {}
            }
        })
    }
}
