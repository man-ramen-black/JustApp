package com.black.core.util

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
            return Settings.canDrawOverlays(context)
        }

        fun getDrawOverlaysSettingIntent(context: Context) : Intent {
            return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        }

        fun showDrawOverlaysSetting(context: Context) {
            Util.startActivitySafely(context, getDrawOverlaysSettingIntent(context))
        }

        // https://blog.kmshack.kr/ignore_battery_optimizations/
        // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS를 사용하면
        // dialog에서 바로 배터리 최적화 해제가 가능하지만, 구글 스토어에서 반려
        fun showIgnoreBatteryOptimizationSettings(context: Context, withToast: Boolean = false) {
            if (withToast) {
                Toast.makeText(context, "배터리 최적화를 해제해주세요.", Toast.LENGTH_LONG)
                    .show()
            }

            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            // 동작하지 않음
            // intent.data = Uri.parse("package:" + context.packageName)

            Util.startActivitySafely(context, intent)
        }

        fun isIgnoringBatteryOptimizations(context: Context) : Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        /** 앱 알림 설정 화면 이동 */
        fun showAppNotificationSettings(context: Context) {
            // ACTION_APP_NOTIFICATION_SETTINGS는 API 26 추가, 그 미만은 해당 화면이 없어 앱 정보 화면으로 대체
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            }

            Util.startActivitySafely(context, intent)
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
            Util.startActivitySafely(context, intent)
        }

        /** 특정 접근성 서비스 상세 설정 화면 표시(차단·미지원 시 접근성 설정 목록에서 해당 서비스 하이라이트) */
        fun openAccessibilityServiceDetailSetting(context: Context, service: Class<out AccessibilityService>) {
            val componentName = ComponentName(context, service).flattenToString()

            // Settings에 공개 상수가 없는 액션, Android 16부터 시스템 권한이 필요해 SecurityException 발생
            val detailIntent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .apply {
                    putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            if (Util.startActivitySafely(context, detailIntent)) {
                return
            }

            // 폴백: 접근성 설정 목록을 열고 해당 서비스 항목 하이라이트(미지원 설정 앱은 단순 목록 표시)
            val highlightIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .apply {
                    putExtra(EXTRA_FRAGMENT_ARG_KEY, componentName)
                    putExtra(
                        EXTRA_SHOW_FRAGMENT_ARGUMENTS,
                        Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, componentName) },
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            if (Util.startActivitySafely(context, highlightIntent)) {
                return
            }

            // 마지막 폴백: extras 없는 접근성 설정 목록
            openAccessibilitySetting(context)
        }

        // 설정 앱이 특정 항목을 스크롤·하이라이트할 때 사용하는 비공개 extras 키
        private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
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