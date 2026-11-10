package com.black.app.testutil

import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hilt 주입이 필요한 @AndroidEntryPoint Fragment를 테스트에서 호스팅하기 위한 빈 Activity
 *
 * launchFragmentInContainer가 제공하는 기본 Activity는 @AndroidEntryPoint가 아니어서
 * Hilt Fragment를 주입할 수 없으므로 테스트 전용 호스트 Activity를 계측 테스트가 도는 개발 앱(debug) 소스셋에 배치
 * (실사용 앱인 stable 빌드에는 미포함)
 */
@AndroidEntryPoint
class HiltTestActivity : AppCompatActivity()
