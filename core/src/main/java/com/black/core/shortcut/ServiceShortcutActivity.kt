package com.black.core.shortcut

import android.content.Intent
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.black.core.service.BaseForegroundService
import com.black.core.service.ServiceActivity
import com.black.core.service.ServiceActivity.Companion.toStartServiceActivityIntent

/**
 * [BaseForegroundService]를 [ServiceActivity]로 시작·중지하는 고정 바로가기(앱 아이콘)를 만드는 Activity
 *
 * 바로가기마다 매니페스트 항목이 따로 필요해 하위 클래스는 서비스 인텐트와 바로가기 ID·이름만 지정
 */
abstract class ServiceShortcutActivity : ShortcutActivity() {
    companion object {
        /** [serviceIntent]를 [ServiceActivity]로 전달하는 바로가기 인텐트(바로가기 인텐트는 action이 필수라 ACTION_VIEW 지정) */
        @VisibleForTesting
        internal fun createShortcutIntent(serviceIntent: Intent): Intent =
            serviceIntent.toStartServiceActivityIntent()
                .apply { action = Intent.ACTION_VIEW }
    }

    @get:StringRes
    protected abstract val shortcutIdRes: Int

    @get:StringRes
    protected abstract val shortLabelRes: Int

    /** 바로가기가 전달할 서비스 인텐트(중지는 [BaseForegroundService.createStopIntent]) */
    protected abstract fun createServiceIntent(): Intent

    override fun getShortcutInfo(): ShortcutInfoCompat {
        return ShortcutInfoCompat.Builder(this, getString(shortcutIdRes))
            .setShortLabel(getString(shortLabelRes))
            .setIcon(IconCompat.createWithResource(this, applicationInfo.icon))
            .setIntent(createShortcutIntent(createServiceIntent()))
            .build()
    }
}
