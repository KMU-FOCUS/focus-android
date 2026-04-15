package com.kmu_focus.focusandroid.core.streaming.data.srt

import java.io.ByteArrayOutputStream
import kotlin.math.min

class MpegTsPacketizer {

    private val continuityCounters = mutableMapOf<Int, Int>()

    fun generatePAT(): ByteArray {
        val sectionWithoutCrc = byteArrayOf(
            0x00,
            0xB0.toByte(), 0x0D,
            0x00, 0x01,
            0xC1.toByte(),
            0x00,
            0x00,
            0x00, 0x01,
            (0xE0 or ((PMT_PID shr 8) and 0x1F)).toByte(),
            (PMT_PID and 0xFF).toByte(),
        )
        return createPsiPacket(PAT_PID, appendCrc(sectionWithoutCrc))
    }

    fun generatePMT(
        videoPid: Int = DEFAULT_VIDEO_PID,
        audioPid: Int = DEFAULT_AUDIO_PID,
    ): ByteArray {
        val sectionWithoutCrc = byteArrayOf(
            0x02,
            0xB0.toByte(), 0x17,
            0x00, 0x01,
            0xC1.toByte(),
            0x00,
            0x00,
            (0xE0 or ((videoPid shr 8) and 0x1F)).toByte(),
            (videoPid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
            0x1B,
            (0xE0 or ((videoPid shr 8) and 0x1F)).toByte(),
            (videoPid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
            0x0F,
            (0xE0 or ((audioPid shr 8) and 0x1F)).toByte(),
            (audioPid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
        )
        return createPsiPacket(PMT_PID, appendCrc(sectionWithoutCrc))
    }

    fun packetizeVideo(
        nalUnit: ByteArray,
        ptsUs: Long,
    ): List<ByteArray> {
        val pesPayload = buildPesPacket(
            streamId = VIDEO_STREAM_ID,
            elementaryStream = nalUnit,
            ptsUs = ptsUs,
            useUnboundedPacketLength = true,
        )
        return packetizePes(pid = DEFAULT_VIDEO_PID, pesPayload = pesPayload)
    }

    fun packetizeAudio(
        aacFrame: ByteArray,
        ptsUs: Long,
    ): List<ByteArray> {
        val pesPayload = buildPesPacket(
            streamId = AUDIO_STREAM_ID,
            elementaryStream = aacFrame,
            ptsUs = ptsUs,
            useUnboundedPacketLength = false,
        )
        return packetizePes(pid = DEFAULT_AUDIO_PID, pesPayload = pesPayload)
    }

    private fun createPsiPacket(
        pid: Int,
        section: ByteArray,
    ): ByteArray {
        val packet = createTsPacketHeader(pid = pid, payloadUnitStart = true)
        packet[4] = 0x00
        System.arraycopy(section, 0, packet, 5, min(section.size, TS_PACKET_SIZE - 5))
        return packet
    }

    private fun buildPesPacket(
        streamId: Int,
        elementaryStream: ByteArray,
        ptsUs: Long,
        useUnboundedPacketLength: Boolean,
    ): ByteArray {
        val header = ByteArrayOutputStream()
        val pts = (ptsUs * 90L) / 1000L
        header.write(byteArrayOf(0x00, 0x00, 0x01, streamId.toByte()))

        val packetLength = if (useUnboundedPacketLength) {
            0
        } else {
            (3 + 5 + elementaryStream.size).coerceAtMost(0xFFFF)
        }
        header.write(byteArrayOf(((packetLength shr 8) and 0xFF).toByte(), (packetLength and 0xFF).toByte()))
        header.write(byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x05))
        header.write(encodePts(pts))
        header.write(elementaryStream)
        return header.toByteArray()
    }

    private fun packetizePes(
        pid: Int,
        pesPayload: ByteArray,
    ): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pesPayload.size) {
            val packet = createTsPacketHeader(
                pid = pid,
                payloadUnitStart = offset == 0,
            )
            val copyLength = min(TS_PACKET_SIZE - TS_HEADER_SIZE, pesPayload.size - offset)
            System.arraycopy(
                pesPayload,
                offset,
                packet,
                TS_HEADER_SIZE,
                copyLength,
            )
            offset += copyLength
            packets += packet
        }
        return packets
    }

    private fun createTsPacketHeader(
        pid: Int,
        payloadUnitStart: Boolean,
    ): ByteArray {
        val packet = ByteArray(TS_PACKET_SIZE) { 0xFF.toByte() }
        val continuityCounter = nextContinuityCounter(pid)
        packet[0] = SYNC_BYTE
        packet[1] = (((if (payloadUnitStart) 0x40 else 0x00) or ((pid shr 8) and 0x1F))).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = (0x10 or continuityCounter).toByte()
        return packet
    }

    private fun nextContinuityCounter(pid: Int): Int {
        val current = continuityCounters[pid] ?: 0
        continuityCounters[pid] = (current + 1) % CONTINUITY_COUNTER_MODULO
        return current
    }

    private fun appendCrc(sectionWithoutCrc: ByteArray): ByteArray {
        val crc = crc32Mpeg(sectionWithoutCrc)
        return sectionWithoutCrc + byteArrayOf(
            ((crc shr 24) and 0xFF).toByte(),
            ((crc shr 16) and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte(),
            (crc and 0xFF).toByte(),
        )
    }

    private fun encodePts(pts: Long): ByteArray {
        val ptsHigh = ((pts shr 30) and 0x07).toInt()
        val ptsMid = ((pts shr 15) and 0x7F).toInt()
        val ptsLow = (pts and 0x7F).toInt()
        return byteArrayOf(
            (0x20 or (ptsHigh shl 1) or 0x01).toByte(),
            ((pts shr 22) and 0xFF).toByte(),
            ((ptsMid shl 1) or 0x01).toByte(),
            ((pts shr 7) and 0xFF).toByte(),
            ((ptsLow shl 1) or 0x01).toByte(),
        )
    }

    private fun crc32Mpeg(data: ByteArray): Int {
        var crc = -0x1
        data.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 24)
            repeat(8) {
                crc = if ((crc and 0x80000000.toInt()) != 0) {
                    (crc shl 1) xor 0x04C11DB7
                } else {
                    crc shl 1
                }
            }
        }
        return crc
    }

    companion object {
        const val TS_PACKET_SIZE = 188
        const val DEFAULT_VIDEO_PID = 0x101
        const val DEFAULT_AUDIO_PID = 0x102

        private const val PAT_PID = 0x0000
        private const val PMT_PID = 0x0100
        private const val VIDEO_STREAM_ID = 0xE0
        private const val AUDIO_STREAM_ID = 0xC0
        private const val TS_HEADER_SIZE = 4
        private const val CONTINUITY_COUNTER_MODULO = 16
        private val SYNC_BYTE = 0x47.toByte()
    }
}
