package com.black.feature.pokerogue.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.black.core.util.Log
import com.black.core.viewmodel.Event
import com.black.core.viewmodel.EventViewModel
import com.black.core.webkit.BKWebView
import com.black.core.webkit.BKWebViewClient
import com.black.feature.pokerogue.data.PokeRepository
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [PokeRogueFragment] */
@HiltViewModel
class PokeRogueViewModel @Inject constructor(
    private val repo: PokeRepository
): EventViewModel(), BKWebViewClient.Callback {

    companion object {
        const val EVENT_RELOAD = "reload"
        const val EVENT_ROTATE = "rotate"
    }

    private val mutableWebView = MutableStateFlow<BKWebView?>(null)

    /** 화면 회전에도 게임 상태를 유지하도록 ViewModel이 보관하는 WebView(파기는 [onCleared]) */
    val webView: StateFlow<BKWebView?> = mutableWebView.asStateFlow()

    val isLoading = MutableStateFlow(false)
    val isStarted = MutableStateFlow(false)
    val isMatchUpOpen = MutableStateFlow(false)
    val isLandscapeButtonShowing = MutableStateFlow(true)

    private val setSelectedTypes = MutableStateFlow(emptyList<TypeUIState>())
    val selectedTypes = setSelectedTypes.asLiveData()

    val attackItemList = setSelectedTypes
        .map { it ->
            repo.getAttackMatchUp(it.map { it.type })
                .entries
                .groupBy({ it.value }, { it.key })
                .map { DamageUiState(it.key, it.value) }
                .sortedByDescending { it.damage.multiplier }
        }
        .asLiveData()

    val defenceListItemList = setSelectedTypes
        .map { it ->
            it.map { type ->
                repo.getDefenceMatchUp(type.type)
                    .entries
                    .groupBy({ it.value }, { it.key })
                    .map { DamageUiState(it.key, it.value) }
                    .sortedByDescending { it.damage.multiplier }
                    .let { DamageListUiState(type.type, it) }
            }
        }
        .asLiveData()

    /**
     * 보관 중인 WebView 반환, 없으면 [create]로 만들어 보관
     *
     * 첫 진입과 프로세스 종료 뒤 복원(새 ViewModel)에서 생성
     */
    fun obtainWebView(create: () -> BKWebView): BKWebView {
        mutableWebView.value?.let { return it }
        return create()
            .also {
                it.addWebViewClientCallback(this)
                mutableWebView.value = it
                isStarted.value = false
            }
    }

    override fun onCleared() {
        mutableWebView.value?.let {
            it.removeWebViewClientCallback(this)
            // pauseTimers는 프로세스 전역이라 이후 만드는 WebView가 멈춘 채 시작하지 않도록 되돌림
            it.resumeTimers()
            it.clear()
        }
        mutableWebView.value = null
        super.onCleared()
    }

    fun onClickPlay() {
        webView.value?.loadUrl("https://pokerogue.net/")
        isStarted.value = true
    }

    fun onClickReload() = viewModelScope.launch {
        eventFlow.emit(Event(EVENT_RELOAD, null))
    }

    fun onClickRotate() = viewModelScope.launch {
        if (webView.value?.url != null) {
            return@launch
        }
        eventFlow.emit(Event(EVENT_ROTATE, null))
    }

    fun onClickMatchUp() = viewModelScope.launch {
        isMatchUpOpen.value = !isMatchUpOpen.value
    }

    fun onClickHide() {
        isLandscapeButtonShowing.value = false
    }

    fun onTypeClick(type: PokeType) {
        Log.d(type)
        if (setSelectedTypes.value.all { it.type != type }) {
            if (setSelectedTypes.value.size < 2) {
                setSelectedTypes.value += TypeUIState(type) { onSelectedTypeClick(it) }
            }
        } else {
            setSelectedTypes.value -= TypeUIState(type) { onSelectedTypeClick(it) }
        }
    }

    private fun onSelectedTypeClick(typeUIState: TypeUIState) {
        setSelectedTypes.value -= typeUIState
    }

    fun onBackPressed(): Boolean {
        return if (isMatchUpOpen.value) {
            isMatchUpOpen.value = false
            true

        } else if (!isLandscapeButtonShowing.value) {
            isLandscapeButtonShowing.value = true
            true

        } else {
            false
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        isLoading.value = true
    }

    override fun onPageLoading(view: WebView, uri: Uri): Boolean {
        return false
    }

    override fun onPageFinished(view: WebView, url: String, isError: Boolean) {
        isLoading.value = false
    }

    override fun onVisitedHistoryUpdated(view: WebView, url: String, isReload: Boolean) {
    }
}