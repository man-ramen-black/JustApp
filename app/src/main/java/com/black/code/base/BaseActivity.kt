package com.black.code.base

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.black.code.util.Log

abstract class BaseActivity<T : ViewDataBinding> : AppCompatActivity() {
    protected lateinit var binding: T
    protected abstract val layoutResId : Int
    protected lateinit var activityLauncher : ActivityResultLauncher<Intent>

    /**
     * Activity View Binding : https://developer.android.com/topic/libraries/view-binding?hl=ko#activities
     */
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 자식 클래스 이름 출력을 위해 javaClass.simpleName 출력
        Log.d(javaClass.simpleName)
        binding = DataBindingUtil.setContentView(this, layoutResId)
        activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d("onActivityResult : resultCode : ${it.resultCode}, data : ${it.data}")
            onActivityResult(it.resultCode, it.data)
        }
    }

    open fun onActivityResult(resultCode: Int, data: Intent?) {
    }

    protected fun launchActivity(intent : Intent) {
        activityLauncher.launch(intent)
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("${javaClass.simpleName} : intent : $intent")
    }
}