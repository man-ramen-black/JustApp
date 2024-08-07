package com.black.app.util

import androidx.appcompat.widget.Toolbar
import androidx.databinding.BindingAdapter
import androidx.navigation.NavController
import com.black.app.view.BKCountDownTimer
import com.black.core.util.Log

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

object ToolbarBindingAdapter {
    @BindingAdapter(value = ["navController", "onClickNavigation", "autoTitle"], requireAll = false)
    @JvmStatic
    fun setNavController(
        view: Toolbar,
        navController: NavController?,
        onClickNavigation: ((isRootDestination: Boolean) -> Any)?,
        isAutoTitle: Boolean?
    ) {
        val label = navController?.currentDestination?.label
        if (isAutoTitle != false && label != null) {
            view.title = label
        }

        val onClick = onClickNavigation
            ?: fun(isRootDestination: Boolean) {
                if (!isRootDestination) {
                    navController?.popBackStack()
                }
            }

        view.setNavigationOnClickListener {
            val isRootDestination = navController?.previousBackStackEntry == null
            onClick(isRootDestination)
        }
    }
}