package com.black.feature.floatingbutton.ui.floating

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.media.AudioManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.black.core.util.Log
import com.black.core.util.PermissionHelper
import com.black.core.view.MovableOverlayView
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.data.datastore.FloatingDataStore
import com.black.feature.floatingbutton.databinding.ViewFloatingBinding
import com.black.feature.floatingbutton.service.FloatingAccessibilityService

class FloatingView : MovableOverlayView<ViewFloatingBinding> {

    private val state by lazy { FloatingViewState(FloatingDataStore(context.applicationContext)) }

    override val layoutId: Int get() = R.layout.view_floating
    override val styleableId: IntArray? get() = null

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

    /** 첫 표시 위치: 화면 왼쪽 아래 */
    @SuppressLint("RtlHardcoded")
    override fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams) {
        windowParams.gravity = Gravity.LEFT or Gravity.BOTTOM
        windowParams.y = 70
    }

    override fun bindVariable(binding: ViewFloatingBinding) {
        super.bindVariable(binding)
        binding.state = state
        binding.floatingView = this
    }

    fun goHome() {
        Intent(Intent.ACTION_MAIN)
            .apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            .also { context.startActivity(it) }
    }

    fun goBack() {
        if (!PermissionHelper.isAccessibilityServiceEnabled(context, FloatingAccessibilityService::class.java)) {
            Toast.makeText(context, "접근성 설정이 필요합니다.", Toast.LENGTH_SHORT)
                .show()
            PermissionHelper.openAccessibilitySetting(context)
            return
        }

        context.sendBroadcast(FloatingAccessibilityService.createRequestBackIntent(context))
    }

    fun lowerVolume() {
        adjustMusicVolume(AudioManager.ADJUST_LOWER)
    }

    fun raiseVolume() {
        adjustMusicVolume(AudioManager.ADJUST_RAISE)
    }

    fun openStatusBar() {
        expandStatusBar(context)
    }

    private fun adjustMusicVolume(direction: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
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