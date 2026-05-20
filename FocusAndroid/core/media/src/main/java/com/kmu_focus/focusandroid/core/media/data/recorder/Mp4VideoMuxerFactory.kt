package com.kmu_focus.focusandroid.core.media.data.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * 표준 Android [MediaMuxer] 로 MP4 파일에 인코딩 sample 을 박는 muxer factory.
 * SRT 송출과 동시에 로컬 MP4 저장이 필요할 때 [TeeVideoMuxerFactory] 와 함께 사용.
 */
class Mp4VideoMuxerFactory : RealTimeRecorder.VideoMuxerFactory {
    override fun create(outputFile: File): RealTimeRecorder.VideoMuxer {
        val muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        return object : RealTimeRecorder.VideoMuxer {
            override fun addTrack(format: MediaFormat): Int = muxer.addTrack(format)

            override fun start() {
                muxer.start()
            }

            override fun writeSampleData(
                trackIndex: Int,
                byteBuf: ByteBuffer,
                info: MediaCodec.BufferInfo,
            ) {
                muxer.writeSampleData(trackIndex, byteBuf, info)
            }

            override fun stopAndRelease() {
                try {
                    muxer.stop()
                } finally {
                    muxer.release()
                }
            }

            override fun releaseQuietly() {
                try {
                    muxer.release()
                } catch (_: Exception) {
                }
            }
        }
    }
}
