package com.black.feature.pokerogue.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.asLiveData
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import com.black.core.webkit.BKWebViewClient
import com.black.feature.pokerogue.data.PokeRepository
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [PokeRogueFragment]의 상태·이벤트 관리 ViewModel
 *
 * WebView는 Fragment가 보관하고, 페이지 로딩 콜백([BKWebViewClient.Callback])으로 이 ViewModel을 등록·해제하는 것도 Fragment 담당
 */
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
    /** WebView에서 [url] 페이지 열기 */
    data class EventLoadUrl(val url: String) : PokeRogueEvent()

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
     * 페이지를 열지 않은 새 WebView에 맞춰 게임 시작 전 상태로 되돌림
     *
     * WebView는 Fragment와 함께 파기되므로, 지정하지 않은 설정 변경으로 Activity가 다시 만들어져 ViewModel만 남으면 이전 게임의 시작·로딩 상태가 남아 있음
     */
    fun resetGameState() {
        isStarted.value = false
        isLoading.value = false
    }

    fun play() {
        sendEvent(EventLoadUrl("https://pokerogue.net/"))
        isStarted.value = true
    }

    fun requestReload() {
        sendEvent(EventShowReloadConfirm)
    }

    /** 게임을 시작하기 전(페이지를 열기 전)에만 화면 방향 전환 */
    fun rotate() {
        if (isStarted.value) {
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