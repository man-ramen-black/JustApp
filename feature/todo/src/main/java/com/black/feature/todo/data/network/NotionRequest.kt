package com.black.feature.todo.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * data source 조회 요청
 *
 * [startCursor]가 null이면 첫 쪽부터 받음.
 */
@Serializable
data class NotionQueryRequest(
    val filter: NotionFilter,
    val sorts: List<NotionSort>,
    @SerialName("page_size") val pageSize: Int = MAX_PAGE_SIZE,
    @SerialName("start_cursor") val startCursor: String? = null,
) {
    companion object {
        /** 노션이 한 쪽에 돌려주는 최대 개수 */
        const val MAX_PAGE_SIZE = 100
    }
}

/**
 * 조회 필터
 *
 * 속성 조건은 [property]와 [checkbox], 수정 시각 조건은 [timestamp]와 [lastEditedTime], 여러 조건은 [and]로 묶음.
 */
@Serializable
data class NotionFilter(
    val property: String? = null,
    val checkbox: NotionCheckboxFilter? = null,
    val and: List<NotionFilter>? = null,
    val timestamp: String? = null,
    @SerialName("last_edited_time") val lastEditedTime: NotionDateFilter? = null,
)

@Serializable
data class NotionCheckboxFilter(val equals: Boolean)

/** [onOrAfter]는 UTC `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` 형식 */
@Serializable
data class NotionDateFilter(@SerialName("on_or_after") val onOrAfter: String)

/** 조회 정렬이며 속성 기준이면 [property], 수정 시각 기준이면 [timestamp] */
@Serializable
data class NotionSort(
    val property: String? = null,
    val timestamp: String? = null,
    val direction: String,
)

/** 페이지 추가 요청 */
@Serializable
data class NotionCreatePageRequest(
    val parent: NotionParent,
    val properties: Map<String, NotionPropertyValue>,
)

/** 페이지를 추가할 data source */
@Serializable
data class NotionParent(
    val type: String = "data_source_id",
    @SerialName("data_source_id") val dataSourceId: String,
)

/** 페이지 속성 수정 요청 */
@Serializable
data class NotionUpdatePageRequest(val properties: Map<String, NotionPropertyValue>)

/** 요청에 싣는 속성 값이며 속성 종류에 맞는 필드 하나만 채움 */
@Serializable
data class NotionPropertyValue(
    val title: List<NotionTextValue>? = null,
    val select: NotionSelectValue? = null,
    val checkbox: Boolean? = null,
    val date: NotionDateValue? = null,
)

/** 요청에 싣는 리치 텍스트 한 조각 */
@Serializable
data class NotionTextValue(val text: NotionText)

@Serializable
data class NotionText(val content: String)

@Serializable
data class NotionSelectValue(val name: String)

/**
 * 요청에 싣는 날짜
 *
 * [start]가 오프셋 없는 지역 시각이면 [timeZone]에 그 시간대 ID를 함께 보내고, 날짜만이면 [timeZone]은 null.
 */
@Serializable
data class NotionDateValue(
    val start: String,
    @SerialName("time_zone") val timeZone: String? = null,
)
