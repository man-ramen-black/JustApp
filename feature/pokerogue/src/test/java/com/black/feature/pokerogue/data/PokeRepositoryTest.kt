package com.black.feature.pokerogue.data

import com.black.feature.pokerogue.model.Damage
import com.black.feature.pokerogue.model.PokeType
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [PokeRepository]가 번들 상성표(poke_type_chart.json)로 계산하는 타입 상성 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PokeRepositoryTest : BaseTest() {

    private val repository = PokeRepository(RuntimeEnvironment.getApplication())

    /**
     * 공격 타입 하나가 각 방어 타입에 주는 배율을 상성표대로 돌려주는지 검증
     *
     * Given: 번들 상성표
     * When: 불꽃 공격으로 getDefenceMatchUp 호출
     * Then: 풀·얼음·벌레·강철 2배, 불꽃·물·바위·드래곤 0.5배, 그 밖의 타입(1배)은 결과에 없음
     */
    @Test
    fun test_01_fireAttackMatchUp() = runTest {
        /** Given **/
        // 번들 자산 사용(별도 준비 없음)

        /** When **/
        val matchUp = repository.getDefenceMatchUp(PokeType.Fire)

        /** Then **/
        assertEquals(
            mapOf(
                PokeType.Grass to Damage.X2,
                PokeType.Ice to Damage.X2,
                PokeType.Bug to Damage.X2,
                PokeType.Steel to Damage.X2,
                PokeType.Fire to Damage.X05,
                PokeType.Water to Damage.X05,
                PokeType.Rock to Damage.X05,
                PokeType.Dragon to Damage.X05,
            ),
            matchUp,
        )
    }

    /**
     * 방어 타입 두 개에 대한 공격 타입별 배율을 곱해서 돌려주는지 검증
     *
     * Given: 번들 상성표
     * When: 불꽃·드래곤 방어로 getAttackMatchUp 호출
     * Then: 드래곤·땅·바위 2배, 불꽃·풀 0.25배, 물(2배와 0.5배가 곱해져 1배)은 결과에 없음
     */
    @Test
    fun test_02_dualDefenceMatchUpMultiplies() = runTest {
        /** Given **/
        // 번들 자산 사용(별도 준비 없음)

        /** When **/
        val matchUp = repository.getAttackMatchUp(listOf(PokeType.Fire, PokeType.Dragon))

        /** Then **/
        assertEquals(Damage.X2, matchUp[PokeType.Dragon])
        assertEquals(Damage.X2, matchUp[PokeType.Ground])
        assertEquals(Damage.X2, matchUp[PokeType.Rock])
        assertEquals(Damage.X025, matchUp[PokeType.Fire])
        assertEquals(Damage.X025, matchUp[PokeType.Grass])
        assertFalse(matchUp.containsKey(PokeType.Water))
    }
}
