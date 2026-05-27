package com.kmu_focus.focusandroid.feature.broadcast.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastSrtInputProfileTest {

    @Test
    fun `SRT 입력 프로파일은 720p 30fps H264 조건을 사용한다`() {
        assertEquals(1280, BroadcastSrtInputProfile.WIDTH)
        assertEquals(720, BroadcastSrtInputProfile.HEIGHT)
        assertEquals(30, BroadcastSrtInputProfile.FRAME_RATE)
        assertEquals("video/avc", BroadcastSrtInputProfile.VIDEO_CODEC_MIME)
    }

    @Test
    fun `SRT 입력 비트레이트와 키프레임 간격은 요청 범위 안이다`() {
        val encoderConfig = BroadcastSrtInputProfile.encoderConfig

        assertEquals(6_000_000, encoderConfig.bitrate)
        assertTrue(encoderConfig.bitrate in 4_000_000..6_000_000)
        assertTrue(encoderConfig.iFrameIntervalSec in 1..2)
        assertEquals(BroadcastSrtInputProfile.FRAME_RATE, encoderConfig.frameRate)
    }
}
