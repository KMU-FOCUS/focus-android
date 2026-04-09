package com.kmu_focus.focusandroid.feature.broadcast.data.mapper

import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus

fun BroadcastResponseDto.toEntity(): Broadcast {
    return Broadcast(
        broadcastId = broadcastId,
        title = title,
        status = when (status.trim().uppercase()) {
            BroadcastStatus.READY.name -> BroadcastStatus.READY
            BroadcastStatus.ON_AIR.name -> BroadcastStatus.ON_AIR
            BroadcastStatus.ENDED.name -> BroadcastStatus.ENDED
            else -> BroadcastStatus.ERROR
        },
        streamKey = streamKey,
        hlsUrl = hlsUrl,
        memberName = memberName,
        memberId = memberId,
        startedAt = startedAt,
        endedAt = endedAt,
    )
}
