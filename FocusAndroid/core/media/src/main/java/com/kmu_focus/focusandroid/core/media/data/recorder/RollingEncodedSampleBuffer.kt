package com.kmu_focus.focusandroid.core.media.data.recorder

internal data class EncodedClipSample(
    val trackIndex: Int,
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int,
    val isVideo: Boolean,
    val isKeyFrame: Boolean,
)

internal data class EncodedClipSnapshot(
    val startPresentationTimeUs: Long,
    val samples: List<EncodedClipSample>,
)

internal class RollingEncodedSampleBuffer(
    private val retentionDurationUs: Long = DEFAULT_RETENTION_DURATION_US,
    private val keyFrameLookbackUs: Long = DEFAULT_KEY_FRAME_LOOKBACK_US,
) {
    private val samples = ArrayDeque<EncodedClipSample>()
    private var latestPresentationTimeUs: Long = Long.MIN_VALUE

    fun append(sample: EncodedClipSample) {
        samples += sample
        latestPresentationTimeUs = maxOf(latestPresentationTimeUs, sample.presentationTimeUs)
        trimExpiredSamples()
    }

    fun snapshot(primaryVideoTrackIndex: Int?): EncodedClipSnapshot? {
        if (samples.isEmpty() || latestPresentationTimeUs == Long.MIN_VALUE) return null

        val cutoffUs = (latestPresentationTimeUs - retentionDurationUs).coerceAtLeast(0L)
        val startUs = resolveStartPresentationTimeUs(
            cutoffUs = cutoffUs,
            primaryVideoTrackIndex = primaryVideoTrackIndex,
        ) ?: return null
        val selectedSamples = samples.filter { it.presentationTimeUs >= startUs }
        if (selectedSamples.none { it.isVideo }) return null

        return EncodedClipSnapshot(
            startPresentationTimeUs = startUs,
            samples = selectedSamples,
        )
    }

    fun clear() {
        samples.clear()
        latestPresentationTimeUs = Long.MIN_VALUE
    }

    private fun trimExpiredSamples() {
        val pruneBeforeUs = latestPresentationTimeUs - retentionDurationUs - keyFrameLookbackUs
        if (pruneBeforeUs <= 0L) return

        while (samples.isNotEmpty() && samples.first().presentationTimeUs < pruneBeforeUs) {
            samples.removeFirst()
        }
    }

    private fun resolveStartPresentationTimeUs(
        cutoffUs: Long,
        primaryVideoTrackIndex: Int?,
    ): Long? {
        val videoSamples = samples.filter { sample ->
            sample.isVideo && (primaryVideoTrackIndex == null || sample.trackIndex == primaryVideoTrackIndex)
        }
        if (videoSamples.isEmpty()) return samples.firstOrNull()?.presentationTimeUs

        val keyFrameBeforeCutoff = videoSamples
            .filter { it.isKeyFrame && it.presentationTimeUs <= cutoffUs }
            .lastOrNull { it.presentationTimeUs >= cutoffUs - keyFrameLookbackUs }
        if (keyFrameBeforeCutoff != null) return keyFrameBeforeCutoff.presentationTimeUs

        val keyFrameAfterCutoff = videoSamples
            .firstOrNull { it.isKeyFrame && it.presentationTimeUs >= cutoffUs }
        if (keyFrameAfterCutoff != null) return keyFrameAfterCutoff.presentationTimeUs

        return videoSamples.firstOrNull { it.presentationTimeUs >= cutoffUs }?.presentationTimeUs
            ?: videoSamples.firstOrNull()?.presentationTimeUs
    }

    companion object {
        const val DEFAULT_RETENTION_DURATION_US: Long = 60_000_000L
        const val DEFAULT_KEY_FRAME_LOOKBACK_US: Long = 2_000_000L
    }
}
