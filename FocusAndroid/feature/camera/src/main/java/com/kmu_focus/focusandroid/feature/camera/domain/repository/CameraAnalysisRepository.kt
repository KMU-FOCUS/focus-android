package com.kmu_focus.focusandroid.feature.camera.domain.repository

import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import com.kmu_focus.focusandroid.feature.camera.domain.entity.OwnerRegistrationResult
import java.nio.ByteBuffer

interface CameraAnalysisRepository {
    fun updateSourceFrameSize(
        width: Int,
        height: Int,
    ) {}

    fun processFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        /**
         * 인코더 PTS와 동일 타임라인의 frame timestamp (microseconds).
         * 미지정 시 timestampMs * 1000 으로 처리. metadata pts_us rebase에 사용.
         */
        timestampUs: Long = timestampMs * 1000L,
    ): ProcessedFrame

    fun registerOwnerFromFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        trackId: Int,
        processedFrame: ProcessedFrame,
    ): OwnerRegistrationResult

    fun clearProcessingThreadCache()

    fun resetSessionState()

    fun startMetadataSession()

    fun startMetadataSession(repository: MetadataRepository)

    fun startMetadataSession(
        repository: MetadataRepository,
        sessionId: String,
    )

    suspend fun closeMetadataSession()

    /**
     * 인코더가 SRT/Muxer로 내보내는 비디오 PTS의 base(첫 sample 기준 microseconds)를 등록.
     * metadata pts_us를 동일한 0-based 타임라인으로 rebase 하기 위해 사용.
     * base가 등록되기 전 frame은 전송하지 않는다.
     */
    fun setEncoderPtsBaseUs(baseUs: Long)
}
