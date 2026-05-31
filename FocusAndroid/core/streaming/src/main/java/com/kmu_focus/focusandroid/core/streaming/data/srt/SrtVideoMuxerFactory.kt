package com.kmu_focus.focusandroid.core.streaming.data.srt

import com.kmu_focus.focusandroid.core.media.api.recorder.VideoMuxer
import com.kmu_focus.focusandroid.core.media.api.recorder.VideoMuxerFactory
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import java.io.File

class SrtVideoMuxerFactory(
    private val config: SrtConnectionConfig,
    private val socketFactory: () -> SrtSocket = ::DefaultSrtSocket,
    private val packetizerFactory: () -> MpegTsPacketizer = ::MpegTsPacketizer,
) : VideoMuxerFactory {

    override fun create(outputFile: File): VideoMuxer {
        return SrtVideoMuxer(
            srtSocket = socketFactory(),
            packetizer = packetizerFactory(),
            config = config,
        )
    }
}
