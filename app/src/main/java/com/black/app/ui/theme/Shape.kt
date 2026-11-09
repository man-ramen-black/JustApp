package com.black.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 화면 공용 모서리 shape */
object AppShapes {
    /** 아이콘 박스·버튼·다이얼로그 공용 모서리 shape */
    val Container = RoundedCornerShape(4.dp)

    /** 알림 미리보기 카드 모서리(알림창 카드 근사) */
    val NotificationPreview = RoundedCornerShape(16.dp)
}
