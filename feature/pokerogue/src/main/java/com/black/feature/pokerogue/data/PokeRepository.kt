package com.black.feature.pokerogue.data

import android.content.Context
import com.black.core.util.FileUtil
import com.black.core.util.JsonUtil
import com.black.feature.pokerogue.model.Damage
import com.black.feature.pokerogue.model.PokeType
import com.black.feature.pokerogue.model.PokeTypeChart
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PokeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var pokeTypeChart: List<PokeTypeChart>? = null

    private suspend fun getPokeTypeCharts(): List<PokeTypeChart> = withContext(Dispatchers.IO) {
        if (pokeTypeChart != null) {
            return@withContext pokeTypeChart!!
        }

        val inputStream = context.assets.open("poke_type_chart.json")
        return@withContext JsonUtil.from<List<PokeTypeChart>>(FileUtil.readText(inputStream), true)!!
            .also { pokeTypeChart = it }
    }

    suspend fun getDefenceMatchUp(attackType: PokeType): Map<PokeType, Damage> = withContext(Dispatchers.IO) {
        getPokeTypeCharts()
            .filter { chart -> attackType.name == chart.name }
            .flatMap { chart ->
                listOf(
                    chart.immunes.map { it to Damage.X0 },
                    chart.weaknesses.map { it to Damage.X05 },
                    chart.strengths.map { it to Damage.X2 }
                )
            }
            .flatten()
            .groupBy(
                { PokeType.valueOf(it.first) },
                { it.second }
            )
            .mapValues { it.value.reduce { a, b -> Damage.valueOf(a.multiplier * b.multiplier) } }
            .filterValues { it != Damage.X1 }
    }

    suspend fun getAttackMatchUp(defenceTypes: List<PokeType>): Map<PokeType, Damage> = withContext(Dispatchers.IO) {
        val typeNames = defenceTypes.map { it.name }
        getPokeTypeCharts()
            .flatMap { chart ->
                listOf(
                    chart.immunes.filter { typeNames.contains(it) }
                        .map { chart.name to Damage.X0 },
                    chart.weaknesses.filter { typeNames.contains(it) }
                        .map { chart.name to Damage.X05 },
                    chart.strengths.filter { typeNames.contains(it) }
                        .map { chart.name to Damage.X2 }
                )
            }
            .flatten()
            .groupBy(
                { PokeType.valueOf(it.first) },
                { it.second }
            )
            .mapValues { it.value.reduce { a, b -> Damage.valueOf(a.multiplier * b.multiplier) } }
            .filterValues { it != Damage.X1 }
    }
}