package com.black.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.IdRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
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

    fun <T> Flow<T>.collect(
        scope: CoroutineScope,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        flowCollector: FlowCollector<T>
    ): Job = scope.launch(coroutineContext) { collect(flowCollector) }

    fun <T> Flow<T>.collectOnMain(
        scope: CoroutineScope,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        collector: FlowCollector<T>
    ): Job = scope.launch(coroutineContext) {
        collect {
            withContext(Dispatchers.Main) {
                collector.emit(it)
            }
        }
    }

    /** 현재 destination에서 navigate할 수 없는 directions이면 navigate하지 않음 */
    @SuppressLint("RestrictedApi")
    fun NavController.navigateSafety(@IdRes actionId: Int, args: Bundle? = null, navOptions: NavOptions? = null, navigatorExtras: Navigator.Extras? = null) {
        if (currentDestination?.getAction(actionId) == null) {
            Log.v(currentDestination?.displayName + " cannot navigate to " + (findDestination(actionId)?.displayName ?: actionId))
            return
        }
        navigate(actionId, args, navOptions, navigatorExtras)
    }

    fun NavController.navigateSafety(directions: NavDirections, navOptions: NavOptions? = null, navigatorExtras: Navigator.Extras? = null)
            = navigate(directions.actionId, directions.arguments, navOptions, navigatorExtras)
}