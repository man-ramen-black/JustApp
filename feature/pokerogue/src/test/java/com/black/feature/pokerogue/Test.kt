package com.black.feature.pokerogue

import com.black.core.util.JsonUtil
import com.black.feature.pokerogue.data.PokeRepository
import com.black.feature.pokerogue.model.PokeType
import com.black.feature.pokerogue.model.PokeTypeChart
import com.black.test.BaseTest
import com.black.test.TestUtil
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class Test: BaseTest() {
    private val pokeTypeChartsJson = "[{\"name\":\"Normal\",\"immunes\":[\"Ghost\"],\"weaknesses\":[\"Rock\",\"Steel\"],\"strengths\":[]},\n" +
            "  {\"name\":\"Fire\",\"immunes\":[],\"weaknesses\":[\"Fire\",\"Water\",\"Rock\",\"Dragon\"],\"strengths\":[\"Grass\",\"Ice\",\"Bug\",\"Steel\"]},\n" +
            "  {\"name\":\"Water\",\"immunes\":[],\"weaknesses\":[\"Water\",\"Grass\",\"Dragon\"],\"strengths\":[\"Fire\",\"Ground\",\"Rock\"]},\n" +
            "  {\"name\":\"Electric\",\"immunes\":[\"Ground\"],\"weaknesses\":[\"Electric\",\"Grass\",\"Dragon\"],\"strengths\":[\"Water\",\"Flying\"]},\n" +
            "  {\"name\":\"Grass\",\"immunes\":[],\"weaknesses\":[\"Fire\",\"Grass\",\"Poison\",\"Flying\",\"Bug\",\"Dragon\",\"Steel\"],\"strengths\":[\"Water\",\"Ground\",\"Rock\"]},\n" +
            "  {\"name\":\"Ice\",\"immunes\":[],\"weaknesses\":[\"Fire\",\"Water\",\"Ice\",\"Steel\"],\"strengths\":[\"Grass\",\"Ground\",\"Flying\",\"Dragon\"]},\n" +
            "  {\"name\":\"Fighting\",\"immunes\":[\"Ghost\"],\"weaknesses\":[\"Poison\",\"Flying\",\"Psychic\",\"Bug\",\"Fairy\"],\"strengths\":[\"Normal\",\"Ice\",\"Rock\",\"Dark\",\"Steel\"]},\n" +
            "  {\"name\":\"Poison\",\"immunes\":[\"Steel\"],\"weaknesses\":[\"Poison\",\"Ground\",\"Rock\",\"Ghost\"],\"strengths\":[\"Grass\",\"Fairy\"]},\n" +
            "  {\"name\":\"Ground\",\"immunes\":[\"Flying\"],\"weaknesses\":[\"Grass\",\"Bug\"],\"strengths\":[\"Fire\",\"Electric\",\"Poison\",\"Rock\",\"Steel\"]},\n" +
            "  {\"name\":\"Flying\",\"immunes\":[],\"weaknesses\":[\"Electric\",\"Rock\",\"Steel\"],\"strengths\":[\"Grass\",\"Fighting\",\"Bug\"]},\n" +
            "  {\"name\":\"Psychic\",\"immunes\":[\"Dark\"],\"weaknesses\":[\"Psychic\",\"Steel\"],\"strengths\":[\"Fighting\",\"Poison\"]},\n" +
            "  {\"name\":\"Bug\",\"immunes\":[],\"weaknesses\":[\"Fire\",\"Fighting\",\"Poison\",\"Flying\",\"Ghost\",\"Steel\",\"Fairy\"],\"strengths\":[\"Grass\",\"Psychic\",\"Dark\"]},\n" +
            "  {\"name\":\"Rock\",\"immunes\":[],\"weaknesses\":[\"Fighting\",\"Ground\",\"Steel\"],\"strengths\":[\"Fire\",\"Ice\",\"Flying\",\"Bug\"]},\n" +
            "  {\"name\":\"Ghost\",\"immunes\":[\"Normal\"],\"weaknesses\":[\"Dark\"],\"strengths\":[\"Psychic\",\"Ghost\"]},\n" +
            "  {\"name\":\"Dragon\",\"immunes\":[\"Fairy\"],\"weaknesses\":[\"Steel\"],\"strengths\":[\"Dragon\"]},\n" +
            "  {\"name\":\"Dark\",\"immunes\":[],\"weaknesses\":[\"Fighting\",\"Dark\",\"Fairy\"],\"strengths\":[\"Psychic\",\"Ghost\"]},\n" +
            "  {\"name\":\"Steel\",\"immunes\":[],\"weaknesses\":[\"Fire\",\"Water\",\"Electric\",\"Steel\"],\"strengths\":[\"Ice\",\"Rock\",\"Fairy\"]},\n" +
            "  {\"name\":\"Fairy\",\"immunes\":[],\"weaknesses\":[\"Fire\",\"Poison\",\"Steel\"],\"strengths\":[\"Fighting\",\"Dragon\",\"Dark\"]}]"

    @Test
    fun getDefenceMatchUp() = runBlocking {
        val repo = spyk(PokeRepository(TestUtil.getTestContext()))
        coEvery { repo["getPokeTypeCharts"]() } returns JsonUtil.from<List<PokeTypeChart>>(pokeTypeChartsJson, true)!!

        val result = repo.getAttackMatchUp(listOf(PokeType.Fire, PokeType.Dragon))
            .entries
            .groupBy({it.value}, {it.key})
        println(result)
    }
}