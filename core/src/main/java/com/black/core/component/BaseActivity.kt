package com.black.core.component

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.black.core.util.Log

abstract class BaseActivity<T : ViewDataBinding> : AppCompatActivity() {
    protected lateinit var binding: T
    protected abstract val layoutResId : Int

    abstract fun bindVariable(binding: T)

    /**
     * Activity View Binding : https://developer.android.com/topic/libraries/view-binding?hl=ko#activities
     */
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 자식 클래스 이름 출력을 위해 javaClass.simpleName 출력
        Log.d(javaClass.simpleName)
        binding = DataBindingUtil.setContentView<T>(this, layoutResId).apply {
            lifecycleOwner = this@BaseActivity
        }
        bindVariable(binding)

        if (savedInstanceState == null) {
            onReceivedIntent(intent ?: return)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        Log.d(javaClass.simpleName)
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

    /**
     * 현재 액티비티가 최상위가 아닌 경우 호출
     * ex. 반투명(또는 투명) 액티비티가 위에 노출된 경우 onPause만 호출 (그 외 멀티 윈도우 등등)
     */
    @CallSuper
    override fun onPause() {
        super.onPause()
        Log.d(javaClass.simpleName)
    }

    /**
     * 현재 액티비티가 숨겨진 경우 호출
     * ex. 반투명(또는 투명) 액티비티가 위에 노출된 경우 onPause만 호출 (그 외 멀티 윈도우 등등)
     */
    @CallSuper
    override fun onStop() {
        super.onStop()
        Log.d(javaClass.simpleName)
    }

    /**
     * targetSdkVersion : Build.VERSION_CODES.P 이상에서는 onStop() 이후에 호출
     * targetSdkVersion : Build.VERSION_CODES.P 미만인 경우 onStop() 이전에 발생하며 onPause() 이전 또는 이후에 발생할지 여부에 대한 보장은 없습니다.
     */
    // https://developer.android.com/reference/android/app/Activity#onSaveInstanceState(android.os.Bundle)
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
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
        Log.d("${javaClass.simpleName} requestCode : $requestCode, resultCode : $resultCode, data : $data")
    }

    @CallSuper
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("${javaClass.simpleName} : requestCode : $requestCode, permissions : $permissions, grantResults : $grantResults")
    }

    /**
     * launchMode가 singleTop, singleTask인 경우,
     * Intent.FLAG_ACTIVITY_SINGLE_TOP로 Activity를 실행한 경우
     * onPause -> onNewIntent -> onResume 순서로 실행됨
     * https://developer.android.com/reference/android/app/Activity#onNewIntent(android.content.Intent)
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("${javaClass.simpleName} : intent : $intent")
        onReceivedIntent(intent)
    }

    /** onCreate, onNewIntent 인텐트 공통 처리용 콜백 */
    protected open fun onReceivedIntent(intent: Intent) { }
}