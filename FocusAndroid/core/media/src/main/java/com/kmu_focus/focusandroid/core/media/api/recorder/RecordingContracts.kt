package com.kmu_focus.focusandroid.core.media.api.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer

/**
 * 인코더 입력 surface를 feature 계층에 전달하기 위한 typed handle.
 *
 * Android media API를 사용하는 경계임을 명시하면서도 Any 캐스팅 없이 전달한다.
 */
@JvmInline
value class EncoderSurfaceHandle(
    val surface: Surface,
)

interface VideoMuxer {
    fun addTrack(format: MediaFormat): Int
    fun start()
    fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, info: MediaCodec.BufferInfo)
    fun stopAndRelease()
    fun releaseQuietly()
}

fun interface VideoMuxerFactory {
    fun create(outputFile: File): VideoMuxer
}
