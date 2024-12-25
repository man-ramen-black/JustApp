package com.black.core.util

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
                ?: return

            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
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
        fun showIgnoreBatteryOptimizationSettings(context: Context, withToast: Boolean = false) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Log.i("API Level < 23")
                return
            }

            if (withToast) {
                Toast.makeText(context, "배터리 최적화를 해제해주세요.", Toast.LENGTH_LONG)
                    .show()
            }

            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .apply {
                    if (context !is Activity) {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    // 동작하지 않음
                    // data = Uri.parse("package:" + context.packageName)
                }

            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }

        fun isIgnoringBatteryOptimizations(context: Context) : Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return true
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        /** 접근성 권한 있는지 체크 */
        fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
            val expectedComponentName = ComponentName(context, service)

            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServicesSetting
                .split(":")
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expectedComponentName }
        }

        fun openAccessibilitySetting(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
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
            onRequestPermissionResult?.invoke(PermissionResult(deniedPermissions.isEmpty(), grantedPermissions, deniedPermissions))
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
        return PermissionResult(permissions.size == grantedPermissions.size, grantedPermissions, deniedPermissions, retryPermissions)
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
    data class PermissionResult(val isGranted: Boolean, val grantedPermissions: List<String>, val deniedPermissions: List<String>, val retryPermissions: List<String> = emptyList())
}