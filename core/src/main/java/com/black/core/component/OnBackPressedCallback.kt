package com.black.core.component

import androidx.activity.OnBackPressedDispatcher

/**
 * [androidx.activity.OnBackPressedCallback]
 **/
open class OnBackPressedCallback(enabled: Boolean = true, private val onBackPressed: OnBackPressedCallback.() -> Unit)
    : androidx.activity.OnBackPressedCallback(enabled)
{
    constructor(onBackPressed: OnBackPressedCallback.() -> Unit) : this(true, onBackPressed)

    /** 부모의 OnBackPressed를 호출한다. */
    fun callSuperOnBackPressed(dispatcher: OnBackPressedDispatcher) {
        // 현재 콜백을 제거하고, onBackPressed를 호출하고 다시 addCallback
        remove()
        dispatcher.onBackPressed()
        dispatcher.addCallback(this)
    }

    override fun handleOnBackPressed() {
        onBackPressed(this)
    }
}