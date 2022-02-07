package com.black.code

import android.os.Bundle
import com.black.code.base.BaseActivity
import com.black.code.contents.ContentsListFragment
import com.black.code.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {
    override val layoutResId: Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, ContentsListFragment())
            .commit()
    }
}