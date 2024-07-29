package com.black.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.black.app.R
import com.black.app.databinding.ActivityMainBinding
import com.black.app.deeplink.DeeplinkManager
import com.black.app.service.ForegroundService
import com.black.core.component.BaseSplashActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
#SplashActivity 가이드
https://lanace.github.io/articles/right-way-on-splash/

#패키지 #구조 (feature -> ui) #package
https://vagabond95.me/posts/android-pakage-structure/
https://infinum.com/handbook/android/project-structure/package-structure
 */
@AndroidEntryPoint
class MainActivity : BaseSplashActivity<ActivityMainBinding>() {

    @Inject lateinit var deeplinkManager: DeeplinkManager

    override val layoutResId: Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 디버깅 시에 서비스가 실행되도록 설정
        ForegroundService.start(this, false)
    }

    override fun onReceivedIntent(intent: Intent) {
        super.onReceivedIntent(intent)
        lifecycleScope.launch {
            deeplinkManager.receiveDeeplink(intent.data)
        }
    }

    override fun bindVariable(binding: ActivityMainBinding) {}

    override fun onSplashStart(splash: Splash) {
        doSomething {
            splash.hide()
        }
    }

    override fun onSplashCanceled() {
        cancelDoSomething()
    }

    private fun doSomething(callback: () -> Unit) {
        lifecycleScope.launch(Dispatchers.Main) {
            delay(500)
            callback()
        }
    }

    private fun cancelDoSomething() {
    }
}