package com.black.code.base

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import com.black.code.util.Log

abstract class BaseFragment<T : ViewDataBinding> : Fragment() {
    protected var binding: T? = null
    protected abstract val layoutResId : Int
    protected lateinit var activityLauncher : ActivityResultLauncher<Intent>

    @CallSuper
    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 자식 클래스 이름 출력을 위해 javaClass.simpleName 출력
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(javaClass.simpleName)
    }

    /**
     * Fragment View Binding = https://developer.android.com/topic/libraries/view-binding?hl=ko#fragments
     * View 셋팅은 onViewCreated에서 하도록 권장
     */
    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(javaClass.simpleName)
        binding = DataBindingUtil.inflate(inflater, layoutResId, container, false)

        activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d("onActivityResult : resultCode : ${it.resultCode}, data : ${it.data}")
            onActivityResult(it.resultCode, it.data)
        }
        return binding!!.root
    }

    open fun onActivityResult(resultCode: Int, data: Intent?) {
    }

    protected fun launchActivity(intent: Intent) {
        activityLauncher.launch(intent)
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onStop() {
        super.onStop()
        Log.d(javaClass.simpleName)
    }

    /**
     * 참고: 프래그먼트는 뷰보다 오래 지속됩니다.
     * 프래그먼트의 onDestroyView() 메서드에서 결합 클래스 인스턴스 참조를 정리해야 합니다.
     * https://developer.android.com/topic/libraries/view-binding?hl=ko#fragments
     */
    @CallSuper
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(javaClass.simpleName)
        binding = null
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onDetach() {
        super.onDetach()
        Log.d(javaClass.simpleName)
    }
}