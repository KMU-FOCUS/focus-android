package com.kmu_focus.focusandroid.feature.broadcast.domain.entity

enum class BroadcastStatus {
    READY,
    ON_AIR,
    ENDED,
    ERROR,
    ;

    companion object {
        fun from(value: String?): BroadcastStatus {
            return entries.firstOrNull { it.name == value?.trim()?.uppercase() } ?: ERROR
        }
    }
}
