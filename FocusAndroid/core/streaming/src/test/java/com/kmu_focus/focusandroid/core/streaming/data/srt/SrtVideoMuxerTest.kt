package com.kmu_focus.focusandroid.core.streaming.data.srt

import android.media.MediaCodec
import android.media.MediaFormat
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class SrtVideoMuxerTest {

    private lateinit var srtSocket: SrtSocket
    private lateinit var packetizer: MpegTsPacketizer
    private lateinit var muxer: SrtVideoMuxer

    private val config = SrtConnectionConfig(
        host = "13.125.126.120",
        port = 8890,
        streamKey = "test-stream-key",
    )

    @Before
    fun setup() {
        srtSocket = mockk(relaxed = true)
        packetizer = mockk(relaxed = true)
        muxer = SrtVideoMuxer(srtSocket, packetizer, config)
    }

    @Test
    fun `addTrack는 비디오 트랙 인덱스를 반환한다`() {
        val format = mockk<MediaFormat>(relaxed = true)
        every { format.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_VIDEO_AVC

        val trackIndex = muxer.addTrack(format)

        assert(trackIndex >= 0)
    }

    @Test
    fun `addTrack는 오디오 트랙 인덱스를 반환한다`() {
        val format = mockk<MediaFormat>(relaxed = true)
        every { format.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_AUDIO_AAC

        val trackIndex = muxer.addTrack(format)

        assert(trackIndex >= 0)
    }

    @Test
    fun `start 호출 시 SRT 소켓 연결을 시도한다`() {
        every { srtSocket.connect(config.host, config.port, config.streamId) } returns true

        muxer.start()

        verify(exactly = 1) { srtSocket.connect(config.host, config.port, config.streamId) }
    }

    @Test
    fun `writeSampleData 호출 시 MPEG-TS 패킷화 후 SRT로 전송한다`() {
        val videoFormat = mockk<MediaFormat>(relaxed = true)
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_VIDEO_AVC
        val trackIndex = muxer.addTrack(videoFormat)

        every { srtSocket.connect(any(), any(), any()) } returns true
        muxer.start()

        val buffer = ByteBuffer.wrap(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65))
        val info = MediaCodec.BufferInfo().apply {
            set(0, 5, 100_000L, 0)
        }
        val tsPackets = listOf(ByteArray(188))
        every { packetizer.packetizeVideo(any(), any()) } returns tsPackets

        muxer.writeSampleData(trackIndex, buffer, info)

        verify { packetizer.packetizeVideo(any(), eq(100_000L)) }
        verify { srtSocket.send(any()) }
    }

    @Test
    fun `stopAndRelease 호출 시 SRT 소켓을 닫는다`() {
        every { srtSocket.connect(any(), any(), any()) } returns true
        muxer.start()

        muxer.stopAndRelease()

        verify(exactly = 1) { srtSocket.close() }
    }

    @Test
    fun `releaseQuietly 호출 시 예외 없이 정리한다`() {
        every { srtSocket.close() } throws RuntimeException("이미 닫힘")

        // 예외가 발생하지 않아야 함
        muxer.releaseQuietly()
    }
}
