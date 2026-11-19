package com.black.feature.todo.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * data source 조회 응답 한 쪽
 *
 * 2xx로 온 노션 오류 본문(`object: error`)이나 빈 객체도 해석되므로 [objectType]이 `list`이고 [results]가 있어야 조회 결과임.
 */
@Serializable
data class NotionQueryResponse(
    @SerialName("object") val objectType: String? = null,
    val results: List<NotionPage>? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

/**
 * 노션 페이지
 *
 * [lastEditedTime]은 UTC `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` 형식. [properties]의 키는 속성 이름.
 */
@Serializable
data class NotionPage(
    @SerialName("object") val objectType: String? = null,
    val id: String? = null,
    @SerialName("last_edited_time") val lastEditedTime: String? = null,
    val url: String? = null,
    @SerialName("in_trash") val inTrash: Boolean = false,
    val archived: Boolean = false,
    val properties: Map<String, NotionProperty> = emptyMap(),
)

/** 페이지 속성 값이며 속성 종류에 맞는 필드만 채워져 옴 */
@Serializable
data class NotionProperty(
    val title: List<NotionRichText>? = null,
    @SerialName("rich_text") val richText: List<NotionRichText>? = null,
    val select: NotionSelect? = null,
    val checkbox: Boolean? = null,
    val date: NotionDate? = null,
)

/** 리치 텍스트 한 조각 */
@Serializable
data class NotionRichText(@SerialName("plain_text") val plainText: String? = null)

/** 선택 값이며 선택하지 않았으면 속성의 select가 null로 옴 */
@Serializable
data class NotionSelect(val name: String? = null)

/**
 * 날짜 값
 *
 * [start]는 `yyyy-MM-dd`, 오프셋이 있는 시각, 오프셋 없는 지역 시각 중 하나이며 지역 시각이면 [timeZone]에 시간대 ID가 옴.
 */
@Serializable
data class NotionDate(
    val start: String? = null,
    @SerialName("time_zone") val timeZone: String? = null,
)
