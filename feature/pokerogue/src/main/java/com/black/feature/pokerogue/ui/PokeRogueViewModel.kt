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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.lang.ref.SoftReference
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

    // 화면 회전 시 객체 유지를 위해 ViewModel에서 관리
    lateinit var webView: SoftReference<BKWebView>
        private set

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

    fun init(webView: SoftReference<BKWebView>) {
        this.webView = webView
        isStarted.value = false
    }

    fun onClickPlay() {
        webView.get()?.loadUrl("https://pokerogue.net/")
        isStarted.value = true
    }

    fun onClickReload() = viewModelScope.launch {
        eventFlow.emit(Event(EVENT_RELOAD, null))
    }

    fun onClickRotate() = viewModelScope.launch {
        if (webView.get()?.url != null) {
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