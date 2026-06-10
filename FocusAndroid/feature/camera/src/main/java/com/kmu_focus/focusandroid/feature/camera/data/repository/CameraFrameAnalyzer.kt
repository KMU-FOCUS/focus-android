package com.kmu_focus.focusandroid.feature.camera.data.repository

import com.kmu_focus.focusandroid.core.media.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.core.media.domain.entity.PrivacyMode
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFrameAnalyzer @Inject constructor(
    private val frameProcessor: FrameProcessor,
) {
    fun setPrivacyMode(mode: PrivacyMode) {
        frameProcessor.setPrivacyMode(mode)
    }

    fun processFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        frameIndex: Int?,
    ): ProcessedFrame {
        return frameProcessor.process(
            rgbaBuffer = rgbaBuffer,
            width = width,
            height = height,
            timestampMs = timestampMs,
            frameIndex = frameIndex,
        )
    }

    fun clearProcessingThreadCache() {
        frameProcessor.clearThreadLocalCache()
    }

    fun resetSessionState() {
        frameProcessor.resetSessionState()
    }
}
