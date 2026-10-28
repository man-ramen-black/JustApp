package com.black.app.testutil

import androidx.appcompat.app.AppCompatActivity

/**
 * 보안 잠금(PIN 등)을 풀지 않고 "앱 사용 중"과 같은 창 상태를 만들기 위한 빈 Activity
 *
 * showWhenLocked로 잠금 화면 위에 표시되어 잠금 화면을 가림(occlude). 잠금 자체는 유지되므로 다른 화면으로 나가려면 여전히 PIN 필요
 * 잠금 화면이 보이는 동안에는 그 아래 앱 창이 화면에 없는 창으로 취급되어 FLAG_KEEP_SCREEN_ON 같은 창 플래그를 검증할 수 없음
 */
class ShowWhenLockedTestActivity : AppCompatActivity()
