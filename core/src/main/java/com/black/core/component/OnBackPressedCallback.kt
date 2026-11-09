package com.black.core.component

/**
 * [androidx.activity.OnBackPressedCallback]
 **/
open class OnBackPressedCallback(enabled: Boolean = true, private val onBackPressed: OnBackPressedCallback.() -> Unit)
    : androidx.activity.OnBackPressedCallback(enabled)
{
    constructor(onBackPressed: OnBackPressedCallback.() -> Unit) : this(true, onBackPressed)

    override fun handleOnBackPressed() {
        onBackPressed(this)
    }
}