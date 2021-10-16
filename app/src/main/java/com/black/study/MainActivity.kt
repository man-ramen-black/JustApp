package com.black.study

import android.os.Bundle
import com.black.study.base.BaseActivity
import com.black.study.contents.ContentsListFragment
import com.black.study.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {
    override val layoutResId: Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, ContentsListFragment())
            .commit()
    }
}