package com.black.app.ui.maintab.main.keepscreenon

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

/**
 * 빠른 설정창 "화면 켜짐 유지" 타일
 *
 * 타일 클릭 시 [KeepScreenOnGlobal] 토글, 다른 앱 위에 표시 권한 미허용 시 권한 설정 화면 이동
 */
class KeepScreenOnTileService : TileService() {
    companion object {
        /**
         * 활성 여부에 따른 타일 상태 판정
         */
        fun resolveTileState(isActive: Boolean): Int =
            if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!PermissionHelper.canDrawOverlays(this)) {
            Log.w("cannot draw overlays")
            // 활성 중 권한 회수 시 시스템이 숨긴 창 정리 후 권한 설정 화면 이동
            KeepScreenOnGlobal.deactivate(this)
            updateTileState()
            showDrawOverlaysSetting()
            return
        }

        KeepScreenOnGlobal.toggle(this)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = resolveTileState(KeepScreenOnGlobal.isActive.value)
        tile.updateTile()
    }

    /**
     * 백그라운드 Activity 시작 제한 회피용 startActivityAndCollapse로 권한 설정 화면 이동
     */
    private fun showDrawOverlaysSetting() {
        val intent = PermissionHelper.getDrawOverlaysSettingIntent(this)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
