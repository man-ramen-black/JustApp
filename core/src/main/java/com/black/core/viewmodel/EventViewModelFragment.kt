package com.black.core.viewmodel

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.black.core.util.FragmentExtension.viewLifecycleScope
import kotlinx.coroutines.launch

/**
 * [viewModel]의 [EventViewModel.events]를 뷰가 STARTED 이상일 때만 수집하는 Fragment 헬퍼
 *
 * 뷰 수명주기([Fragment.getViewLifecycleOwner]) 범위라 뷰를 만드는 onCreateView·onViewCreated에서 호출하고, 뷰가 파기되면 수집도 끝남.
 * STARTED 미만 구간에 보낸 이벤트는 [EventViewModel]이 보관했다가 다시 STARTED가 되면 전달.
 * Compose 화면은 [CollectEvents] 사용
 */
fun Fragment.collectEvents(viewModel: EventViewModel, onEvent: suspend (ViewModelEvent) -> Unit) {
    viewLifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { onEvent(it) }
        }
    }
}
