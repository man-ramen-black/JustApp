package com.black.core.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * [EventViewModel.events]를 STARTED 이상에서만 수집하는 Compose 헬퍼.
 * 비가시 구간 side effect 차단 — 화면 단위 일회성 이벤트 구독 용도
 */
@Composable
fun EventViewModel.CollectEvents(onEvent: suspend (ViewModelEvent) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(this, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { onEvent(it) }
        }
    }
}
