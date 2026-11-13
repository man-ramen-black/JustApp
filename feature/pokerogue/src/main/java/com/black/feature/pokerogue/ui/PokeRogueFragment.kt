package com.black.feature.pokerogue.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.black.core.component.BaseFragment
import com.black.core.dialog.BKAlertDialogBuilder
import com.black.core.dialog.DialogManager.Companion.showByDialogManager
import com.black.core.dialog.DialogManager.ShowType
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
    companion object {
        /** 새로고침 확인 다이얼로그 tag */
        private const val RELOAD_CONFIRM_DIALOG_TAG = "PokeRogueReloadConfirm"
    }

    private val typeList by lazy {
        PokeType.entries
            .map { it -> TypeUIState(it) { viewModel.toggleType(it.type) } }
    }

    private val viewModel: PokeRogueViewModel by viewModels()

    /**
     * Fragment 수명 동안 보관하는 게임 WebView
     *
     * MainActivity가 회전을 configChanges로 직접 처리해 Fragment가 유지되므로 회전해도 게임(JS 메모리) 상태 유지.
     * Activity context로 만들어 JS 다이얼로그를 Activity에서 띄우고, 파기는 [onDestroy]에서 수행
     */
    private var webView: BKWebView? = null

    /**
     * 현재 바인딩을 만든 화면 방향
     *
     * 방향이 바뀌면 [onConfigurationChanged]에서 그 방향의 레이아웃으로 교체
     */
    private var layoutOrientation = Configuration.ORIENTATION_UNDEFINED

    override val layoutResId: Int = R.layout.pkrg_fragment_poke_rogue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = BKWebView(requireActivity()).also { it.addWebViewClientCallback(viewModel) }
        // 새 WebView는 페이지가 없으므로 Activity 재생성으로 남은 ViewModel의 시작 상태도 되돌림
        viewModel.resetGameState()

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

    /**
     * 바인딩 루트를 컨테이너로 감싸 Fragment 뷰로 사용
     *
     * Fragment 뷰는 교체할 수 없으므로 방향이 바뀌면 [replaceBinding]으로 이 컨테이너 안의 바인딩 루트만 교체
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layoutContainer = FrameLayout(inflater.context)
        super.onCreateView(inflater, layoutContainer, savedInstanceState)
        layoutContainer.addView(binding.root)
        return layoutContainer
    }

    /**
     * 바인딩 변수 연결
     *
     * 레이아웃을 교체할 때 새 바인딩에도 다시 호출되므로 관찰 등록은 뷰마다 한 번인 [onViewCreated]에서 수행
     */
    override fun onBindVariable(binding: PkrgFragmentPokeRogueBinding) {
        layoutOrientation = resources.configuration.orientation
        binding.viewModel = viewModel
        // 레이아웃을 교체하면 같은 WebView를 이전 컨테이너에서 떼어 새 컨테이너에 붙임(webView 바인딩 어댑터)
        binding.webView = webView

        // 어댑터는 연결한 RecyclerView를 붙잡으므로 교체한 이전 레이아웃과 나눠 쓰지 않도록 바인딩마다 새로 만듦.
        // 상성 목록은 레이아웃의 itemList 바인딩이 바인딩 실행 때 현재 값으로 채우고 이후 변경도 전달(교체 시 붙이기 전에 실행)
        binding.selectedTypeAdapter = TypeAdapter()
        binding.attackAdapter = AttackAdapter()
        binding.defenceListAdapter = DefenceListAdapter()
        binding.typeSelectAdapter = TypeAdapter().also { it.submitList(typeList) }
    }

    /**
     * 화면 방향이 바뀌면 그 방향의 레이아웃으로 교체
     *
     * MainActivity가 회전을 직접 처리해 다시 만들어지지 않으므로 layout-land 리소스가 자동으로 적용되지 않음
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == layoutOrientation) {
            return
        }

        val layoutContainer = view as? ViewGroup ?: return
        replaceBinding(layoutContainer)
    }

    private fun handleEvent(event: ViewModelEvent) {
        when (event) {
            is PokeRogueViewModel.EventShowReloadConfirm -> {
                // 새로고침 버튼을 빠르게 두 번 눌러도 다이얼로그가 겹치지 않도록 떠 있으면 미표시
                BKAlertDialogBuilder(requireActivity())
                    .setMessage(R.string.reload_confirm_message)
                    .setPositiveButton(R.string.ok) { dialog, _ ->
                        webView?.reload()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.cancel()
                    }
                    .showByDialogManager(
                        requireActivity(),
                        tag = RELOAD_CONFIRM_DIALOG_TAG,
                        showType = ShowType.CANCEL,
                    )
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

            is PokeRogueViewModel.EventLoadUrl -> {
                webView?.loadUrl(event.url)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handleEvent(it) }
            }
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
                    webView?.let {
                        it.onResume()
                        // 게임 화면만 쓰는 프로세스 전역 JS 타이머 재개
                        it.resumeTimers()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    webView?.let {
                        it.onPause()
                        // 백그라운드에서 게임 JS가 돌지 않도록 프로세스 전역 JS 타이머 정지
                        it.pauseTimers()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // 화면 이탈로 Fragment가 제거될 때만 복구(지정하지 않은 설정 변경 등 Activity 재생성 시 유지), WebView 파기는 onDestroy 담당
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

    /** 화면 이탈이나 Activity 재생성으로 Fragment가 파기될 때 WebView 파기 */
    override fun onDestroy() {
        webView?.let {
            it.removeWebViewClientCallback(viewModel)
            // destroy는 뷰 계층에서 뗀 뒤 호출해야 함
            (it.parent as? ViewGroup)?.removeView(it)
            // pauseTimers는 프로세스 전역이라 이후 만드는 WebView가 멈춘 채 시작하지 않도록 되돌림
            it.resumeTimers()
            it.clear()
        }
        webView = null
        super.onDestroy()
    }
}
