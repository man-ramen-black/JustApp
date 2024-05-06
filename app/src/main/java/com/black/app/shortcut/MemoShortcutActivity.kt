package com.black.app.shortcut

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.black.app.R
import com.black.app.ui.MainActivity
import com.black.app.ui.example.texteditor.TextEditorFragment

/**
 * https://m.blog.naver.com/PostView.naver?isHttpsRedirect=true&blogId=kdr0923&logNo=50087667730
 * Created by jinhyuk.lee on 2022/11/08
 **/
class MemoShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("black://navigate/${TextEditorFragment::class.java.simpleName.replace("Fragment", "")}")
            }

            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, getString(R.string.shortcut_memo_id))
                .setShortLabel(getString(R.string.shortcut_memo_label))
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(shortcutIntent)
                .build()

            val intent = ShortcutManagerCompat.createShortcutResultIntent(this, pinShortcutInfo)
            setResult(RESULT_OK, intent)
        }
        finish()
    }
}