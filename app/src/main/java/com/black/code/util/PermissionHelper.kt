package com.black.code.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class PermissionHelper(private val activity: Activity, private val activityResultCaller: ActivityResultCaller) {

    companion object {
        fun canDrawOverlays(context: Context) : Boolean {
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                true
            } else {
                Settings.canDrawOverlays(context)
            }
        }

        private fun getDrawOverlaysSettingIntent(context: Context) : Intent? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Log.w("Build.VERSION.SDK_INT < Build.VERSION_CODES.M")
                return null
            }
            return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        }

        fun showDrawOverlaysSetting(context: Context) {
            val intent = getDrawOverlaysSettingIntent(context)
                ?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ?: return
            context.startActivity(intent)
        }

        fun requestDrawOverlaysPermission(activity: ComponentActivity, onResult: (Boolean) -> Unit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                onResult(true)
                return
            }
            val activityLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                onResult(canDrawOverlays(activity))
            }
            val intent = getDrawOverlaysSettingIntent(activity)
                ?: return
            activityLauncher.launch(intent)
        }

        // https://blog.kmshack.kr/ignore_battery_optimizations/
        // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS를 사용하면
        // dialog에서 바로 배터리 최적화 해제가 가능하지만, 구글 스토어에서 반려
        fun ignoreBatteryOptimization() {

        }

        fun isIgnoredBatteryOptimization() : Boolean {
            return true
        }
    }

    private lateinit var launcher : ActivityResultLauncher<Array<String>>
    private var onRequestPermissionResult : ((PermissionResult) -> Unit)? = null

    constructor(activity: AppCompatActivity) : this(activity, activity)

    fun init() {
        launcher = activityResultCaller.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val grantedPermissions = ArrayList<String>()
            val deniedPermissions = ArrayList<String>()
            for((permission, isGranted) in it) {
                if (isGranted) {
                    grantedPermissions.add(permission)
                } else {
                    deniedPermissions.add(permission)
                }
            }
            onRequestPermissionResult?.invoke(PermissionResult(grantedPermissions, deniedPermissions))
            onRequestPermissionResult = null
        }
    }

    fun checkPermissions(permissions : Array<String>) : PermissionResult {
        val grantedPermissions = ArrayList<String>()
        val deniedPermissions = ArrayList<String>()
        val retryPermissions = ArrayList<String>()

        for (permission in permissions) {
            when {
                ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED -> {
                    grantedPermissions.add(permission)
                }

                // 사용자가 권한을 거부한 경우 true, 권한 최초 요청, 다시 묻지 않음, 권한 허용한 경우 false
                // ex) "x 기능 이용을 위해 x 권한 허용이 필요합니다." Dialog 노출
                // https://greedy0110.tistory.com/59
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
                    retryPermissions.add(permission)
                }

                // 권한을 아직 허용하지 않은 경우 or 다시 묻지 않음 체크 후 거부한 경우
                else -> {
                    deniedPermissions.add(permission)
                }
            }
        }
        return PermissionResult(grantedPermissions, deniedPermissions, retryPermissions)
    }

    fun requestPermissions(permissions: Array<String>, onRequestPermissionResult: ((PermissionResult) -> Unit)?) {
        this.onRequestPermissionResult = onRequestPermissionResult
        launcher.launch(permissions)
    }

    /**
     * @param grantedPermissions 허용된 권한 리스트
     * @param deniedPermissions 권한을 아직 허용하지 않았거나, 다시 묻지 않음을 체크하지 않고 거부한 권한 리스트
     * @param retryPermissions 사용자가 거부한 권한 리스트 (재허용 가능)
     */
    data class PermissionResult(val grantedPermissions: List<String>, val deniedPermissions: List<String>, val retryPermissions: List<String> = emptyList())
}