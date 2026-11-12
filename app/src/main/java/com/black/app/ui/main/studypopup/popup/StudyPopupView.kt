package com.black.app.ui.main.studypopup.popup

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.black.app.R
import com.black.app.databinding.ViewStudyPopupBinding
import com.black.app.model.StudyPopupRepository
import com.black.core.util.Log
import com.black.core.view.LoopingPagerAdapter
import com.black.core.view.OverlayView
import com.black.core.viewmodel.ViewModelEvent
import kotlinx.coroutines.launch

/**
 * Created by jinhyuk.lee on 2022/05/24
 **/
class StudyPopupView : OverlayView<ViewStudyPopupBinding> {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
            : super(context, attrs, defStyleAttr, defStyleRes)

    /** 오버레이는 ViewModelStoreOwner 안에 있지 않아 뷰가 가진 저장소로 ViewModel 수명 관리(창에서 떼면 onCleared) */
    private val viewModelStore = ViewModelStore()
    private val viewModel by lazy {
        val repository = StudyPopupRepository(context)
        val factory = viewModelFactory { initializer { StudyPopupViewModel(repository) } }
        ViewModelProvider(viewModelStore, factory)[StudyPopupViewModel::class.java]
    }
    private val adapter by lazy { StudyPopupPagerAdapter() }

    override val layoutId: Int
        get() = R.layout.view_study_popup

    override val styleableId: IntArray?
        get() = null

    override fun initialize(binding: ViewStudyPopupBinding, typedArray: TypedArray?) {

    }

    override fun bindVariable(binding: ViewStudyPopupBinding) {
        super.bindVariable(binding)
        Log.d()
        binding.viewModel = viewModel
        binding.adapter = adapter
        // 창에서 떼면(DESTROYED) 수집도 함께 종료
        lifecycleScope.launch {
            viewModel.events.collect { handleEvent(it) }
        }
        viewModel.loadContents()
    }

    override fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams) {
        windowParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            windowAnimations = android.R.style.Animation_Dialog
        }
    }

    private fun handleEvent(event: ViewModelEvent) {
        when (event) {
            is StudyPopupViewModel.EventClose -> {
                detachView()
            }
            is StudyPopupViewModel.EventShowContents -> {
                Log.d("contents size : ${event.contents.size}")
                // 목록 반영이 끝난 뒤 시작 위치를 정해야 첫 페이지가 바로 보임
                adapter.submitList(event.contents) {
                    viewModel.currentItem.set(LoopingPagerAdapter.INITIAL_POSITION)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewModelStore.clear()
    }
}
