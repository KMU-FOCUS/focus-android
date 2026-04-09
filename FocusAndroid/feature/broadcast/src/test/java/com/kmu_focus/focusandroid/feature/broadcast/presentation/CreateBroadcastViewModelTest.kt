package com.kmu_focus.focusandroid.feature.broadcast.presentation

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.CreateBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.StartBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.presentation.create.CreateBroadcastViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateBroadcastViewModelTest {

    private lateinit var createBroadcastUseCase: CreateBroadcastUseCase
    private lateinit var startBroadcastUseCase: StartBroadcastUseCase
    private lateinit var viewModel: CreateBroadcastViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    private val createdBroadcast = Broadcast(
        broadcastId = "broadcast-1",
        title = "테스트 방송",
        memberName = "홍길동",
        memberId = "member-1",
        status = BroadcastStatus.READY,
        streamKey = "stream-key-abc",
        hlsUrl = null,
        startedAt = null,
        endedAt = null,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        createBroadcastUseCase = mockk()
        startBroadcastUseCase = mockk()
        viewModel = CreateBroadcastViewModel(createBroadcastUseCase, startBroadcastUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태는 빈 제목과 아바타이다`() {
        val state = viewModel.uiState.value
        assertEquals("", state.title)
        assertEquals("", state.avatarId)
        assertFalse(state.isCreating)
        assertNull(state.createdBroadcast)
        assertNull(state.error)
    }

    @Test
    fun `제목 변경 시 uiState가 업데이트된다`() {
        viewModel.updateTitle("새 방송 제목")

        assertEquals("새 방송 제목", viewModel.uiState.value.title)
    }

    @Test
    fun `아바타 선택 시 uiState가 업데이트된다`() {
        viewModel.selectAvatar("avatar-a")

        assertEquals("avatar-a", viewModel.uiState.value.avatarId)
    }

    @Test
    fun `방송 생성 성공 시 createdBroadcast가 설정된다`() = runTest {
        coEvery { createBroadcastUseCase("테스트 방송") } returns Result.success(createdBroadcast)

        viewModel.updateTitle("테스트 방송")
        viewModel.createBroadcast()

        val state = viewModel.uiState.value
        assertNotNull(state.createdBroadcast)
        assertEquals("broadcast-1", state.createdBroadcast?.broadcastId)
        assertEquals("stream-key-abc", state.createdBroadcast?.streamKey)
        assertFalse(state.isCreating)
    }

    @Test
    fun `방송 생성 실패 시 error가 설정된다`() = runTest {
        coEvery { createBroadcastUseCase(any()) } returns Result.failure(RuntimeException("생성 실패"))

        viewModel.updateTitle("테스트 방송")
        viewModel.createBroadcast()

        val state = viewModel.uiState.value
        assertNull(state.createdBroadcast)
        assertEquals("생성 실패", state.error)
        assertFalse(state.isCreating)
    }

    @Test
    fun `빈 제목으로 방송 생성 시 error가 설정된다`() = runTest {
        viewModel.createBroadcast()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        coVerify(exactly = 0) { createBroadcastUseCase(any()) }
    }

    @Test
    fun `방송 생성 후 시작 성공 시 ON_AIR 상태가 된다`() = runTest {
        val startedBroadcast = createdBroadcast.copy(
            status = BroadcastStatus.ON_AIR,
            hlsUrl = "https://cdn.example.com/live/broadcast-1.m3u8",
        )
        coEvery { createBroadcastUseCase("테스트 방송") } returns Result.success(createdBroadcast)
        coEvery { startBroadcastUseCase("broadcast-1", "avatar-a") } returns Result.success(startedBroadcast)

        viewModel.updateTitle("테스트 방송")
        viewModel.selectAvatar("avatar-a")
        viewModel.createBroadcast()
        viewModel.startBroadcast()

        val state = viewModel.uiState.value
        assertEquals(BroadcastStatus.ON_AIR, state.createdBroadcast?.status)
    }
}
