package com.black.test

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.robolectric.Shadows.shadowOf

object TestUtil {
    fun getTestContext(): Context {
        return InstrumentationRegistry.getInstrumentation().targetContext
    }

    /** 런처 액티비티 없이 라벨·아이콘만 가진 앱을 Robolectric 패키지 관리자에 설치 */
    fun installApp(context: Context, packageName: String, label: String) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    nonLocalizedLabel = label
                }
            }
        )
        setAppIcon(context, packageName)
    }

    /**
     * 이미 있는 [packageName] 앱에 아이콘 지정
     *
     * 패키지 정보를 다시 설치하지 않으므로 자기 앱처럼 덮어쓰면 안 되는 패키지에도 사용
     */
    fun setAppIcon(context: Context, packageName: String) {
        // Robolectric은 아이콘 미지정 시 null 반환(실기기는 기본 아이콘 반환)
        shadowOf(context.packageManager).setApplicationIcon(packageName, ColorDrawable(Color.RED))
    }

    /**
     * [packageName] 앱에 MAIN·LAUNCHER 인텐트 필터를 가진 [activityName] 액티비티 추가
     *
     * 액티비티가 이미 있으면 인텐트 필터만 추가함.
     * 기능 모듈 테스트처럼 매니페스트에 앱의 시작 액티비티가 없을 때 `getLaunchIntentForPackage`가 돌려줄 액티비티를 만드는 데 씀.
     */
    fun addLauncherActivity(context: Context, packageName: String, activityName: String) {
        val shadowPackageManager = shadowOf(context.packageManager)
        val component = ComponentName(packageName, activityName)
        shadowPackageManager.addActivityIfNotPresent(component)
        shadowPackageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        )
    }
}
