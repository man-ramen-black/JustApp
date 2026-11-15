package com.black.app.shortcut

import android.content.Intent
import com.black.app.R
import com.black.app.sleeproutine.SleepRoutineService
import com.black.core.shortcut.ServiceShortcutActivity

/**
 * 수면 루틴 시작 바로가기
 *
 * 런처의 위젯·바로가기 목록에서 추가.
 */
class SleepRoutineShortcutActivity : ServiceShortcutActivity() {
    override val shortcutIdRes: Int = R.string.shortcut_sleep_routine_id

    override val shortLabelRes: Int = R.string.shortcut_sleep_routine_label

    override fun createServiceIntent(): Intent = Intent(this, SleepRoutineService::class.java)
}
