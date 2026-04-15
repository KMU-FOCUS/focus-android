package com.kmu_focus.focusandroid.core.streaming.data.srt

import io.github.thibaultbee.srtdroid.core.enums.SockOpt

interface SrtSocket {
    fun connect(
        host: String,
        port: Int,
        streamId: String,
    ): Boolean

    fun send(data: ByteArray): Int

    fun close()
}

internal class DefaultSrtSocket : SrtSocket {
    private val socket = io.github.thibaultbee.srtdroid.core.models.SrtSocket()

    override fun connect(
        host: String,
        port: Int,
        streamId: String,
    ): Boolean {
        return runCatching {
            socket.setSockFlag(SockOpt.STREAMID, streamId)
            socket.connect(host, port)
            true
        }.getOrDefault(false)
    }

    override fun send(data: ByteArray): Int {
        return runCatching { socket.send(data) }.getOrDefault(-1)
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
