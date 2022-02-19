package com.black.code.util

import android.content.Context

object Util {
    fun pxToDp(context: Context, px: Int) : Float {
        return px.toFloat() / context.resources.displayMetrics.density
    }
}