package com.kmu_focus.focusandroid.feature.camera.data.repository

import com.kmu_focus.focusandroid.core.media.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.core.media.data.recorder.OriginalClipRecorder
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.media.api.recorder.EncoderSurfaceHandle
import com.kmu_focus.focusandroid.core.media.api.recorder.VideoMuxerFactory
import com.kmu_focus.focusandroid.core.media.domain.entity.EncoderConfig
import com.kmu_focus.focusandroid.feature.camera.data.audio.MicAudioSource
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraRecordingRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

class CameraRecordingRepositoryImpl @Inject constructor(
    private val realTimeRecorder: RealTimeRecorder,
    private val originalClipRecorder: OriginalClipRecorder,
    private val videoLocalDataSource: VideoLocalDataSource,
    private val micAudioSourceProvider: Provider<MicAudioSource>,
) : CameraRecordingRepository {

    override fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (EncoderSurfaceHandle, Int, Int) -> Unit,
    ): File {
        val outputFile = videoLocalDataSource.createTempOutputFile()
        val micAudioSource = micAudioSourceProvider.get()

        try {
            realTimeRecorder.start(
                width = width,
                height = height,
                outputFile = outputFile,
                audioTrackSource = micAudioSource,
                onInputSurfaceReady = { surface ->
                    onSurfaceReady(surface, width, height)
                },
            )
        } catch (error: Exception) {
            runCatching { micAudioSource.release() }
            throw error
        }

        return outputFile
    }

    override fun startBroadcastRecording(
        width: Int,
        height: Int,
        muxerFactory: VideoMuxerFactory,
        onSurfaceReady: (EncoderSurfaceHandle, Int, Int) -> Unit,
        encoderConfig: EncoderConfig?,
    ) {
        val outputFile = videoLocalDataSource.createTempOutputFile()
        val micAudioSource = micAudioSourceProvider.get()
        try {
            realTimeRecorder.start(
                width = width,
                height = height,
                outputFile = outputFile,
                bitRate = encoderConfig?.bitrate,
                frameRate = encoderConfig?.frameRate ?: DEFAULT_FRAME_RATE,
                iFrameIntervalSec = encoderConfig?.iFrameIntervalSec,
                audioTrackSource = micAudioSource,
                onInputSurfaceReady = { surface ->
                    onSurfaceReady(surface, width, height)
                },
                muxerFactory = muxerFactory,
            )
        } catch (error: Exception) {
            runCatching { micAudioSource.release() }
            throw error
        }
    }

    override fun startOriginalClipBuffer(
        width: Int,
        height: Int,
        onSurfaceReady: (EncoderSurfaceHandle, Int, Int) -> Unit,
        encoderConfig: EncoderConfig?,
    ) {
        originalClipRecorder.start(
            width = width,
            height = height,
            outputFile = videoLocalDataSource.createTempOutputFile(),
            bitRate = encoderConfig?.bitrate,
            frameRate = encoderConfig?.frameRate ?: DEFAULT_FRAME_RATE,
            iFrameIntervalSec = encoderConfig?.iFrameIntervalSec ?: DEFAULT_CLIP_I_FRAME_INTERVAL_SEC,
            onInputSurfaceReady = { surface ->
                onSurfaceReady(surface, width, height)
            },
        )
    }

    override suspend fun saveOriginalClipToGallery(): String {
        val outputFile = videoLocalDataSource.createTempOutputFile()
        return try {
            originalClipRecorder.saveClip(outputFile)
            videoLocalDataSource.moveToGallery(outputFile)
        } catch (error: Exception) {
            videoLocalDataSource.deleteFile(outputFile)
            throw error
        }
    }

    override fun stopOriginalClipBuffer() {
        originalClipRecorder.stop()
    }

    override fun stopRecording() {
        realTimeRecorder.stop()
    }

    private companion object {
        private const val DEFAULT_FRAME_RATE = 30
        private const val DEFAULT_CLIP_I_FRAME_INTERVAL_SEC = 1
    }
}
