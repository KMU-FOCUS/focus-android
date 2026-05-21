package com.kmu_focus.focusandroid.feature.account.data.remote.dto

import com.kmu_focus.focusandroid.feature.account.domain.entity.YoutubeConnectionStatus

data class YoutubeConnectionStatusResponseDto(
    val connected: Boolean,
    val channelId: String? = null,
    val channelName: String? = null,
    val watchUrl: String? = null,
    val accessTokenExpiresAt: String? = null,
    val connectedAt: String? = null,
)

fun YoutubeConnectionStatusResponseDto.toEntity(): YoutubeConnectionStatus {
    return YoutubeConnectionStatus(
        connected = connected,
        channelId = channelId,
        channelName = channelName,
        watchUrl = watchUrl,
        accessTokenExpiresAt = accessTokenExpiresAt,
        connectedAt = connectedAt,
    )
}
