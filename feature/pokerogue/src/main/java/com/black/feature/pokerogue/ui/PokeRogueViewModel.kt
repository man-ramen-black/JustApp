package com.black.feature.pokerogue.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.asLiveData
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import com.black.core.webkit.BKWebView
import com.black.core.webkit.BKWebViewClient
import com.black.feature.pokerogue.data.PokeRepository
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [PokeRogueFragment]의 상태·이벤트 관리 ViewModel */
@HiltViewModel
class PokeRogueViewModel @Inject constructor(
    private val repo: PokeRepository
): EventViewModel(), BKWebViewClient.Callback {

    /** PokeRogue 화면 일회성 이벤트 */
    sealed class PokeRogueEvent : ViewModelEvent

    /** 다시 불러오기 확인 다이얼로그 표시 */
    data object EventShowReloadConfirm : PokeRogueEvent()
    /** 화면 방향 전환 */
    data object EventRotate : PokeRogueEvent()

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

    fun play() {
        webView.value?.loadUrl("https://pokerogue.net/")
        isStarted.value = true
    }

    fun requestReload() {
        sendEvent(EventShowReloadConfirm)
    }

    /** 게임을 시작하기 전(페이지를 열기 전)에만 화면 방향 전환 */
    fun rotate() {
        if (webView.value?.url != null) {
            return
        }
        sendEvent(EventRotate)
    }

    fun toggleMatchUp() {
        isMatchUpOpen.value = !isMatchUpOpen.value
    }

    fun hideLandscapeButton() {
        isLandscapeButtonShowing.value = false
    }

    fun toggleType(type: PokeType) {
        Log.d(type)
        if (setSelectedTypes.value.all { it.type != type }) {
            if (setSelectedTypes.value.size < 2) {
                setSelectedTypes.value += TypeUIState(type) { deselectType(it) }
            }
        } else {
            setSelectedTypes.value -= TypeUIState(type) { deselectType(it) }
        }
    }

    private fun deselectType(typeUIState: TypeUIState) {
        setSelectedTypes.value -= typeUIState
    }

    /** 뒤로 가기를 화면 안에서 처리했으면 true(상성표 닫기, 숨긴 가로 버튼 다시 표시) */
    fun handleBack(): Boolean {
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