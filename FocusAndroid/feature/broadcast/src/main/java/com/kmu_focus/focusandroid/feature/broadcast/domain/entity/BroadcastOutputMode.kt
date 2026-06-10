package com.kmu_focus.focusandroid.feature.broadcast.domain.entity

enum class BroadcastOutputMode(
    val apiValue: String,
) {
    CHZZK_RTMP("CHZZK_RTMP"),
    YOUTUBE_RTMP("YOUTUBE_RTMP"),
}

fun BroadcastOutputMode.displayTitle(): String = when (this) {
    BroadcastOutputMode.CHZZK_RTMP -> "치지직"
    BroadcastOutputMode.YOUTUBE_RTMP -> "유튜브"
}
