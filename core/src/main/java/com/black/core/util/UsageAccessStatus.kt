package com.black.core.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Context를 들지 않는 ViewModel이 주입받아 쓰는 사용 정보 접근 권한 상태 조회 */
class UsageAccessStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 사용 정보 접근 권한 허용 여부
     *
     * 권한이 없으면 시스템 사용 기록을 읽을 수 없음.
     */
    fun isUsageAccessGranted(): Boolean = PermissionHelper.isUsageAccessGranted(context)
}
