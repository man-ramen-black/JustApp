package com.black.core.service

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.black.core.util.Log

/**
 * 바로가기·알림 인텐트로 [BaseForegroundService]를 시작하는 투명 Activity
 *
 * 바로가기 인텐트가 이 Activity를 직접 가리키고 API 24·25에서는 런처가 그 인텐트를 자기 권한으로 시작하므로 exported 유지.
 * 외부 앱도 부를 수 있어 시작 대상은 같은 패키지의 [BaseForegroundService] 하위 클래스로 한정하고,
 * 서비스에는 [BaseForegroundService.EXTRA_STOP]만 전달
 */
class ServiceActivity: Activity() {
    companion object {
        private const val EXTRA_CLASS_NAME = "ServiceActivity.EXTRA_CLASS_NAME"

        fun Intent.toStartServiceActivityIntent(): Intent {
            return Intent(this)
                .apply {
                    val component = this@toStartServiceActivityIntent.component
                        ?: return@apply

                    putExtra(EXTRA_CLASS_NAME, component.className)
                    setComponent(ComponentName(component.packageName, ServiceActivity::class.java.name))
                }
        }

        /** [source]가 가리키는 서비스 인텐트 생성(허용되지 않은 대상이면 null) */
        internal fun createServiceIntent(context: Context, source: Intent): Intent? {
            val className = source.getStringExtra(EXTRA_CLASS_NAME)
                ?: run {
                    Log.w("className is null")
                    return null
                }

            if (!isForegroundServiceClass(context, className)) {
                Log.w("not allowed service : $className")
                return null
            }

            return Intent()
                .apply {
                    component = ComponentName(context, className)
                    if (source.getBooleanExtra(BaseForegroundService.EXTRA_STOP, false)) {
                        putExtra(BaseForegroundService.EXTRA_STOP, true)
                    }
                }
        }

        /** 외부에서 받은 이름이라 클래스 초기화(static 블록 실행) 없이 타입만 확인 */
        private fun isForegroundServiceClass(context: Context, className: String): Boolean {
            val serviceClass = try {
                Class.forName(className, false, context.classLoader)
            } catch (e: ClassNotFoundException) {
                return false
            }
            return BaseForegroundService::class.java.isAssignableFrom(serviceClass)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createServiceIntent(this, intent)
            ?.let { startService(it) }
        finish()
    }
}
