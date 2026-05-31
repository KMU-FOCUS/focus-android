package com.kmu_focus.focusandroid.core.media.data.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingEncodedSampleBufferTest {

    @Test
    fun `snapshot은 cutoff 직전 키프레임부터 클립을 만든다`() {
        val buffer = RollingEncodedSampleBuffer(
            retentionDurationUs = 60_000_000L,
            keyFrameLookbackUs = 2_000_000L,
        )
        buffer.append(videoSample(0L, keyFrame = true))
        buffer.append(videoSample(59_000_000L, keyFrame = true))
        buffer.append(videoSample(60_500_000L))
        buffer.append(videoSample(119_000_000L))

        val snapshot = buffer.snapshot(primaryVideoTrackIndex = VIDEO_TRACK)

        assertEquals(59_000_000L, snapshot?.startPresentationTimeUs)
        assertEquals(
            listOf(59_000_000L, 60_500_000L, 119_000_000L),
            snapshot?.samples?.map { it.presentationTimeUs },
        )
    }

    @Test
    fun `cutoff 이전 키프레임이 없으면 cutoff 이후 첫 키프레임부터 시작한다`() {
        val buffer = RollingEncodedSampleBuffer(
            retentionDurationUs = 60_000_000L,
            keyFrameLookbackUs = 2_000_000L,
        )
        buffer.append(videoSample(10_000_000L, keyFrame = true))
        buffer.append(videoSample(58_000_000L))
        buffer.append(videoSample(62_000_000L, keyFrame = true))
        buffer.append(videoSample(120_000_000L))

        val snapshot = buffer.snapshot(primaryVideoTrackIndex = VIDEO_TRACK)

        assertEquals(62_000_000L, snapshot?.startPresentationTimeUs)
        assertEquals(
            listOf(62_000_000L, 120_000_000L),
            snapshot?.samples?.map { it.presentationTimeUs },
        )
    }

    @Test
    fun `retention과 키프레임 lookback보다 오래된 샘플은 제거한다`() {
        val buffer = RollingEncodedSampleBuffer(
            retentionDurationUs = 60_000_000L,
            keyFrameLookbackUs = 2_000_000L,
        )
        buffer.append(videoSample(0L, keyFrame = true))
        buffer.append(videoSample(59_500_000L, keyFrame = true))
        buffer.append(videoSample(121_000_000L))

        val snapshot = buffer.snapshot(primaryVideoTrackIndex = VIDEO_TRACK)

        assertTrue(snapshot?.samples?.none { it.presentationTimeUs == 0L } == true)
        assertEquals(59_500_000L, snapshot?.startPresentationTimeUs)
    }

    @Test
    fun `비디오 샘플이 없으면 snapshot은 null이다`() {
        val buffer = RollingEncodedSampleBuffer()

        buffer.append(
            EncodedClipSample(
                trackIndex = 1,
                data = byteArrayOf(1),
                presentationTimeUs = 100_000L,
                flags = 0,
                isVideo = false,
                isKeyFrame = false,
            ),
        )

        assertNull(buffer.snapshot(primaryVideoTrackIndex = VIDEO_TRACK))
    }

    private fun videoSample(
        presentationTimeUs: Long,
        keyFrame: Boolean = false,
    ): EncodedClipSample {
        return EncodedClipSample(
            trackIndex = VIDEO_TRACK,
            data = byteArrayOf(1, 2, 3),
            presentationTimeUs = presentationTimeUs,
            flags = if (keyFrame) 1 else 0,
            isVideo = true,
            isKeyFrame = keyFrame,
        )
    }

    private companion object {
        private const val VIDEO_TRACK = 0
    }
}
