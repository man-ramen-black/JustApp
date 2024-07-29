package com.black.app.deeplink

import android.net.Uri
import com.black.core.util.Log
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject

@ActivityRetainedScoped
class DeeplinkManager @Inject constructor() {
    // Fragment가 생성되기 전(collect 전)에 딥링크가 발생한 경우를 위해 replay = 1 적용
    // 동일한 딥링크가 처리될 수 있도록 StableFlow 대신 SharedFlow 사용
    private val receivedDeeplinkFlow = MutableSharedFlow<Uri>(replay = 1)
    private val deeplinkFlow = receivedDeeplinkFlow.asSharedFlow()

    // 이미 딥링크를 처리한 클래스들 체크를 위한 set
    private val handledTags = CopyOnWriteArraySet<String>()

    suspend fun receiveDeeplink(uri: Uri?) {
        Log.d(uri)
        uri ?: return

        if (uri.scheme != Deeplink.Scheme.APP) {
            return
        }

        // 새로운 딥링크 호출 시 처리된 클래스 목록 초기화
        handledTags.clear()

        // 딥링크 전달
        receivedDeeplinkFlow.emit(uri)
    }

    fun getDeeplinkFlow(tag: String): Flow<Uri> {
        return deeplinkFlow
            // 이미 딥링크가 처리된 클래스 필터링
            .filter { !handledTags.contains(tag) }
            .onEach { handledTags.add(tag) }
    }

    fun getDeeplinkFlow(caller: Any): Flow<Uri>
        = getDeeplinkFlow(caller::class.java.name)
}