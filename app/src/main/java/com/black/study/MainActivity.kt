package com.black.study

import android.os.Bundle
import android.view.LayoutInflater
import androidx.databinding.DataBindingUtil
import com.black.study.base.BaseActivity
import com.black.study.base.ListFragment
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