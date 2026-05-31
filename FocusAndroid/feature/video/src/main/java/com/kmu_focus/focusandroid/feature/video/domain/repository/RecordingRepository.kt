package com.kmu_focus.focusandroid.feature.video.domain.repository

import com.kmu_focus.focusandroid.core.media.api.recorder.EncoderSurfaceHandle
import java.io.File

/**
 * 실시간 녹화 담당. 인코더 surface handle은 onSurfaceReady로 전달.
 */
interface RecordingRepository {
    /** @return 출력 파일 (임시 생성). 실패 시 예외. */
    fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (encoderSurface: EncoderSurfaceHandle, width: Int, height: Int) -> Unit,
        sourceUri: String? = null,
        audioStartPositionMs: Long = 0L,
    ): File

    fun stopRecording()

    val lastRecordingSampleCount: Int
}
