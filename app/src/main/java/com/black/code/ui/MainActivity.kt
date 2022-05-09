package com.black.code.ui

import android.os.Bundle
import android.view.MenuItem
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

        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id,
                com.black.code.ui.example.ExampleListFragment()
            )
            .commit()

        // 프래그먼트 이동 시 액션바 백버튼 활성화
        supportFragmentManager.addOnBackStackChangedListener {
            Log.d("backStackEntry : ${supportFragmentManager.backStackEntryCount}")
            val actionBarBackButtonEnable = supportFragmentManager.backStackEntryCount != 0
            supportActionBar?.setDisplayHomeAsUpEnabled(actionBarBackButtonEnable)
        }

        // 앱 디버깅 시에 서비스가 실행되도록 설정
        ForegroundService.start(this, false)
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