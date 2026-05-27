package com.kmu_focus.focusandroid.feature.broadcast.domain.config

import com.kmu_focus.focusandroid.core.media.domain.entity.EncoderConfig

object BroadcastSrtInputProfile {
    const val WIDTH = 1280
    const val HEIGHT = 720
    const val FRAME_RATE = 30
    const val BITRATE = 6_000_000
    const val I_FRAME_INTERVAL_SEC = 1
    const val VIDEO_CODEC_MIME = "video/avc"

    val encoderConfig: EncoderConfig
        get() = EncoderConfig(
            bitrate = BITRATE,
            frameRate = FRAME_RATE,
            iFrameIntervalSec = I_FRAME_INTERVAL_SEC,
        )
}
