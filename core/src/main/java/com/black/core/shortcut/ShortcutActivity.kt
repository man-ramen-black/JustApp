package com.black.core.shortcut

import android.app.Activity
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat

abstract class ShortcutActivity: Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            finish()
            return
        }

        val intent = ShortcutManagerCompat.createShortcutResultIntent(this, getShortcutInfo())
        setResult(RESULT_OK, intent)
        finish()
    }

    /**
    Sample
    val shortcutIntent = Intent(this, MainActivity::class.java).apply {
    action = Intent.ACTION_VIEW
    data = Uri.parse("black://navigate/memo")
    }

    val pinShortcutInfo = ShortcutInfoCompat.Builder(this, getString(R.string.shortcut_memo_id))
    .setShortLabel(getString(R.string.shortcut_memo_label))
    .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
    .setIntent(shortcutIntent)
    .build()
     */
    abstract fun getShortcutInfo(): ShortcutInfoCompat
}