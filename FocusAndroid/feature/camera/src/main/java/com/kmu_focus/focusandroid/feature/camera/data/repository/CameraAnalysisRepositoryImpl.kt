package com.kmu_focus.focusandroid.feature.camera.data.repository

import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.core.media.domain.entity.PrivacyMode
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import com.kmu_focus.focusandroid.feature.camera.domain.entity.OwnerRegistrationResult
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraAnalysisRepository
import java.nio.ByteBuffer
import javax.inject.Inject

class CameraAnalysisRepositoryImpl @Inject constructor(
    private val frameAnalyzer: CameraFrameAnalyzer,
    private val ownerEnrollmentManager: OwnerEnrollmentManager,
    private val metadataSessionSynchronizer: CameraMetadataSessionSynchronizer,
) : CameraAnalysisRepository {

    override fun setPrivacyMode(mode: PrivacyMode) {
        frameAnalyzer.setPrivacyMode(mode)
    }

    override fun updateSourceFrameSize(
        width: Int,
        height: Int,
    ) {
        metadataSessionSynchronizer.updateSourceFrameSize(width, height)
    }

    override fun setBroadcastSourceOverride(
        width: Int,
        height: Int,
    ) {
        metadataSessionSynchronizer.setBroadcastSourceOverride(width, height)
    }

    override fun processFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        timestampUs: Long,
    ): ProcessedFrame {
        val frameIndex = metadataSessionSynchronizer.nextFrameIndex()
        val processedFrame = frameAnalyzer.processFrame(
            rgbaBuffer = rgbaBuffer,
            width = width,
            height = height,
            timestampMs = timestampMs,
            frameIndex = frameIndex,
        )
        if (frameIndex != null) {
            metadataSessionSynchronizer.enqueueMetadataFrame(processedFrame, timestampUs)
        }
        return processedFrame
    }

    override fun registerOwnerFromFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        trackId: Int,
        processedFrame: ProcessedFrame,
    ): OwnerRegistrationResult {
        return ownerEnrollmentManager.registerOwnerFromFrame(
            rgbaBuffer = rgbaBuffer,
            width = width,
            height = height,
            trackId = trackId,
            processedFrame = processedFrame,
        )
    }

    override fun removeOwner(
        ownerId: Int,
        trackId: Int,
        thumbnailPath: String?,
    ): Boolean {
        return ownerEnrollmentManager.removeOwner(
            ownerId = ownerId,
            trackId = trackId,
            thumbnailPath = thumbnailPath,
        )
    }

    override fun clearProcessingThreadCache() {
        frameAnalyzer.clearProcessingThreadCache()
    }

    override fun resetSessionState() {
        ownerEnrollmentManager.resetSessionState()
        frameAnalyzer.resetSessionState()
    }

    override fun startMetadataSession() {
        metadataSessionSynchronizer.startMetadataSession()
    }

    override fun startMetadataSession(repository: MetadataRepository) {
        metadataSessionSynchronizer.startMetadataSession(repository = repository)
    }

    override fun startMetadataSession(
        repository: MetadataRepository,
        sessionId: String,
    ) {
        metadataSessionSynchronizer.startMetadataSession(
            repository = repository,
            sessionId = sessionId,
        )
    }

    override suspend fun closeMetadataSession() {
        metadataSessionSynchronizer.closeMetadataSession()
    }

    override fun setEncoderPtsBaseUs(baseUs: Long) {
        metadataSessionSynchronizer.setEncoderPtsBaseUs(baseUs)
    }

    internal fun onVideoSampleWritten(
        rawPtsUs: Long,
        rebasedPtsUs: Long,
    ) {
        metadataSessionSynchronizer.onVideoSampleWritten(
            rawPtsUs = rawPtsUs,
            rebasedPtsUs = rebasedPtsUs,
        )
    }
}
