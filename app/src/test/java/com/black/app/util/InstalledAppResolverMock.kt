package com.black.app.util

import io.mockk.every
import io.mockk.mockk

/**
 * [InstalledAppResolver] 목업이 모든 packageName을 설치된 앱으로 찾도록 응답 구성
 *
 * 라벨은 [label] 규칙(기본은 packageName 그대로), 아이콘은 목업 Drawable.
 * 설치되지 않은 앱 표시용 기본 아이콘도 목업 Drawable로 응답해 [find] 응답을 테스트에서 null로 덮어써도 동작
 */
fun InstalledAppResolver.mockInstalledApps(label: (packageName: String) -> String = { it }) {
    every { find(any<String>()) } answers { InstalledApp(firstArg(), label(firstArg()), mockk()) }
    every { defaultIcon() } returns mockk()
}
