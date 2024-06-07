package com.black.feature.pokerogue.ui

import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/** [PokeRogueFragment] */
@HiltViewModel
class PokeRogueViewModel @Inject constructor(): EventViewModel() {
    val url = MutableStateFlow("https://pokerogue.net/")
    val isLoading = MutableStateFlow(false)

    fun onPageStarted(url: String) {
        isLoading.value = true
    }

    fun onPageFinished(url: String, isError: Boolean) {
        isLoading.value = false
    }
}