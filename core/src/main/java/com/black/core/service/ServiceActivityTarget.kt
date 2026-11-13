package com.black.core.service

/**
 * [ServiceActivity]로 시작·중지할 수 있는 [BaseForegroundService] 표시
 *
 * exported ServiceActivity로 외부 앱도 시작·중지할 수 있으므로, 그래도 되는 서비스(바로가기·알림이 ServiceActivity를 쓰는 서비스)만 구현.
 * 실행 설정 확인 같은 시작 조건을 따로 거쳐야 하는 서비스는 구현하지 않음
 */
interface ServiceActivityTarget
