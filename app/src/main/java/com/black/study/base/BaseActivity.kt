package com.black.study.base

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.black.study.util.Log

abstract class BaseActivity<T : ViewDataBinding> : AppCompatActivity() {
    protected lateinit var binding: T
    protected abstract val layoutResId : Int

    /**
     * Activity View Binding : https://developer.android.com/topic/libraries/view-binding?hl=ko#activities
     */
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 자식 클래스 이름 출력을 위해 javaClass.simpleName 출력
        Log.d(javaClass.simpleName)
        binding = DataBindingUtil.setContentView(this, layoutResId)
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onStop() {
        super.onStop()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        Log.d(javaClass.simpleName)
    }

    @CallSuper
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(javaClass.simpleName)
    }

    /**
     * registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult -> }
     * 위와 같이 사용하는 것으로 변경됨
     * https://pluu.github.io/blog/android/2020/05/01/migation-activity-result/
     */
    @CallSuper
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(javaClass.simpleName + "requestCode : $requestCode, resultCode : $resultCode, data : $data")
    }

    @CallSuper
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(javaClass.simpleName + "requestCode : $requestCode, permissions : $permissions, grantResults : $grantResults")
    }
}