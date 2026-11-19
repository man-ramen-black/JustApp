package com.black.feature.usagetimer.model

import com.black.core.model.OverlayPosition
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor

/**
 * 사용 타이머 설정의 기본값
 *
 * 저장소(DataStore)와 화면 상태, 오버레이가 같은 값을 쓰도록 모델 계층에 둠
 */
object UsageTimerDefaults {
    /**
     * 저장된 위치가 없을 때의 표시 위치
     *
     * 화면 세로 중앙 왼쪽.
     */
    val POSITION = OverlayPosition(HorizontalAnchor.LEFT, VerticalAnchor.CENTER)
}
