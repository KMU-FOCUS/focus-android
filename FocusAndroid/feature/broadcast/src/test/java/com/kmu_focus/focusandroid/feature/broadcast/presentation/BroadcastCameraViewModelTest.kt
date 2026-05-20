package com.kmu_focus.focusandroid.feature.broadcast.presentation

import androidx.lifecycle.SavedStateHandle
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.BroadcastStreamingUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.CreateBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.DeleteBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.BroadcastCameraViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastCameraViewModelTest {

    private lateinit var createBroadcastUseCase: CreateBroadcastUseCase
    private lateinit var deleteBroadcastUseCase: DeleteBroadcastUseCase
    private lateinit var broadcastStreamingUseCase: BroadcastStreamingUseCase
    private lateinit var viewModel: BroadcastCameraViewModel

    private val testDispatcher = UnconfinedTestDispatcher()
    private val preparedBroadcast = Broadcast(
        broadcastId = "broadcast-1",
        title = "테스트 방송",
        status = BroadcastStatus.READY,
        streamKey = "stream-key-abc",
        hlsUrl = "https://cdn.example.com/live.m3u8",
        memberName = "tester",
        memberId = "member-1",
        startedAt = null,
        endedAt = null,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        createBroadcastUseCase = mockk()
        deleteBroadcastUseCase = mockk()
        broadcastStreamingUseCase = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태는 세션 없는 라이브 홈이다`() {
        viewModel = BroadcastCameraViewModel(
            createBroadcastUseCase = createBroadcastUseCase,
            deleteBroadcastUseCase = deleteBroadcastUseCase,
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            savedStateHandle = SavedStateHandle(),
        )

        val state = viewModel.uiState.value
        assertEquals("", state.broadcastId)
        assertEquals("", state.streamKey)
        assertFalse(state.isBroadcasting)
        assertFalse(state.isPreparing)
        assertFalse(state.isStopping)
        assertEquals(SrtConnectionState.DISCONNECTED, state.srtState)
        assertNull(state.error)
    }

    @Test
    fun `startBroadcasting 성공 시 방송을 생성하고 준비 상태로 진입한다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery {
            broadcastStreamingUseCase.prepareBroadcastStreaming(
                streamKey = preparedBroadcast.streamKey,
                mediaMtxHost = any(),
                mediaMtxPort = any(),
            )
        } returns Result.success(Unit)

        viewModel = BroadcastCameraViewModel(
            createBroadcastUseCase = createBroadcastUseCase,
            deleteBroadcastUseCase = deleteBroadcastUseCase,
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.startBroadcasting()

        val state = viewModel.uiState.value
        assertTrue(state.isPreparing)
        assertFalse(state.isBroadcasting)
        assertEquals("broadcast-1", state.broadcastId)
        assertEquals("stream-key-abc", state.streamKey)
        assertEquals(SrtConnectionState.CONNECTING, state.srtState)
        assertNull(state.error)
    }

    @Test
    fun `startBroadcasting 실패 시 error가 설정된다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.failure(RuntimeException("방송 생성 실패"))

        viewModel = BroadcastCameraViewModel(
            createBroadcastUseCase = createBroadcastUseCase,
            deleteBroadcastUseCase = deleteBroadcastUseCase,
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.startBroadcasting()

        assertFalse(viewModel.uiState.value.isPreparing)
        assertEquals("방송 생성 실패", viewModel.uiState.value.error)
    }

    @Test
    fun `confirmBroadcastStarted 성공 시 방송 중 상태가 된다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery { broadcastStreamingUseCase.prepareBroadcastStreaming(any(), any(), any()) } returns Result.success(Unit)
        coEvery { broadcastStreamingUseCase.confirmBroadcastStarted("broadcast-1") } returns Result.success(
            preparedBroadcast.copy(status = BroadcastStatus.ON_AIR),
        )
        every { broadcastStreamingUseCase.startHeartbeat(eq("broadcast-1"), any()) } returns Job()

        viewModel = BroadcastCameraViewModel(
            createBroadcastUseCase = createBroadcastUseCase,
            deleteBroadcastUseCase = deleteBroadcastUseCase,
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.startBroadcasting()
        viewModel.confirmBroadcastStarted()

        val state = viewModel.uiState.value
        assertTrue(state.isBroadcasting)
        assertFalse(state.isPreparing)
        assertEquals(SrtConnectionState.CONNECTED, state.srtState)
    }

    @Test
    fun `stopBroadcasting 호출 시 방송 종료 후 삭제까지 수행한다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery { broadcastStreamingUseCase.prepareBroadcastStreaming(any(), any(), any()) } returns Result.success(Unit)
        coEvery { broadcastStreamingUseCase.confirmBroadcastStarted("broadcast-1") } returns Result.success(
            preparedBroadcast.copy(status = BroadcastStatus.ON_AIR),
        )
        every { broadcastStreamingUseCase.startHeartbeat(eq("broadcast-1"), any()) } returns Job()
        coEvery { broadcastStreamingUseCase.stopBroadcast("broadcast-1") } returns Result.success(Unit)
        coEvery { deleteBroadcastUseCase.invoke("broadcast-1") } returns Result.success(Unit)

        viewModel = BroadcastCameraViewModel(
            createBroadcastUseCase = createBroadcastUseCase,
            deleteBroadcastUseCase = deleteBroadcastUseCase,
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.startBroadcasting()
        viewModel.confirmBroadcastStarted()
        viewModel.stopBroadcasting()

        val state = viewModel.uiState.value
        assertFalse(state.isBroadcasting)
        assertFalse(state.isPreparing)
        assertFalse(state.isStopping)
        assertEquals("", state.broadcastId)
        assertEquals(SrtConnectionState.DISCONNECTED, state.srtState)
        coVerify(exactly = 1) { broadcastStreamingUseCase.stopBroadcast("broadcast-1") }
        coVerify(exactly = 1) { deleteBroadcastUseCase.invoke("broadcast-1") }
    }

    @Test
    fun `cancelPreparingBroadcast 호출 시 생성된 방송을 삭제한다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery { broadcastStreamingUseCase.prepareBroadcastStreaming(any(), any(), any()) } returns Result.success(Unit)
        coEvery { deleteBroadcastUseCase.invoke("broadcast-1") } returns Result.success(Unit)
        coEvery { broadcastStreamingUseCase.stopPreparedStreaming() } returns Unit

        viewModel = BroadcastCameraViewModel(
            createBroadcastUseCase = createBroadcastUseCase,
            deleteBroadcastUseCase = deleteBroadcastUseCase,
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.startBroadcasting()
        viewModel.cancelPreparingBroadcast()

        assertEquals("", viewModel.uiState.value.broadcastId)
        assertFalse(viewModel.uiState.value.isPreparing)
        coVerify(exactly = 1) { deleteBroadcastUseCase.invoke("broadcast-1") }
    }
}
