package com.black.feature.floatingbutton.ui.floating

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.media.AudioManager
import android.util.AttributeSet
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.black.core.di.Hilt
import com.black.core.util.Log
import com.black.core.view.MovableOverlayView
import com.black.core.viewmodel.EventCollector
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.databinding.ViewFloatingBinding
import com.black.feature.floatingbutton.service.FloatingAccessibilityService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class FloatingView : MovableOverlayView<ViewFloatingBinding>, EventCollector {

    private val viewModel by lazy {
        Hilt.fromApplication(context, FloatingViewModel.EntryPoint::class.java)
            .floatingViewModel
    }

    private val mainScope = MainScope()

    override val layoutId: Int get() = R.layout.view_floating
    override val styleableId: IntArray? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun initialize(binding: ViewFloatingBinding, typedArray: TypedArray?) {
        isMovable = true
    }

    override fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams) {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.viewModel = viewModel
        viewModel.collectEvent(mainScope, this)
    }

    override suspend fun onEventCollected(action: String, data: Any?) {
        Log.d(action)
        when (action) {
            FloatingViewModel.EVENT_HOME -> {
                Intent(Intent.ACTION_MAIN)
                    .apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    .also { context.startActivity(it) }
            }

            FloatingViewModel.EVENT_BACK -> {
                context.sendBroadcast(Intent(FloatingAccessibilityService.ACTION_REQUEST_BACK))
            }

            FloatingViewModel.EVENT_VOLUME_DOWN -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            }

            FloatingViewModel.EVENT_VOLUME_UP -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
            }

            FloatingViewModel.EVENT_STATUS_BAR -> {
                expandStatusBar(context)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainScope.cancel()
    }

    private fun expandStatusBar(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.EXPAND_STATUS_BAR
            ) != PackageManager.PERMISSION_GRANTED) {
            Log.w("EXPAND_STATUS_BAR permission denied")
            return
        }

        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(context.getSystemService("statusbar"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}