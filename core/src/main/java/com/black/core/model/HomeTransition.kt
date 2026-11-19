package com.black.core.model

/**
 * 집 도착·출발 알림 한 건
 *
 * 추정 기록은 그 시각에 실제로 연결·해제된 것이 아니라 나중에 알게 된 상태를 맞춘 기록이며, 다음 경우에 생김.
 * - 감시를 시작하거나 수집을 켜면, 서비스가 멈췄거나 수집이 꺼진 사이의 변화를 저장된 마지막 상태와 지금 상태의 차이로 맞춤.
 *   감시 시작 전부터 연결돼 있던 네트워크는 그 첫 보고, 보고 없이 끊김, 보고 대기 만료 때 맞춤
 * - 지금 연결의 첫 보고에서 SSID가 null이었다가 다음 보고에서 보이면 그 SSID로 연결을 다시 기록함.
 *   위치 권한을 나중에 허용한 경우 등이며 도착만 생김
 * - 집 SSID를 새로 등록하거나 지워 지금 연결의 집 여부가 바뀌면 도착이나 출발을 기록함
 *
 * 추정 기록을 정하는 판정기는 기기 사용 기록의 `WifiTransitionMachine`(core에서 링크할 수 없는 `:feature:usagelog` 모듈 클래스).
 *
 * @property arrived 도착이면 true, 출발이면 false
 * @property inferred 위 경우의 추정 기록에서 나온 알림이면 true
 * @property time 기록의 시각(epoch 밀리초)
 */
data class HomeTransition(val arrived: Boolean, val inferred: Boolean, val time: Long)
