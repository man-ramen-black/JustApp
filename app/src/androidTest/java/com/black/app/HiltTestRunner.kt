package com.black.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * 계측 테스트에서 Application을 [HiltTestApplication]으로 교체하는 커스텀 러너
 *
 * @AndroidEntryPoint Activity/Fragment를 계측 테스트에서 주입하려면 Application이
 * Hilt 테스트용이어야 하므로, app/build.gradle.kts의 testInstrumentationRunner로 지정한다.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
