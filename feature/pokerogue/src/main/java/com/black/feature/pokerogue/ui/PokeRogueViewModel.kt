package com.black.feature.pokerogue.ui

import com.black.core.viewmodel.EventViewModel
import com.black.feature.pokerogue.data.PokeRepository
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [PokeRogueFragment] */
@HiltViewModel
class PokeRogueViewModel @Inject constructor(
    private val repo: PokeRepository
): EventViewModel() {

    val url = MutableStateFlow<String?>(null)
    val isLoading = MutableStateFlow(false)
    val selectedTypes = MutableStateFlow(emptyList<TypeUIState>())
    val damageItemList = selectedTypes
        .map { it ->
            repo.getDefenceMatchUp(it.map { it.type })
                .entries
                .groupBy({ it.value }, { it.key })
                .map { DamageUiState(it.key, it.value) }
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

    fun onTypeClick(type: PokeType) {
        if (selectedTypes.value.any { it.type == type }) {
            return
        }
        selectedTypes.value += TypeUIState(type) { onSelectedTypeClick(it) }
    }

    private fun onSelectedTypeClick(typeUIState: TypeUIState) {
        selectedTypes.value -= typeUIState
    }
}