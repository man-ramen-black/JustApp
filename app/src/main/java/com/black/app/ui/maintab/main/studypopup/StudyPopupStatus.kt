package com.black.app.ui.maintab.main.studypopup

import android.content.Context
import com.black.core.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [StudyPopupSettingScreen]이 보여 주는 팝업 표시 권한 상태 조회 */
class StudyPopupStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 다른 앱 위에 표시 권한 허용 여부 */
    fun canDrawOverlays(): Boolean = PermissionHelper.canDrawOverlays(context)
}
