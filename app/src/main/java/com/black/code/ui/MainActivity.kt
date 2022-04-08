package com.black.code.ui

import android.os.Bundle
import com.black.code.R
import com.black.code.base.component.BaseActivity
import com.black.code.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {
    override val layoutResId: Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id,
                com.black.code.ui.example.ExampleListFragment()
            )
            .commit()
    }

    override fun bindVariable(binding: ActivityMainBinding) {

    }
}