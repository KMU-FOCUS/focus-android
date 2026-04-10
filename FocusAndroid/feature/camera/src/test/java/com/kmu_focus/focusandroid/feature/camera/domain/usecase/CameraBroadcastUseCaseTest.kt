package com.kmu_focus.focusandroid.feature.camera.domain.usecase

import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraStreamRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraBroadcastUseCaseTest {

    private lateinit var streamRepository: CameraStreamRepository
    private lateinit var useCase: CameraBroadcastUseCase

    @Before
    fun setup() {
        streamRepository = mockk()
        useCase = CameraBroadcastUseCase(streamRepository)
    }

    @Test
    fun `startStreaming 성공 시 Result success를 반환한다`() = runTest {
        coEvery { streamRepository.connect("rtmp://server/live/stream-key-abc") } returns Result.success(Unit)

        val result = useCase.startStreaming("rtmp://server/live/stream-key-abc")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { streamRepository.connect("rtmp://server/live/stream-key-abc") }
    }

    @Test
    fun `startStreaming 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { streamRepository.connect(any()) } returns Result.failure(RuntimeException("연결 실패"))

        val result = useCase.startStreaming("rtmp://server/live/invalid")

        assertTrue(result.isFailure)
    }

    @Test
    fun `빈 rtmpUrl로 startStreaming 시 Result failure를 반환한다`() = runTest {
        val result = useCase.startStreaming("")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { streamRepository.connect(any()) }
    }

    @Test
    fun `stopStreaming 성공 시 Result success를 반환한다`() = runTest {
        coEvery { streamRepository.disconnect() } returns Result.success(Unit)

        val result = useCase.stopStreaming()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { streamRepository.disconnect() }
    }

    @Test
    fun `stopStreaming 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { streamRepository.disconnect() } returns Result.failure(RuntimeException("해제 실패"))

        val result = useCase.stopStreaming()

        assertTrue(result.isFailure)
    }

    @Test
    fun `isStreaming은 현재 스트리밍 상태를 반환한다`() {
        every { streamRepository.isStreaming() } returns true

        assertTrue(useCase.isStreaming())
    }
}
