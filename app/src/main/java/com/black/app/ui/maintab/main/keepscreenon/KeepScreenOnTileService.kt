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
        /** 활성 여부에 따른 타일 상태 판정 */
        fun resolveTileState(isActive: Boolean): Int =
            if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    }

    override fun onStartListening() {
        super.onStartListening()
        // 빠른 설정창이 열릴 때마다 화면 꺼짐 자동 해제 등 바깥 변경분 반영
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        // 권한 미허용: 토글 대신 권한 설정 화면 안내
        if (!PermissionHelper.canDrawOverlays(this)) {
            Log.w("cannot draw overlays")
            // 활성 중 권한 회수 시 시스템이 숨긴 창 정리 후 권한 설정 화면 이동
            KeepScreenOnGlobal.deactivate(this)
            updateTileState()
            showDrawOverlaysSetting()
            return
        }

        // 권한 허용: 켜짐 유지 토글 후 타일 표시 갱신
        KeepScreenOnGlobal.toggle(this)
        updateTileState()
    }

    private fun updateTileState() {
        // 리스닝 구간 밖에서는 qsTile이 null
        val tile = qsTile ?: return
        tile.state = resolveTileState(KeepScreenOnGlobal.isActive.value)
        tile.updateTile()
    }

    /** 백그라운드 Activity 시작 제한 회피용 startActivityAndCollapse로 권한 설정 화면 이동 */
    private fun showDrawOverlaysSetting() {
        val intent = PermissionHelper.getDrawOverlaysSettingIntent(this)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return

        // Android 14(targetSdk 34) 이상에서 Intent 버전은 UnsupportedOperationException, PendingIntent 버전 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
