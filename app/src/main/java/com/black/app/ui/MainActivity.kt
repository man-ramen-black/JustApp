package com.black.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import androidx.collection.valueIterator
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.black.app.R
import com.black.app.databinding.ActivityMainBinding
import com.black.app.service.ForegroundService
import com.black.core.component.BaseSplashActivity
import com.black.core.util.Log
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

/*
#SplashActivity 가이드
https://lanace.github.io/articles/right-way-on-splash/

#패키지 #구조 (feature -> ui) #package
https://vagabond95.me/posts/android-pakage-structure/
https://infinum.com/handbook/android/project-structure/package-structure
 */
@AndroidEntryPoint
class MainActivity : BaseSplashActivity<ActivityMainBinding>() {

    private var navController : NavController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val navControllerQueue = LinkedList<Runnable>()
    override val layoutResId: Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 디버깅 시에 서비스가 실행되도록 설정
        ForegroundService.start(this, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data?.host == "navigate") {
            navControllerQueue.push {
                val path = intent.data?.pathSegments?.getOrNull(0)
                Log.d("path : $path")
                navController?.run {
                    for (node in graph.nodes.valueIterator()) {
                        Log.d("label : ${node.label}")
                        if (currentDestination?.id != node.id && // 이동하려는 화면이 이미 노출 중인지 체크
                            node.label?.contentEquals(path, true) == true) {
                            Log.d("navigate!")
                            navigate(node.id)
                            break
                        }
                    }
                }
            }
        }

        if (navController != null) {
            while (navControllerQueue.isNotEmpty()) {
                navControllerQueue.pop().run()
            }
        }
    }

    override fun bindVariable(binding: ActivityMainBinding) {

    }

    override fun onSplashStart(splash: Splash) {
        doSomething {
            splash.hide()
        }
    }

    override fun onSplashCanceled() {
        cancelDoSomething()
    }

    private fun doSomething(callback: () -> Unit) {
        handler.postDelayed({
            callback()
        }, 1000)
    }

    private fun cancelDoSomething() {
        handler.removeCallbacksAndMessages(null)
    }
}