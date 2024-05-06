package com.black.app.view

import android.content.Context
import android.content.res.TypedArray
import android.os.CountDownTimer
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.databinding.BindingAdapter
import com.black.app.R
import com.black.core.util.Log
import com.black.core.util.Util

/**
 * Created by jinhyuk.lee on 2022/05/21
 **/
class BKCountDownTimer: AppCompatTextView {

    companion object {
        @BindingAdapter("app:millisInFuture")
        @JvmStatic
        fun setMillisInFuture(view: BKCountDownTimer, millisInFuture: Long) {
            view.millisInFuture = millisInFuture
            if (millisInFuture == 0L) {
                view.stop()
            } else {
                view.start()
            }
        }

        @BindingAdapter("app:onFinish")
        @JvmStatic
        fun setOnFinishListener(view: BKCountDownTimer, onFinishListener: OnFinishListener) {
            view.onFinishListener = onFinishListener
        }
    }

    constructor(context: Context) : super(context) {
        initializeInternal(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initializeInternal(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initializeInternal(attrs)
    }

    private fun initializeInternal(attrs: AttributeSet?) {
        val typedArray = let {
            context.obtainStyledAttributes(
                attrs ?: return@let null,
                R.styleable.BKCountDownTimer
            )
        }

        initialize(typedArray)
        typedArray?.recycle()
    }

    private fun initialize(typedArray: TypedArray?) {
    }

    var format : String = "mm:ss"
    var millisInFuture : Long = 0
    private var timer: CountDownTimer? = null

    var onTickListener: OnTickListener? = null
    var onFinishListener: OnFinishListener? = null

    fun start() {
        stop()
        Log.d("millisInFuture : $millisInFuture")
        timer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                text = Util.milliSecondsToTimeString(format, millisUntilFinished)
                onTickListener?.onTick(this@BKCountDownTimer)
            }
            override fun onFinish() {
                onFinishListener?.onFinished(this@BKCountDownTimer)
            }
        }.start()
    }

    fun stop() {
        text = Util.milliSecondsToTimeString(format, millisInFuture)
        pause()
        timer = null
    }

    fun pause() {
        timer?.cancel()
    }

    interface OnTickListener {
        fun onTick(countDownTimer: BKCountDownTimer)
    }

    interface OnFinishListener {
        fun onFinished(countDownTimer: BKCountDownTimer)
    }
}