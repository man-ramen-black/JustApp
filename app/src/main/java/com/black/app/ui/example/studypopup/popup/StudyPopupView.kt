package com.black.app.ui.example.studypopup.popup

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import com.black.app.R
import com.black.app.base.view.OverlayView
import com.black.app.base.viewmodel.EventObserver
import com.black.app.databinding.ViewStudyPopupBinding
import com.black.app.model.StudyPopupModel
import com.black.app.model.database.studypopup.StudyPopupData
import com.black.app.util.Log

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
        binding.adapter = adapter
        viewModel.initList()
    }

    override fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams) {
        windowParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            windowAnimations = android.R.style.Animation_Dialog
        }
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
            StudyPopupViewModel.EVENT_TOAST -> {
                Toast.makeText(context, data as String, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDetachedFromWindow() {
        viewModel.removeEventObserver()
        super.onDetachedFromWindow()
    }
}