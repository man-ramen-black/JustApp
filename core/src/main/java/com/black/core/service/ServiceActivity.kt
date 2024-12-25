package com.black.core.service

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.black.core.util.Log

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val className = intent.extras?.getString(EXTRA_CLASS_NAME)
            ?: run {
                Log.w("className is null")
                finish()
                return
            }

        val serviceIntent = Intent(intent)
            .apply { component = ComponentName(this@ServiceActivity, className) }
        startService(serviceIntent)
        finish()
    }
}