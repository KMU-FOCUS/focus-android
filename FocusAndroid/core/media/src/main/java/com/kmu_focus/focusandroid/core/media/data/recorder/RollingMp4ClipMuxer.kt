package com.kmu_focus.focusandroid.core.media.data.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.kmu_focus.focusandroid.core.media.api.recorder.VideoMuxer
import java.io.File
import java.nio.ByteBuffer

class RollingMp4ClipMuxer(
    retentionDurationUs: Long = RollingEncodedSampleBuffer.DEFAULT_RETENTION_DURATION_US,
    keyFrameLookbackUs: Long = RollingEncodedSampleBuffer.DEFAULT_KEY_FRAME_LOOKBACK_US,
) : VideoMuxer {
    private enum class TrackType {
        Video,
        Audio,
        Other,
    }

    private val sampleBuffer = RollingEncodedSampleBuffer(
        retentionDurationUs = retentionDurationUs,
        keyFrameLookbackUs = keyFrameLookbackUs,
    )
    private val trackFormats = linkedMapOf<Int, MediaFormat>()
    private val trackTypes = linkedMapOf<Int, TrackType>()
    private var nextTrackIndex = 0
    private var started = false
    private var primaryVideoTrackIndex: Int? = null

    @Synchronized
    override fun addTrack(format: MediaFormat): Int {
        val trackType = resolveTrackType(format)
        val trackIndex = nextTrackIndex++
        trackFormats[trackIndex] = format
        trackTypes[trackIndex] = trackType
        if (trackType == TrackType.Video && primaryVideoTrackIndex == null) {
            primaryVideoTrackIndex = trackIndex
        }
        return trackIndex
    }

    @Synchronized
    override fun start() {
        started = true
    }

    @Synchronized
    override fun writeSampleData(
        trackIndex: Int,
        byteBuf: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        if (!started || info.size <= 0) return

        val trackType = trackTypes[trackIndex] ?: TrackType.Other
        val copiedData = copySampleData(byteBuf, info)
        if (copiedData.isEmpty()) return

        sampleBuffer.append(
            EncodedClipSample(
                trackIndex = trackIndex,
                data = copiedData,
                presentationTimeUs = info.presentationTimeUs.coerceAtLeast(0L),
                flags = info.flags,
                isVideo = trackType == TrackType.Video,
                isKeyFrame = trackType == TrackType.Video && info.isKeyFrame(),
            ),
        )
    }

    @Synchronized
    fun saveClip(outputFile: File): File {
        val snapshot = sampleBuffer.snapshot(primaryVideoTrackIndex)
            ?: throw IllegalStateException("저장할 원본 클립이 아직 없습니다")
        val formatSnapshot = LinkedHashMap(trackFormats)
        require(formatSnapshot.isNotEmpty()) { "원본 클립 트랙 정보가 없습니다" }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            val trackIndexMap = formatSnapshot.mapValues { (_, format) ->
                muxer.addTrack(format)
            }
            muxer.start()
            muxerStarted = true

            snapshot.samples.forEach { sample ->
                val outputTrackIndex = trackIndexMap[sample.trackIndex] ?: return@forEach
                val rebasedPtsUs = (sample.presentationTimeUs - snapshot.startPresentationTimeUs)
                    .coerceAtLeast(0L)
                val bufferInfo = MediaCodec.BufferInfo().apply {
                    set(0, sample.data.size, rebasedPtsUs, sample.flags)
                }
                muxer.writeSampleData(
                    outputTrackIndex,
                    ByteBuffer.wrap(sample.data),
                    bufferInfo,
                )
            }

            muxer.stop()
            muxerStarted = false
            return outputFile
        } catch (error: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw error
        } finally {
            runCatching {
                if (muxerStarted) {
                    muxer?.stop()
                }
            }
            runCatching { muxer?.release() }
        }
    }

    @Synchronized
    override fun stopAndRelease() {
        started = false
    }

    @Synchronized
    override fun releaseQuietly() {
        started = false
        sampleBuffer.clear()
    }

    private fun resolveTrackType(format: MediaFormat): TrackType {
        val mimeType = runCatching { format.getString(MediaFormat.KEY_MIME) }
            .getOrNull()
            .orEmpty()
        return when {
            mimeType.startsWith("video/") -> TrackType.Video
            mimeType.startsWith("audio/") -> TrackType.Audio
            else -> TrackType.Other
        }
    }

    private fun copySampleData(
        byteBuf: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ): ByteArray {
        val duplicate = byteBuf.duplicate()
        val start = info.offset.coerceAtLeast(0)
        val end = (start + info.size).coerceAtMost(duplicate.limit())
        if (end <= start) return ByteArray(0)

        duplicate.position(start)
        duplicate.limit(end)
        return ByteArray(duplicate.remaining()).also(duplicate::get)
    }

    private fun MediaCodec.BufferInfo.isKeyFrame(): Boolean {
        return flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
    }
}
