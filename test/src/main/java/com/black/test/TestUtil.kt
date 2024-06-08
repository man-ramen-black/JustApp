package com.black.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

object TestUtil {
    fun getTestContext(): Context {
        return InstrumentationRegistry.getInstrumentation().targetContext
    }
}