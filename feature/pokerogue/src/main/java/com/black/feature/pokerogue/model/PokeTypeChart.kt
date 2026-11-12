package com.black.feature.pokerogue.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 공격 타입 하나의 상성표(poke_type_chart.json 한 항목)
 *
 * @property name 공격 타입 이름
 * @property immunes 이 타입 공격이 통하지 않는(0배) 방어 타입
 * @property resistedBy 이 타입 공격을 반감하는(0.5배) 방어 타입. 자산 JSON 키는 weaknesses
 * @property strengths 이 타입 공격이 효과가 굉장한(2배) 방어 타입
 */
@Serializable
data class PokeTypeChart(
    val name: String,
    val immunes: List<String>,
    @SerialName("weaknesses")
    val resistedBy: List<String>,
    val strengths: List<String>,
)
