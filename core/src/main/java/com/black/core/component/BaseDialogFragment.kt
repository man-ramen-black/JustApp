package com.black.core.component

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleEventObserver
import com.black.core.util.Log

/**
 * Created by jinhyuk.lee on 2022/05/20
 **/
abstract class BaseDialogFragment<T : ViewDataBinding> : DialogFragment() {
    private var bindingOrNull: T? = null
    // onCreateView ~ onDestroyView까지 유효
    protected val binding get() = bindingOrNull!!

    private var onDismissListener: ((DialogInterface) -> Unit)? = null

    protected abstract val layoutResId : Int

    init {
        // 생명주기 로그는 단계마다 재정의하지 않고 관찰자 하나로 출력(자식 클래스 이름 출력을 위해 javaClass.simpleName)
        lifecycle.addObserver(LifecycleEventObserver { _, event -> Log.d("${javaClass.simpleName} : $event") })
    }

    abstract fun onBindVariable(binding: T)

    open fun show(manager: FragmentManager) {
        this.show(manager, javaClass.simpleName)
    }

    open fun setOnDismissListener(onDismiss: (DialogInterface) -> Unit) {
        onDismissListener = onDismiss
    }

    protected open fun onBackPressed() {
        cancel()
    }

    protected fun cancel() {
        dialog?.cancel()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        return object: Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                this@BaseDialogFragment.onBackPressed()
            }
        }
    }

    /** Fragment View Binding = https://developer.android.com/topic/libraries/view-binding?hl=ko#fragments */
    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(javaClass.simpleName)
        bindingOrNull = DataBindingUtil.inflate<T>(inflater, layoutResId, container, false).apply {
            // lifecycleOwner를 적용하지 않으면 liveData 변경 시 뷰에 반영되지 않음
            // https://stackoverflow.com/questions/59545195/mutablelivedata-not-updating-in-ui
            lifecycleOwner = viewLifecycleOwner
        }
        onBindVariable(bindingOrNull!!)
        return bindingOrNull!!.root
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
        bindingOrNull = null
    }

    @CallSuper
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke(dialog)
    }
}