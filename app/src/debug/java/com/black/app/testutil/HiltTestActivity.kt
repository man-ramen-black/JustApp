package com.black.app.testutil

import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hilt 주입이 필요한 @AndroidEntryPoint Fragment를 테스트에서 호스팅하기 위한 빈 Activity
 *
 * launchFragmentInContainer가 제공하는 기본 Activity는 @AndroidEntryPoint가 아니어서
 * Hilt Fragment를 주입할 수 없으므로, 테스트 전용 호스트 Activity를 debug 소스셋에 둔다.
 */
@AndroidEntryPoint
class HiltTestActivity : AppCompatActivity()
