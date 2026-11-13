package com.black.core.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Context를 들지 않는 ViewModel이 주입받아 쓰는 다른 앱 위에 표시 권한 상태 조회 */
class OverlayPermissionStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 다른 앱 위에 표시 권한 허용 여부(없이 오버레이 창을 붙이면 실패) */
    fun canDrawOverlays(): Boolean = PermissionHelper.canDrawOverlays(context)
}
