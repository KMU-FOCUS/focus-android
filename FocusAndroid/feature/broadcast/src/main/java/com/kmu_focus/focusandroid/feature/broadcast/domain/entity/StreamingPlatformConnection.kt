package com.kmu_focus.focusandroid.feature.broadcast.domain.entity

data class StreamingPlatformConnection(
    val outputMode: BroadcastOutputMode,
    val connected: Boolean,
    val channelName: String? = null,
    val watchUrl: String? = null,
)
