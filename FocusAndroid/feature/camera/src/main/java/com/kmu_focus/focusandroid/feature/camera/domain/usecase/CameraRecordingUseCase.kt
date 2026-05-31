package com.kmu_focus.focusandroid.feature.camera.domain.usecase

import com.kmu_focus.focusandroid.core.media.domain.entity.EncoderConfig
import com.kmu_focus.focusandroid.core.media.api.recorder.EncoderSurfaceHandle
import com.kmu_focus.focusandroid.core.media.api.recorder.VideoMuxerFactory
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraRecordingRepository
import java.io.File
import javax.inject.Inject

class CameraRecordingUseCase @Inject constructor(
    private val cameraRecordingRepository: CameraRecordingRepository,
) {
    fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (EncoderSurfaceHandle, Int, Int) -> Unit,
    ): Result<File> = runCatching {
        cameraRecordingRepository.startRecording(
            width = width,
            height = height,
            onSurfaceReady = onSurfaceReady,
        )
    }

    fun startBroadcastRecording(
        width: Int,
        height: Int,
        muxerFactory: VideoMuxerFactory,
        onSurfaceReady: (EncoderSurfaceHandle, Int, Int) -> Unit,
        encoderConfig: EncoderConfig? = null,
    ): Result<Unit> = runCatching {
        cameraRecordingRepository.startBroadcastRecording(
            width = width,
            height = height,
            muxerFactory = muxerFactory,
            onSurfaceReady = onSurfaceReady,
            encoderConfig = encoderConfig,
        )
    }

    fun startOriginalClipBuffer(
        width: Int,
        height: Int,
        onSurfaceReady: (EncoderSurfaceHandle, Int, Int) -> Unit,
        encoderConfig: EncoderConfig? = null,
    ): Result<Unit> = runCatching {
        cameraRecordingRepository.startOriginalClipBuffer(
            width = width,
            height = height,
            onSurfaceReady = onSurfaceReady,
            encoderConfig = encoderConfig,
        )
    }

    suspend fun saveOriginalClipToGallery(): Result<String> = runCatching {
        cameraRecordingRepository.saveOriginalClipToGallery()
    }

    fun stopOriginalClipBuffer(): Result<Unit> = runCatching {
        cameraRecordingRepository.stopOriginalClipBuffer()
    }

    fun stopRecording(): Result<Unit> = runCatching {
        cameraRecordingRepository.stopRecording()
    }
}
