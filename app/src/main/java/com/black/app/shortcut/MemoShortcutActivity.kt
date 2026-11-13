package com.black.app.shortcut

import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.black.app.R
import com.black.app.deeplink.Deeplink
import com.black.app.ui.MainActivity
import com.black.core.shortcut.ShortcutActivity

/**
 * https://m.blog.naver.com/PostView.naver?isHttpsRedirect=true&blogId=kdr0923&logNo=50087667730
 * Created by jinhyuk.lee on 2022/11/08
 **/
class MemoShortcutActivity : ShortcutActivity() {
    override fun getShortcutInfo(): ShortcutInfoCompat {
        val shortcutIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.Builder()
                .scheme(Deeplink.Scheme.APP)
                .authority(Deeplink.Host.NAVIGATE)
                .path(Deeplink.PathNavigate.MEMO)
                .build()
        }

        return ShortcutInfoCompat.Builder(this, getString(R.string.shortcut_memo_id))
            .setShortLabel(getString(R.string.shortcut_memo_label))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent)
            .build()
    }
}
