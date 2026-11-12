package com.black.core.component

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.CallSuper
import androidx.core.view.doOnAttach
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.black.core.util.Log

abstract class BaseFragment<T : ViewDataBinding> : Fragment() {
    private var bindingOrNull: T? = null
    // onCreateView ~ onDestroyView까지 유효
    protected val binding get() = bindingOrNull!!

    /**
     * 저장된 상태로 재생성 중이거나 상태 저장 이후인지 여부
     *
     * AndroidFragment(Compose) 호스팅은 화면 이탈로 제거하기 직전에도 saveFragmentInstanceState를 호출해 true가 됨.
     * 이탈과 재생성을 구분하려면 [isRemoving] 사용
     */
    protected var isRestoring = false
    val onBackPressedDispatcher get() = requireActivity().onBackPressedDispatcher
    private var onBackPressed: (() -> Unit)? = null
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            onBackPressed?.invoke()
        }
    }

    protected abstract val layoutResId : Int

    init {
        // 생명주기 로그는 단계마다 재정의하지 않고 관찰자 하나로 출력(자식 클래스 이름 출력을 위해 javaClass.simpleName)
        lifecycle.addObserver(LifecycleEventObserver { _, event -> Log.d("${javaClass.simpleName} : $event") })
    }

    abstract fun onBindVariable(binding: T)

    fun setOnBackPressed(onBackPressed: (() -> Unit)?) {
        onBackPressedCallback.isEnabled = onBackPressed != null
        this.onBackPressed = onBackPressed
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRestoring = savedInstanceState != null
    }

    /*
    #Fragment #DataBinding
    https://developer.android.com/topic/libraries/view-binding?hl=ko#fragments
     */
    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(javaClass.simpleName)
        bindingOrNull = inflateBinding(inflater, container)

        onBindVariable(binding)
        return binding.root
    }

    /** 현재 설정에 맞는 [layoutResId] 리소스로 바인딩 생성 */
    private fun inflateBinding(inflater: LayoutInflater, parent: ViewGroup?): T {
        return DataBindingUtil.inflate<T>(inflater, layoutResId, parent, false).apply {
            // lifecycleOwner를 적용하지 않으면 liveData 변경 시 뷰에 반영되지 않음
            // https://stackoverflow.com/questions/59545195/mutablelivedata-not-updating-in-ui
            lifecycleOwner = this@BaseFragment.viewLifecycleOwner
        }
    }

    /**
     * 현재 설정의 [layoutResId] 리소스로 바인딩을 다시 만들어 [parent] 안의 이전 바인딩 루트와 교체
     *
     * Activity가 configChanges로 설정 변경을 직접 처리하면 방향별 레이아웃(layout-land 등)이 자동으로 다시 적용되지 않아 [onConfigurationChanged]에서 호출.
     * Fragment 뷰는 교체할 수 없으므로 [parent]는 바인딩 루트를 감싸 Fragment 뷰로 돌려준 컨테이너.
     * 새 바인딩에도 [onBindVariable]을 다시 호출하므로 거기서는 바인딩 변수만 연결하고, 관찰·수집 등록은 뷰마다 한 번 호출되는 [onViewCreated]에서 수행
     */
    protected fun replaceBinding(parent: ViewGroup) {
        val previous = binding
        bindingOrNull = inflateBinding(layoutInflater, parent)
        onBindVariable(binding)
        // 붙이기 전에 바인딩을 실행해 바인딩 전 기본값으로 그려지는 프레임 방지
        binding.executePendingBindings()

        // 이전 바인딩의 관찰과 수명주기 연결 해제
        previous.unbind()
        previous.lifecycleOwner = null
        parent.removeView(previous.root)
        parent.addView(binding.root)
    }

    /** View 셋팅은 onViewCreated에서 하는 것을 권장 */
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(javaClass.simpleName)

        // 복원된 Fragment는 컨테이너가 준비된 뒤에 뷰가 붙으므로 부착 시점에 상위 화면을 찾아 등록
        view.doOnAttach { addOnBackPressedCallback(it) }
    }

    /**
     * 뒤로가기 콜백을 Fragment를 감싼 상위 화면의 수명주기로 등록
     *
     * OnBackPressedDispatcher는 마지막에 등록된 콜백을 먼저 실행하고, 등록 수명주기의 ON_START마다 콜백을 다시 등록함.
     * Compose NavHost(AndroidFragment) 호스팅에서 Fragment 수명주기로 등록하면 Activity 재생성(복원된 Fragment가 NavHost 컴포지션보다 먼저 시작)이나
     * 백그라운드 복귀(Fragment ON_START가 Activity ON_START보다 먼저 전달)마다 NavHost 뒤로가기 콜백이 나중에 등록되어 Fragment 콜백보다 우선함.
     * NavHost 안의 상위 화면 수명주기(NavBackStackEntry)는 NavHost 콜백 등록 뒤에 시작하므로 Fragment 콜백이 먼저 실행됨
     */
    private fun addOnBackPressedCallback(view: View) {
        // >> https://developer.android.com/guide/navigation/navigation-custom-back?hl=ko
        // https://stackoverflow.com/questions/63376684/onbackpressedcallback-not-trigger-after-screen-rotation
        // LifecycleOwner 설정 시 LifeCycle에 따라 자동으로 enabled가 on/off되며, remove 처리는 onDestroyView에서 수행
        // Fragment 뷰 자신에는 viewLifecycleOwner가 지정되어 있으므로 부모부터 탐색
        val hostLifecycleOwner = (view.parent as? View)?.findViewTreeLifecycleOwner() ?: viewLifecycleOwner
        onBackPressedDispatcher.addCallback(hostLifecycleOwner, onBackPressedCallback)
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        isRestoring = false
    }

    /*
    참고: 프래그먼트는 뷰보다 오래 지속됩니다.
    프래그먼트의 onDestroyView() 메서드에서 결합 클래스 인스턴스 참조를 정리해야 합니다.
    #fragment #onDestroyView #destroy
    https://developer.android.com/topic/libraries/view-binding?hl=ko#fragments
     */
    @CallSuper
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(javaClass.simpleName)
        // 상위 화면 수명주기로 등록한 뒤로가기 콜백은 뷰와 함께 해제(상위 화면이 Fragment보다 오래 유지될 수 있음)
        onBackPressedCallback.remove()
        bindingOrNull = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        isRestoring = true
    }

}