package com.black.app.ui.maintab.main.keepscreenon

import android.service.quicksettings.Tile
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config

/**
 * [KeepScreenOnTileService] 활성 여부에 따른 타일 상태 판정 검증
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Config(sdk = [34])
class KeepScreenOnTileServiceTest : BaseTest() {

    /**
     * 활성 상태면 타일 활성 표시
     *
     * Given: 화면 켜짐 유지 활성
     * When: resolveTileState 호출
     * Then: STATE_ACTIVE
     */
    @Test
    fun test_01_activeResolvesActiveTile() {
        /** Given / When / Then **/
        assertEquals(Tile.STATE_ACTIVE, KeepScreenOnTileService.resolveTileState(true))
    }

    /**
     * 비활성 상태면 타일 비활성 표시
     *
     * Given: 화면 켜짐 유지 비활성
     * When: resolveTileState 호출
     * Then: STATE_INACTIVE
     */
    @Test
    fun test_02_inactiveResolvesInactiveTile() {
        /** Given / When / Then **/
        assertEquals(Tile.STATE_INACTIVE, KeepScreenOnTileService.resolveTileState(false))
    }
}
