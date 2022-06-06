package com.black.code.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import com.black.code.R
import com.black.code.base.component.BaseActivity
import com.black.code.databinding.ActivityMainBinding
import com.black.code.service.ForegroundService
import com.black.code.util.Log

/*
#SplashActivity 가이드
https://lanace.github.io/articles/right-way-on-splash/

#패키지 #구조 (feature -> ui) #package
https://vagabond95.me/posts/android-pakage-structure/
https://infinum.com/handbook/android/project-structure/package-structure
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {
    override val layoutResId: Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱 디버깅 시에 서비스가 실행되도록 설정
        ForegroundService.start(this, false)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        /*
        https://issuetracker.google.com/issues/142847973
        onCreate 시점에는 Fragment가 아직 생성되지 않았기 때문에 findNavController가 null로 반환됨
        onPostCreate에서 findNavController 호출하여 ActionBar 셋팅
         */
        setupActionBarWithNavController(findNavController(R.id.nav_host))
    }

    override fun bindVariable(binding: ActivityMainBinding) {

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // 액션바 백버튼 터치 시 동작
            android.R.id.home -> supportFragmentManager.popBackStack()
        }
        return super.onOptionsItemSelected(item)
    }
}