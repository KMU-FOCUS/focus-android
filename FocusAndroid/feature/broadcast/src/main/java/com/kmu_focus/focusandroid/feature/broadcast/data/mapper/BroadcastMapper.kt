package com.kmu_focus.focusandroid.feature.broadcast.data.mapper

import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus

fun BroadcastResponseDto.toEntity(): Broadcast {
    val resolvedStatus = status.toBroadcastStatus()
    val resolvedLiveStatus = liveStatus?.toBroadcastStatusOrNull() ?: resolvedStatus

    return Broadcast(
        broadcastId = broadcastId,
        title = title,
        status = resolvedStatus,
        streamKey = streamKey,
        hlsUrl = hlsUrl,
        memberName = memberName,
        memberId = memberId,
        startedAt = startedAt,
        endedAt = endedAt,
        liveStatus = resolvedLiveStatus,
        platform = platform,
        outputMode = outputMode,
        platformChannelId = platformChannelId,
        watchUrl = watchUrl,
        lastStartFailureReason = lastStartFailureReason,
    )
}

private fun String.toBroadcastStatus(): BroadcastStatus {
    return toBroadcastStatusOrNull() ?: BroadcastStatus.ERROR
}

private fun String.toBroadcastStatusOrNull(): BroadcastStatus? {
    return runCatching { BroadcastStatus.valueOf(this) }.getOrNull()
}
