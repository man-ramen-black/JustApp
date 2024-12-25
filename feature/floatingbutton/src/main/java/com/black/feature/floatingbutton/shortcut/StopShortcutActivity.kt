package com.black.feature.floatingbutton.shortcut

import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.black.core.service.ForegroundService
import com.black.core.service.ServiceActivity.Companion.toStartServiceActivityIntent
import com.black.core.shortcut.ShortcutActivity
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.service.FloatingForegroundService

class StopShortcutActivity: ShortcutActivity() {
    override fun getShortcutInfo(): ShortcutInfoCompat {
        val intent = ForegroundService.createStopIntent(this, FloatingForegroundService::class.java)
            .toStartServiceActivityIntent()
            .apply { action = Intent.ACTION_VIEW }

        return ShortcutInfoCompat.Builder(this, getString(R.string.shortcut_floating_off_id))
            .setShortLabel(getString(R.string.shortcut_floating_off_label))
            .setIcon(IconCompat.createWithResource(this, applicationInfo.icon))
            .setIntent(intent)
            .build()
    }
}