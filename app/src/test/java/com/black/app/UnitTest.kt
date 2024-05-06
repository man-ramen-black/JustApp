package com.black.app

import org.junit.Assert
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class UnitTest {
    @Test
    fun unitTest() {
        val text = "com.black.study.base.BaseActivity".split(".").subList(0, 3).joinToString(".")
        Assert.fail("[$text]")
//        Assert.fail("com.black.study.base.BaseActivity".substringAfterLast("."))
    }
}
