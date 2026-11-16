package com.black.core.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Context를 들지 않는 ViewModel이 주입받아 쓰는 앱 접근성 서비스 켜짐 여부 조회 */
class AccessibilityServiceStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 앱 접근성 서비스 켜짐 여부
     *
     * core는 앱 모듈의 서비스 클래스를 모르므로 매니페스트에 선언된 서비스를 기준으로 확인
     */
    fun isAccessibilityServiceEnabled(): Boolean = PermissionHelper.isAccessibilityServiceEnabled(context)
}
