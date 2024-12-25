package com.black.core.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Extensions {

    inline fun <T> T.letIf(ifBlock: (T) -> Boolean, thenBlock: (T) -> T): T {
        return if (ifBlock(this)) {
            thenBlock(this)
        } else {
            this
        }
    }

    inline fun <T> T.alsoIf(ifBlock: (T) -> Boolean, thenBlock: (T) -> Unit): T {
        if (ifBlock(this)) {
            thenBlock(this)
        }
        return this
    }

    inline fun <T> T.runIf(ifBlock: (T) -> Boolean, thenBlock: T.() -> T): T {
        return if (ifBlock(this)) {
            thenBlock()
        } else {
            this
        }
    }

    inline fun <T> T.applyIf(ifBlock: (T) -> Boolean, thenBlock: T.() -> Unit): T {
        if (ifBlock(this)) {
            thenBlock()
        }
        return this
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

    inline fun <reified T> Flow<T>.collect(
        lifecycleOwner: LifecycleOwner,
        coroutineContext: CoroutineContext = Dispatchers.Main,
        collector: FlowCollector<T>
    ): Job = collect(lifecycleOwner.lifecycleScope, coroutineContext, collector)

    inline fun <reified T> Flow<T>.collect(
        scope: CoroutineScope,
        coroutineContext: CoroutineContext = Dispatchers.Main,
        collector: FlowCollector<T>
    ): Job = scope.launch(coroutineContext) {
        collect {
            withContext(Dispatchers.Main) {
                collector.emit(it)
            }
        }
    }
}