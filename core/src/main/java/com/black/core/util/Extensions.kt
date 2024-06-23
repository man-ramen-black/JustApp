package com.black.core.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Extensions {
    fun <T> T.ifThen(isTrue: Boolean, then: T.() -> Unit): T = apply {
        if (isTrue) then()
    }

    /**  Context에서 activity 획득 */
    tailrec fun Context.activity(): ComponentActivity?
            = when(this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }

    fun ViewModel.launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ) = viewModelScope.launch(context, start, block)

    fun <T> Flow<T>.collect(
        scope: CoroutineScope,
        coroutineContext: CoroutineContext = Dispatchers.Main,
        flowCollector: FlowCollector<T>
    ): Job = scope.launch(coroutineContext) { collect(flowCollector) }
}