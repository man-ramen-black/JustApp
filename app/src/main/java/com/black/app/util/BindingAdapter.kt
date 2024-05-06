package com.black.app.util

import androidx.databinding.BindingAdapter
import com.black.app.view.BKCountDownTimer

/** BKCountDownTimer */
object BKCountDownTimerBindingAdapter {
    @BindingAdapter("millisInFuture")
    @JvmStatic
    fun setMillisInFuture(view: BKCountDownTimer, millisInFuture: Long) {
        view.millisInFuture = millisInFuture
        if (millisInFuture == 0L) {
            view.stop()
        } else {
            view.start()
        }
    }

    @BindingAdapter("onFinish")
    @JvmStatic
    fun setOnFinishListener(view: BKCountDownTimer, onFinishListener: BKCountDownTimer.OnFinishListener) {
        view.onFinishListener = onFinishListener
    }
}