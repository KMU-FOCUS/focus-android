package com.kmu_focus.focusandroid.feature.metadatareview.domain.model

import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata

class ParsedMetadata(
    frames: List<FrameMetadata>,
    coordinateWidth: Int = 0,
    coordinateHeight: Int = 0,
    val fps: Float? = null,
) {
    val frames: List<FrameMetadata> = frames.sortedBy(FrameMetadata::ptsUs)
    val frameCount: Int = this.frames.size
    val totalFaceCount: Int = this.frames.sumOf { it.faces.size }
    val framesWithFacesCount: Int = this.frames.count { it.faces.isNotEmpty() }
    val maxFacesInFrame: Int = this.frames.maxOfOrNull { it.faces.size } ?: 0
    val sessionId: String? = this.frames.firstOrNull()?.sessionId
    val coordinateWidth: Int = coordinateWidth
        .takeIf { it > 0 }
        ?: inferCoordinateWidth(this.frames)
    val coordinateHeight: Int = coordinateHeight
        .takeIf { it > 0 }
        ?: inferCoordinateHeight(this.frames)
    val isPlaybackTimelineNormalized: Boolean

    private val framePtsUs: LongArray

    init {
        val timelineOffsetUs = resolveTimelineOffsetUs(this.frames)
        isPlaybackTimelineNormalized = timelineOffsetUs > 0L
        framePtsUs = this.frames.map { frame ->
            (frame.ptsUs - timelineOffsetUs).coerceAtLeast(0L)
        }.toLongArray()
    }

    fun frameAt(positionMs: Long): FrameMetadata? {
        if (framePtsUs.isEmpty()) return null

        val positionUs = positionMs.coerceAtLeast(0L) * MICROS_PER_MILLISECOND
        val rawIndex = framePtsUs.binarySearch(positionUs)
        val frameIndex = if (rawIndex >= 0) rawIndex else -rawIndex - 2

        if (frameIndex < 0) return null

        val frameStartUs = framePtsUs[frameIndex]
        val frameEndExclusiveUs = if (frameIndex + 1 < framePtsUs.size) {
            framePtsUs[frameIndex + 1]
        } else {
            frameStartUs + LAST_FRAME_VISIBILITY_WINDOW_US
        }

        return if (positionUs in frameStartUs until frameEndExclusiveUs) {
            frames[frameIndex]
        } else {
            null
        }
    }

    private companion object {
        const val MICROS_PER_MILLISECOND = 1_000L
        const val LAST_FRAME_VISIBILITY_WINDOW_US = 100_000L
        const val ABSOLUTE_TIMESTAMP_THRESHOLD_US = 86_400_000_000L

        fun inferCoordinateWidth(frames: List<FrameMetadata>): Int {
            return frames.asSequence()
                .flatMap { it.faces.asSequence() }
                .maxOfOrNull { face -> face.bbox.x + face.bbox.width }
                ?.coerceAtLeast(1)
                ?: 1
        }

        fun inferCoordinateHeight(frames: List<FrameMetadata>): Int {
            return frames.asSequence()
                .flatMap { it.faces.asSequence() }
                .maxOfOrNull { face -> face.bbox.y + face.bbox.height }
                ?.coerceAtLeast(1)
                ?: 1
        }

        fun resolveTimelineOffsetUs(frames: List<FrameMetadata>): Long {
            val firstPtsUs = frames.firstOrNull()?.ptsUs ?: return 0L
            return if (firstPtsUs >= ABSOLUTE_TIMESTAMP_THRESHOLD_US) {
                firstPtsUs
            } else {
                0L
            }
        }
    }
}
