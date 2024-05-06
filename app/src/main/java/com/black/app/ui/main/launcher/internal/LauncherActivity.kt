package com.black.app.ui.main.launcher.internal

import android.appwidget.AppWidgetManager
import android.os.Bundle
import com.black.app.R
import com.black.app.databinding.ActivityLauncherBinding

/**
 * 런처 앱을 위한 가이드나 정보가 매우 적음
 * 마이너 런처 앱의 경우 구글 런처 오픈 소스를 활용하여 개발(com.android.launcher3)
 * 런처 앱을 직접 구현하는 경우 구현해야 할 기능이 매~우 많다
 * 드래그 앤 드롭, 뷰 사이즈 조정, 위젯 추가, 앱 서랍 등등등...
 * 그리고 구글 디스커버를 앱에 탑재시키는 방법은 찾아내지 못 함..
 */
class LauncherActivity : com.black.core.component.BaseActivity<ActivityLauncherBinding>() {
    override val layoutResId: Int = R.layout.activity_launcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, LauncherMainFragment())
            .commit()

        val widgetManager = AppWidgetManager.getInstance(this)
    }

    override fun bindVariable(binding: ActivityLauncherBinding) {
    }
}