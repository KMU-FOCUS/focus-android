package com.kmu_focus.focusandroid.core.streaming.data.srt

import android.media.MediaCodec
import android.media.MediaFormat
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import java.nio.ByteBuffer

class SrtVideoMuxer(
    private val srtSocket: SrtSocket,
    private val packetizer: MpegTsPacketizer,
    private val config: SrtConnectionConfig,
) : RealTimeRecorder.VideoMuxer {

    private val trackTypes = mutableMapOf<Int, TrackType>()
    private var nextTrackIndex = 0
    private var started = false

    override fun addTrack(format: MediaFormat): Int {
        val mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val trackType = when {
            mimeType.startsWith("video/") -> TrackType.VIDEO
            mimeType.startsWith("audio/") -> TrackType.AUDIO
            else -> throw IllegalArgumentException("Unsupported track mime type: $mimeType")
        }
        return nextTrackIndex++.also { trackIndex ->
            trackTypes[trackIndex] = trackType
        }
    }

    override fun start() {
        if (started) {
            return
        }
        val connected = srtSocket.connect(config.host, config.port, config.streamId)
        if (!connected) {
            throw IllegalStateException("Failed to connect to SRT endpoint")
        }
        srtSocket.send(packetizer.generatePAT())
        srtSocket.send(packetizer.generatePMT())
        started = true
    }

    override fun writeSampleData(
        trackIndex: Int,
        byteBuf: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        if (!started) {
            return
        }

        val trackType = trackTypes[trackIndex] ?: return
        val sampleData = extractSampleData(byteBuf, info)
        if (sampleData.isEmpty()) {
            return
        }
        val packets = when (trackType) {
            TrackType.VIDEO -> packetizer.packetizeVideo(sampleData, info.presentationTimeUs)
            TrackType.AUDIO -> packetizer.packetizeAudio(sampleData, info.presentationTimeUs)
        }
        packets.forEach { packet ->
            srtSocket.send(packet)
        }
    }

    override fun stopAndRelease() {
        started = false
        srtSocket.close()
    }

    override fun releaseQuietly() {
        runCatching { stopAndRelease() }
    }

    private fun extractSampleData(
        byteBuf: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ): ByteArray {
        val duplicate = byteBuf.duplicate()
        val start = info.offset.coerceAtLeast(0)
        val size = if (info.size > 0) info.size else duplicate.remaining()
        val end = (start + size).coerceAtMost(duplicate.limit())
        duplicate.position(start)
        duplicate.limit(end)
        return ByteArray(duplicate.remaining()).also(duplicate::get)
    }

    private enum class TrackType {
        VIDEO,
        AUDIO,
    }
}
