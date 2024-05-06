package com.black.app.view

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.widget.Chronometer

class BKChronometer : Chronometer{

    var isRunning : Boolean = false
        private set

    private var pauseTime = 0L

    constructor(context: Context?) : super(context) {
        initialize(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initialize(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initialize(attrs)
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(attrs)
    }

    private fun initialize(attrs: AttributeSet?) {}

    override fun start() {
        isRunning = true
        base = SystemClock.elapsedRealtime() - pauseTime
        super.start()
        pauseTime = 0
    }

    override fun stop() {
        super.stop()
        base = SystemClock.elapsedRealtime()
        pauseTime = 0
    }

    fun pause() {
        super.stop()
        pauseTime = SystemClock.elapsedRealtime() - base
    }
}