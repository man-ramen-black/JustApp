package com.black.feature.floatingbutton.ui.floating

import android.Manifest
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.Toast
import com.black.core.model.OverlayPosition
import com.black.core.system.MediaVolumeController
import com.black.core.system.SystemActions
import com.black.core.util.Log
import com.black.core.util.PermissionHelper
import com.black.core.view.MovableOverlayView
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.data.datastore.FloatingDataStore
import com.black.feature.floatingbutton.databinding.ViewFloatingBinding
import kotlinx.coroutines.flow.Flow

class FloatingView : MovableOverlayView<ViewFloatingBinding> {

    private val state by lazy { FloatingViewState(FloatingDataStore(context.applicationContext)) }
    private val mediaVolumeController by lazy { MediaVolumeController(context) }

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

    override val defaultPosition: OverlayPosition get() = FloatingDataStore.DEFAULT_POSITION

    override fun createPositionFlow(): Flow<OverlayPosition> =
        FloatingDataStore(context.applicationContext).getPositionFlow()

    override fun bindVariable(binding: ViewFloatingBinding) {
        super.bindVariable(binding)
        binding.state = state
        binding.floatingView = this
    }

    fun goHome() {
        // 서비스 컨텍스트라 FLAG_ACTIVITY_NEW_TASK는 startActivitySafely가 추가
        SystemActions.goHome(context)
    }

    fun goBack() {
        // 접근성 서비스가 꺼져 있어 요청을 보내지 못하면 설정 안내
        if (!SystemActions.requestBack(context)) {
            Toast.makeText(context, R.string.floating_accessibility_required, Toast.LENGTH_SHORT)
                .show()
            PermissionHelper.openAccessibilityServiceDetailSetting(context)
        }
    }

    fun lowerVolume() {
        mediaVolumeController.lower()
    }

    fun raiseVolume() {
        mediaVolumeController.raise()
    }

    fun openStatusBar() {
        expandStatusBar(context)
    }

    private fun expandStatusBar(context: Context) {
        if (!PermissionHelper.isGranted(context, Manifest.permission.EXPAND_STATUS_BAR)) {
            Log.w("EXPAND_STATUS_BAR permission denied")
            return
        }

        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(context.getSystemService("statusbar"))
        } catch (e: Exception) {
            Log.w(e)
        }
    }
}