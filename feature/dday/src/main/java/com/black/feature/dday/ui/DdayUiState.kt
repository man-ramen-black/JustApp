package com.black.feature.dday.ui

import com.black.core.system.DeviceCalendar
import com.black.feature.dday.data.usecase.DdayLoadResult
import com.black.feature.dday.model.DdayStyle

/** 캘린더 선택 목록의 한 줄, [included]는 위젯에 일정을 올리는지 여부 */
data class DdayCalendarOption(val calendar: DeviceCalendar, val included: Boolean)

/** [DdayScreen]의 화면 상태 */
data class DdayUiState(
    /** 첫 불러오기가 끝났는지 */
    val loaded: Boolean = false,
    /** 캘린더 읽기 권한 허용 여부 */
    val permissionGranted: Boolean = false,
    /** 기기 캘린더 목록, 권한이 없으면 빈 목록 */
    val calendars: List<DdayCalendarOption> = emptyList(),
    /** 현재 위젯 스타일 */
    val style: DdayStyle = DdayStyle(),
    /** 미리보기에 쓰는 실제 위젯 조회 결과 */
    val preview: DdayLoadResult = DdayLoadResult.Loaded(emptyList()),
)
