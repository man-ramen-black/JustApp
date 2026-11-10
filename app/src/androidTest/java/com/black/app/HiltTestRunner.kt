package com.black.app

import android.app.Application
import android.app.UiAutomation
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import androidx.test.uiautomator.Configurator
import dagger.hilt.android.testing.HiltTestApplication

/**
 * 계측 테스트에서 Application을 [HiltTestApplication]으로 교체하는 커스텀 러너
 *
 * @AndroidEntryPoint Activity/Fragment를 계측 테스트에서 주입하려면 Application이
 * Hilt 테스트용이어야 하므로, app/build.gradle.kts의 testInstrumentationRunner로 지정한다.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        // UiAutomation 기본 플래그(0)는 연결된 동안 기기의 모든 접근성 서비스를 언바인드해
        // 같은 기기의 실사용 앱·다른 앱의 접근성 서비스까지 계측 내내 끊기므로, UiDevice가 처음 연결하기 전에 억제 해제
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        super.onCreate(arguments)
    }

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
