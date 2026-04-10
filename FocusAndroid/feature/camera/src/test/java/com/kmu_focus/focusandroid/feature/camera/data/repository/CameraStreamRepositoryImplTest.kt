package com.kmu_focus.focusandroid.feature.camera.data.repository

import com.kmu_focus.focusandroid.feature.camera.data.stream.RtmpStreamManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraStreamRepositoryImplTest {

    private lateinit var rtmpStreamManager: RtmpStreamManager
    private lateinit var repository: CameraStreamRepositoryImpl

    @Before
    fun setup() {
        rtmpStreamManager = mockk()
        repository = CameraStreamRepositoryImpl(rtmpStreamManager)
    }

    @Test
    fun `connect 성공 시 Result success를 반환한다`() = runTest {
        coEvery { rtmpStreamManager.connect("rtmp://server/live/stream-key-abc") } returns true

        val result = repository.connect("rtmp://server/live/stream-key-abc")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { rtmpStreamManager.connect("rtmp://server/live/stream-key-abc") }
    }

    @Test
    fun `connect 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { rtmpStreamManager.connect(any()) } returns false

        val result = repository.connect("rtmp://server/live/invalid-key")

        assertTrue(result.isFailure)
    }

    @Test
    fun `connect 중 예외 발생 시 Result failure를 반환한다`() = runTest {
        coEvery { rtmpStreamManager.connect(any()) } throws RuntimeException("연결 타임아웃")

        val result = repository.connect("rtmp://server/live/stream-key-abc")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message == "연결 타임아웃")
    }

    @Test
    fun `disconnect 성공 시 Result success를 반환한다`() = runTest {
        coEvery { rtmpStreamManager.disconnect() } returns Unit

        val result = repository.disconnect()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { rtmpStreamManager.disconnect() }
    }

    @Test
    fun `disconnect 중 예외 발생 시 Result failure를 반환한다`() = runTest {
        coEvery { rtmpStreamManager.disconnect() } throws RuntimeException("해제 실패")

        val result = repository.disconnect()

        assertTrue(result.isFailure)
    }

    @Test
    fun `isStreaming은 RtmpStreamManager의 상태를 반환한다`() {
        every { rtmpStreamManager.isStreaming() } returns true

        assertTrue(repository.isStreaming())

        verify(exactly = 1) { rtmpStreamManager.isStreaming() }
    }

    @Test
    fun `스트리밍 중이 아닐 때 isStreaming은 false를 반환한다`() {
        every { rtmpStreamManager.isStreaming() } returns false

        assertFalse(repository.isStreaming())
    }
}
