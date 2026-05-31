package com.kmu_focus.focusandroid.core.media.data.recorder

import android.view.Surface
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OriginalClipRecorder @Inject constructor() {
    private val recorder = RealTimeRecorder(
        loggerTag = "OriginalClipRecorder",
        enableBackgroundDrain = System.getProperty("focus.test.mode") != "true",
    )

    @Volatile
    private var rollingMuxer: RollingMp4ClipMuxer? = null

    @Synchronized
    fun start(
        width: Int,
        height: Int,
        outputFile: File,
        bitRate: Int? = null,
        frameRate: Int = DEFAULT_FRAME_RATE,
        iFrameIntervalSec: Int = DEFAULT_I_FRAME_INTERVAL_SEC,
        onInputSurfaceReady: (Surface) -> Unit,
    ) {
        if (recorder.isRecording) return

        val muxer = RollingMp4ClipMuxer()
        rollingMuxer = muxer
        try {
            recorder.start(
                width = width,
                height = height,
                outputFile = outputFile,
                bitRate = bitRate,
                frameRate = frameRate,
                iFrameIntervalSec = iFrameIntervalSec,
                audioTrackSource = null,
                onInputSurfaceReady = onInputSurfaceReady,
                muxerFactory = RealTimeRecorder.VideoMuxerFactory { muxer },
            )
        } catch (error: Exception) {
            rollingMuxer = null
            throw error
        }
    }

    @Synchronized
    fun saveClip(outputFile: File): File {
        val muxer = rollingMuxer ?: throw IllegalStateException("원본 클립 버퍼가 준비되지 않았습니다")
        return muxer.saveClip(outputFile)
    }

    @Synchronized
    fun stop() {
        recorder.stop()
        rollingMuxer?.releaseQuietly()
        rollingMuxer = null
    }

    private companion object {
        private const val DEFAULT_FRAME_RATE = 30
        private const val DEFAULT_I_FRAME_INTERVAL_SEC = 1
    }
}
