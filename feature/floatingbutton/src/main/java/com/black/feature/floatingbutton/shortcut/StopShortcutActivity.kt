package com.black.feature.floatingbutton.shortcut

import android.content.Intent
import com.black.core.service.BaseForegroundService
import com.black.core.shortcut.ServiceShortcutActivity
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.service.FloatingForegroundService

class StopShortcutActivity: ServiceShortcutActivity() {
    override val shortcutIdRes: Int = R.string.shortcut_floating_off_id

    override val shortLabelRes: Int = R.string.shortcut_floating_off_label

    override fun createServiceIntent(): Intent =
        BaseForegroundService.createStopIntent(this, FloatingForegroundService::class.java)
}
