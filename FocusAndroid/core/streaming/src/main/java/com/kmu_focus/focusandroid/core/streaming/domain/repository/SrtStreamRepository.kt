package com.kmu_focus.focusandroid.core.streaming.domain.repository

import com.kmu_focus.focusandroid.core.media.api.recorder.VideoMuxerFactory
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import kotlinx.coroutines.flow.StateFlow

interface SrtStreamRepository {
    val connectionState: StateFlow<SrtConnectionState>

    fun createMuxerFactory(config: SrtConnectionConfig): VideoMuxerFactory

    suspend fun disconnect()
}
