package com.black.feature.pokerogue.ui

import androidx.lifecycle.viewModelScope
import com.black.core.viewmodel.Event
import com.black.core.viewmodel.EventViewModel
import com.black.feature.pokerogue.data.PokeRepository
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [PokeRogueFragment] */
@HiltViewModel
class PokeRogueViewModel @Inject constructor(
    private val repo: PokeRepository
): EventViewModel() {

    companion object {
        const val EVENT_RELOAD = "reload"
    }

    val url = MutableStateFlow<String?>(null)
    val isLoading = MutableStateFlow(false)
    val selectedTypes = MutableStateFlow(emptyList<TypeUIState>())
    val attackItemList = selectedTypes
        .map { it ->
            repo.getAttackMatchUp(it.map { it.type })
                .entries
                .groupBy({ it.value }, { it.key })
                .map { DamageUiState(it.key, it.value) }
                .sortedByDescending { it.damage.multiplier }
        }

    val defenceListItemList = selectedTypes
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

    fun onPageStarted(url: String) {
        isLoading.value = true
    }

    fun onPageFinished(url: String, isError: Boolean) {
        isLoading.value = false
    }

    fun onClickPlay() {
        url.value = "https://pokerogue.net/"
    }

    fun onClickReload() = viewModelScope.launch {
        eventFlow.emit(Event(EVENT_RELOAD, null))
    }

    fun onTypeClick(type: PokeType) {
        if (selectedTypes.value.all { it.type != type }) {
            if (selectedTypes.value.size < 2) {
                selectedTypes.value += TypeUIState(type) { onSelectedTypeClick(it) }
            }
        } else {
            selectedTypes.value -= TypeUIState(type) { onSelectedTypeClick(it) }
        }
    }

    private fun onSelectedTypeClick(typeUIState: TypeUIState) {
    }
}