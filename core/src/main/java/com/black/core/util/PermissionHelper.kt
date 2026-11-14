package com.black.core.util

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.net.toUri
import com.black.core.R

class PermissionHelper(private val activity: Activity, private val activityResultCaller: ActivityResultCaller) {

    companion object {
        /**
         * [context]에 [permission] 권한이 허용됐는지 확인
         *
         * Activity 없이 확인만 하므로 서비스·리시버·오버레이 뷰에서도 사용. 요청·재요청 안내가 필요하면 [checkPermissions] 사용
         */
        fun isGranted(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * 알림 권한 허용 여부
         *
         * Android 13 미만은 권한 자체가 없어 true
         */
        fun isNotificationPermissionGranted(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        }

        /**
         * 앱 알림 허용 여부
         *
         * Android 13 이상은 알림 권한까지 반영하고, Android 13 미만은 앱 알림 설정에서 끈 경우도 false.
         * 채널 하나만 끈 경우는 반영하지 않으므로 채널 단위 판단은 호출부가 채널 중요도로 확인
         */
        fun areNotificationsEnabled(context: Context): Boolean {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /**
         * 사용 정보 접근 허용 여부
         *
         * 이 권한은 런타임 권한 API가 아니라 AppOps로 확인함.
         * 설정 앱에서 한 번도 바꾸지 않은 기본 모드(`MODE_DEFAULT`)는 시스템 UsageStatsService처럼
         * `PACKAGE_USAGE_STATS` 권한 부여 여부로 판정(adb `pm grant`, 사전 설치 앱 등).
         */
        fun isUsageAccessGranted(context: Context): Boolean {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
                )
            }
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return isGranted(context, Manifest.permission.PACKAGE_USAGE_STATS)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        /** 정확한 위치 권한(`ACCESS_FINE_LOCATION`) 허용 여부 */
        fun isFineLocationGranted(context: Context): Boolean =
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

        /**
         * 대략적인 위치만 허용됐는지 여부
         *
         * API 31 이상은 권한 창과 앱 정보 화면에서 정확한 위치를 끄고 대략적인 위치만 고를 수 있어,
         * 정확한 위치가 필요한 기능은 이 상태를 따로 안내함. API 31 미만은 항상 false.
         */
        fun isApproximateLocationOnly(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) &&
                !isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

        /**
         * 백그라운드 위치 권한(위치 항상 허용) 허용 여부
         *
         * API 29 미만에는 백그라운드 위치 권한이 따로 없어 항상 true.
         */
        fun isBackgroundLocationGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        /**
         * 기기 위치 설정 켜짐 여부
         *
         * 위치 권한이 있어도 이 설정이 꺼져 있으면 Wi-Fi 이름(SSID) 같은 위치 기반 정보를 읽을 수 없음.
         */
        fun isLocationEnabled(context: Context): Boolean {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
            return LocationManagerCompat.isLocationEnabled(locationManager)
        }

        /** 기기 위치 설정 화면 이동 */
        fun showLocationSourceSettings(context: Context) {
            startFirstAvailable(context, listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
        }

        /**
         * 사용 정보 접근 설정 화면 이동
         *
         * 이 앱의 항목 화면을 먼저 시도하고, 열 수 없으면 사용 정보 접근 목록 화면.
         */
        fun showUsageAccessSettings(context: Context) {
            startFirstAvailable(
                context,
                listOf(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri()),
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                ),
            )
        }

        /** 앱 정보 화면 이동 */
        fun showAppDetailsSettings(context: Context) {
            startFirstAvailable(
                context,
                listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())),
            )
        }

        /** [intents]를 앞에서부터 시도해 처음 열리는 화면을 시작하고, 모두 열 수 없으면 로그만 남김 */
        private fun startFirstAvailable(context: Context, intents: List<Intent>) {
            for (intent in intents) {
                if (Util.startActivitySafely(context, intent)) {
                    return
                }
            }
        }

        /**
         * 알림 접근(알림 리스너) 허용 여부
         *
         * 패키지가 아니라 [service] 컴포넌트가 허용 목록에 있는지 확인함.
         */
        fun isNotificationListenerEnabled(context: Context, service: Class<out NotificationListenerService>): Boolean {
            val expectedComponentName = ComponentName(context, service)

            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                ENABLED_NOTIFICATION_LISTENERS,
            ) ?: return false

            return enabledListeners
                .split(":")
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expectedComponentName }
        }

        /**
         * 알림 접근 설정 화면 이동
         *
         * API 30 이상은 [service]의 상세 화면, 그 미만이나 상세 화면이 없으면 알림 접근 목록 화면.
         */
        fun showNotificationListenerSettings(context: Context, service: Class<out NotificationListenerService>) {
            val listIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            val intents = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, service).flattenToString(),
                    )
                listOf(detailIntent, listIntent)
            } else {
                listOf(listIntent)
            }

            startFirstAvailable(context, intents)
        }

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
                Toast.makeText(context, R.string.ignore_battery_optimization_guide, Toast.LENGTH_LONG)
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

        // 알림 접근을 허용한 리스너 컴포넌트 목록의 Settings.Secure 키(공개 상수가 없음)
        private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

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
                isGranted(activity, permission) -> {
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