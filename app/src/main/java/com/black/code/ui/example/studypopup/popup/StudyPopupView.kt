package com.black.code.ui.example.studypopup.popup

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.WindowManager
import com.black.code.R
import com.black.code.base.view.OverlayView
import com.black.code.base.view.setLoopingAdapter
import com.black.code.base.viewmodel.EventObserver
import com.black.code.databinding.ViewStudyPopupBinding
import com.black.code.model.StudyPopupModel
import com.black.code.model.database.studypopup.StudyPopupData
import com.black.code.util.Log

/**
 * Created by jinhyuk.lee on 2022/05/24
 **/
class StudyPopupView : OverlayView<ViewStudyPopupBinding>, EventObserver {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
            : super(context, attrs, defStyleAttr, defStyleRes)

    private val viewModel by lazy { StudyPopupViewModel() }
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
        binding.viewModel = viewModel.apply {
            setModel(StudyPopupModel(context))
            observeEventForever(this@StudyPopupView::onReceivedEvent)
        }
        viewModel.initList()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.pager.setLoopingAdapter(adapter)
    }

    override fun onSetLayoutParams(windowParams: WindowManager.LayoutParams) {
    }

    @Suppress("UNCHECKED_CAST")
    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            StudyPopupViewModel.EVENT_CLOSE -> {
                detachView()
            }
            StudyPopupViewModel.EVENT_SUBMIT_LIST -> {
                Log.d("EVENT_SUBMIT_LIST")
                adapter.submitList(data as List<StudyPopupData.Contents>)
            }
            StudyPopupViewModel.EVENT_SET_CURRENT_ITEM -> {
                Log.d("EVENT_SET_CURRENT_ITEM : $data, size : ${adapter.itemCount}")
                viewModel.currentItem.set(data as Int)
            }
        }
    }

    override fun onDetachedFromWindow() {
        viewModel.removeEventObserver()
        super.onDetachedFromWindow()
    }
}