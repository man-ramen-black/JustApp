package com.black.feature.pokerogue.model

import kotlinx.serialization.Serializable

@Serializable
data class PokeTypeChart(
    val name: String,
    val immunes: List<String>,
    val weaknesses: List<String>,
    val strengths: List<String>,
)