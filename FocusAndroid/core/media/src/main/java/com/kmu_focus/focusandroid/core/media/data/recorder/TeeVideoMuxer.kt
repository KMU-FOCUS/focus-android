package com.kmu_focus.focusandroid.core.media.data.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * 두 개의 [RealTimeRecorder.VideoMuxer] 에 동일한 sample 을 동시에 박는 wrapper.
 * 예: SRT(라이브 송출) + MP4(로컬 저장) 동시 운영.
 *
 * trackIndex 는 primary muxer 기준으로 반환. secondary muxer 의 trackIndex 는 내부에서 mapping 관리.
 * primary 가 실패해도 secondary 가 살아있으면 송출은 계속 (둘 다 stopAndRelease 시 양쪽 닫음).
 */
class TeeVideoMuxer(
    private val primary: RealTimeRecorder.VideoMuxer,
    private val secondary: RealTimeRecorder.VideoMuxer,
    private val secondaryTag: String = "TeeMuxer",
) : RealTimeRecorder.VideoMuxer {

    private val trackMapping = mutableMapOf<Int, Int>()
    private var secondaryFailed = false

    override fun addTrack(format: MediaFormat): Int {
        val primaryIndex = primary.addTrack(format)
        runCatching {
            val secondaryIndex = secondary.addTrack(format)
            trackMapping[primaryIndex] = secondaryIndex
        }.onFailure {
            Log.w(secondaryTag, "secondary addTrack failed, disabling secondary", it)
            secondaryFailed = true
        }
        return primaryIndex
    }

    override fun start() {
        primary.start()
        if (!secondaryFailed) {
            runCatching { secondary.start() }.onFailure {
                Log.w(secondaryTag, "secondary start failed, disabling secondary", it)
                secondaryFailed = true
                runCatching { secondary.releaseQuietly() }
            }
        }
    }

    override fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, info: MediaCodec.BufferInfo) {
        primary.writeSampleData(trackIndex, byteBuf, info)
        if (!secondaryFailed) {
            val secondaryTrack = trackMapping[trackIndex] ?: return
            val bufferCopy = duplicateForSecondary(byteBuf, info)
            val infoCopy = MediaCodec.BufferInfo().apply {
                set(0, info.size, info.presentationTimeUs, info.flags)
            }
            runCatching {
                secondary.writeSampleData(secondaryTrack, bufferCopy, infoCopy)
            }.onFailure {
                Log.w(secondaryTag, "secondary writeSampleData failed, disabling secondary", it)
                secondaryFailed = true
                runCatching { secondary.releaseQuietly() }
            }
        }
    }

    override fun stopAndRelease() {
        runCatching { primary.stopAndRelease() }.onFailure {
            Log.w(secondaryTag, "primary stopAndRelease failed", it)
        }
        if (!secondaryFailed) {
            runCatching { secondary.stopAndRelease() }.onFailure {
                Log.w(secondaryTag, "secondary stopAndRelease failed", it)
            }
        }
    }

    override fun releaseQuietly() {
        runCatching { primary.releaseQuietly() }
        if (!secondaryFailed) {
            runCatching { secondary.releaseQuietly() }
        }
    }

    /**
     * MediaCodec output buffer 는 두 muxer가 동시에 read 하면 position 충돌이 가능하므로
     * secondary 용 ByteBuffer 를 별도로 duplicate해서 자신만의 position 사용.
     */
    private fun duplicateForSecondary(byteBuf: ByteBuffer, info: MediaCodec.BufferInfo): ByteBuffer {
        return byteBuf.duplicate().apply {
            position(info.offset)
            limit(info.offset + info.size)
        }
    }
}

/**
 * 두 [RealTimeRecorder.VideoMuxerFactory] 를 합쳐 [TeeVideoMuxer] 를 생성하는 factory.
 *
 * @param primaryFactory primary muxer factory (예: SRT). primaryFactory 가 받은 outputFile 그대로 사용.
 * @param secondaryFactory secondary muxer factory (예: 로컬 MP4). secondaryOutputFileProvider 가 반환하는 파일을 사용.
 * @param secondaryOutputFileProvider primary 의 outputFile 을 받아 secondary 의 outputFile 을 반환하는 lambda.
 */
class TeeVideoMuxerFactory(
    private val primaryFactory: RealTimeRecorder.VideoMuxerFactory,
    private val secondaryFactory: RealTimeRecorder.VideoMuxerFactory,
    private val secondaryOutputFileProvider: (File) -> File,
) : RealTimeRecorder.VideoMuxerFactory {

    override fun create(outputFile: File): RealTimeRecorder.VideoMuxer {
        val secondaryFile = secondaryOutputFileProvider(outputFile)
        val primary = primaryFactory.create(outputFile)
        val secondary = secondaryFactory.create(secondaryFile)
        return TeeVideoMuxer(primary = primary, secondary = secondary)
    }
}
