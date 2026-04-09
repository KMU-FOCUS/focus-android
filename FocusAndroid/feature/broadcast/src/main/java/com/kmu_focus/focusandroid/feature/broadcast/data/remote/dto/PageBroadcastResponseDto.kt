package com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto

data class PageBroadcastResponseDto(
    val content: List<BroadcastResponseDto>,
    val totalElements: Long,
    val totalPages: Int,
    val size: Int,
    val number: Int,
    val sort: SortObjectDto? = null,
    val numberOfElements: Int? = null,
    val pageable: PageableObjectDto? = null,
    val first: Boolean,
    val last: Boolean,
    val empty: Boolean,
)

data class PageableObjectDto(
    val offset: Long? = null,
    val sort: SortObjectDto? = null,
    val paged: Boolean? = null,
    val pageNumber: Int? = null,
    val pageSize: Int? = null,
    val unpaged: Boolean? = null,
)

data class SortObjectDto(
    val empty: Boolean? = null,
    val sorted: Boolean? = null,
    val unsorted: Boolean? = null,
)
