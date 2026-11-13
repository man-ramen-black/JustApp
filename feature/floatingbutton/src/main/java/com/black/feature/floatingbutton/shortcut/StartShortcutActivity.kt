package com.black.feature.floatingbutton.shortcut

import android.content.Intent
import com.black.core.shortcut.ServiceShortcutActivity
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.service.FloatingForegroundService

class StartShortcutActivity: ServiceShortcutActivity() {
    override val shortcutIdRes: Int = R.string.shortcut_floating_on_id

    override val shortLabelRes: Int = R.string.shortcut_floating_on_label

    override fun createServiceIntent(): Intent = Intent(this, FloatingForegroundService::class.java)
}
