package com.kmu_focus.focusandroid.feature.broadcast.presentation

import androidx.lifecycle.SavedStateHandle
import com.kmu_focus.focusandroid.core.metadata.domain.repository.LiveMetadataRepositoryFactory
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisJob
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastFaceStatistics
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastMediaAsset
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAiReport
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastContentRatio
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.BroadcastStreamingUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.CreateBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.DeleteBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.GetBroadcastHighlightsUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.GetLatestBroadcastAnalysisUseCase
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.BroadcastCameraViewModel
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.CompletedBroadcastReportSeed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastCameraViewModelTest {

    private lateinit var createBroadcastUseCase: CreateBroadcastUseCase
    private lateinit var deleteBroadcastUseCase: DeleteBroadcastUseCase
    private lateinit var broadcastStreamingUseCase: BroadcastStreamingUseCase
    private lateinit var getLatestBroadcastAnalysisUseCase: GetLatestBroadcastAnalysisUseCase
    private lateinit var getBroadcastHighlightsUseCase: GetBroadcastHighlightsUseCase
    private lateinit var liveMetadataRepositoryFactory: LiveMetadataRepositoryFactory
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
    private val sampleAnalysisJob = BroadcastAnalysisJob(
        analysisJobId = "job-1",
        broadcastId = "broadcast-1",
        jobType = "FULL_SUMMARY",
        jobStatus = BroadcastAnalysisStatus.SUCCEEDED,
        completedAt = "2026-05-21T00:00:00",
        errorMessage = null,
        createdAt = "2026-05-21T00:00:00",
        mediaAsset = BroadcastMediaAsset(
            mediaAssetId = "asset-1",
            assetType = "ANALYSIS_MP4",
            storageProvider = "LOCAL_FILE",
            storageKey = "android/broadcast-1/recording.mp4",
            storageUrl = null,
            durationSec = 30,
            resolutionWidth = 1280,
            resolutionHeight = 720,
            fileSizeBytes = 1024,
            createdAt = "2026-05-21T00:00:00",
        ),
    )
    private val sampleAnalysisResult = BroadcastAnalysisResult(
        broadcastId = "broadcast-1",
        latestJob = sampleAnalysisJob,
        latestReport = BroadcastAiReport(
            aiReportId = "report-1",
            reportType = "POST_STREAM_SUMMARY",
            title = "FOCUS 방송 리포트",
            summary = "요약",
            strengths = listOf("강점"),
            weaknesses = listOf("약점"),
            actionItems = listOf("액션"),
            viewerPeakInsight = null,
            faceStatistics = BroadcastFaceStatistics(
                totalReplacedFaceCount = 3,
                maxSimultaneousCrowdCount = 2,
            ),
            contentRatios = listOf(
                BroadcastContentRatio(
                    contentType = "토크",
                    percentage = 100.0,
                    durationSec = 30,
                ),
            ),
            createdAt = "2026-05-21T00:00:00",
        ),
        highlightCount = 1,
    )
    private val processingAnalysisResult = sampleAnalysisResult.copy(
        latestJob = sampleAnalysisJob.copy(
            jobStatus = BroadcastAnalysisStatus.PROCESSING,
            completedAt = null,
        ),
        latestReport = null,
    )
    private val failedAnalysisResult = sampleAnalysisResult.copy(
        latestJob = sampleAnalysisJob.copy(
            jobStatus = BroadcastAnalysisStatus.FAILED,
            errorMessage = "분석 실패",
        ),
        latestReport = null,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        createBroadcastUseCase = mockk()
        deleteBroadcastUseCase = mockk()
        broadcastStreamingUseCase = mockk(relaxed = true)
        getLatestBroadcastAnalysisUseCase = mockk()
        getBroadcastHighlightsUseCase = mockk()
        liveMetadataRepositoryFactory = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): BroadcastCameraViewModel {
        return BroadcastCameraViewModel(
            createBroadcastUseCase = createBroadcastUseCase,
            deleteBroadcastUseCase = deleteBroadcastUseCase,
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            getLatestBroadcastAnalysisUseCase = getLatestBroadcastAnalysisUseCase,
            getBroadcastHighlightsUseCase = getBroadcastHighlightsUseCase,
            liveMetadataRepositoryFactory = liveMetadataRepositoryFactory,
            savedStateHandle = savedStateHandle,
        )
    }

    @Test
    fun `createLiveMetadataRepository는 세션 factory에 위임한다`() {
        val metadataRepository = mockk<MetadataRepository>()
        every { liveMetadataRepositoryFactory.create() } returns metadataRepository
        viewModel = createViewModel()

        assertSame(metadataRepository, viewModel.createLiveMetadataRepository())
        verify(exactly = 1) { liveMetadataRepositoryFactory.create() }
    }

    @Test
    fun `초기 상태는 세션 없는 라이브 홈이다`() {
        viewModel = createViewModel()

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
    fun `stream key는 saved state에 저장하지 않고 이전 저장값도 제거한다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery {
            broadcastStreamingUseCase.prepareBroadcastStreaming(
                streamKey = preparedBroadcast.streamKey,
                mediaMtxHost = any(),
                mediaMtxPort = any(),
            )
        } returns Result.success(Unit)
        val savedStateHandle = SavedStateHandle(mapOf("streamKey" to "legacy-stream-key"))
        viewModel = createViewModel(savedStateHandle)

        assertNull(savedStateHandle.get<String>("streamKey"))
        viewModel.startBroadcasting()

        assertEquals("stream-key-abc", viewModel.uiState.value.streamKey)
        assertNull(savedStateHandle.get<String>("streamKey"))
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

        viewModel = createViewModel()

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

        viewModel = createViewModel()

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

        viewModel = createViewModel()

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
        coEvery { getLatestBroadcastAnalysisUseCase.invoke("broadcast-1") } returns Result.success(sampleAnalysisResult)
        coEvery { getBroadcastHighlightsUseCase.invoke("broadcast-1") } returns Result.success(emptyList())

        viewModel = createViewModel()

        viewModel.startBroadcasting()
        viewModel.confirmBroadcastStarted()
        viewModel.stopBroadcasting(
            CompletedBroadcastReportSeed(
                broadcastId = "broadcast-1",
                durationSec = 30,
                ownerCount = 1,
                recordingFilePath = null,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isBroadcasting)
        assertFalse(state.isPreparing)
        assertFalse(state.isStopping)
        assertEquals("", state.broadcastId)
        assertEquals(SrtConnectionState.DISCONNECTED, state.srtState)
        assertTrue(state.completedReport != null)
        assertEquals(BroadcastAnalysisStatus.SUCCEEDED, state.completedReport?.analysisStatus)
        coVerify(exactly = 1) { broadcastStreamingUseCase.stopBroadcast("broadcast-1") }
        coVerify(exactly = 1) { deleteBroadcastUseCase.invoke("broadcast-1") }
    }

    @Test
    fun `stopBroadcasting은 analysis job이 processing이면 polling을 계속하고 succeeded에서 멈춘다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery { broadcastStreamingUseCase.prepareBroadcastStreaming(any(), any(), any()) } returns Result.success(Unit)
        coEvery { broadcastStreamingUseCase.confirmBroadcastStarted("broadcast-1") } returns Result.success(
            preparedBroadcast.copy(status = BroadcastStatus.ON_AIR),
        )
        every { broadcastStreamingUseCase.startHeartbeat(eq("broadcast-1"), any()) } returns Job()
        coEvery { broadcastStreamingUseCase.stopBroadcast("broadcast-1") } returns Result.success(Unit)
        coEvery { deleteBroadcastUseCase.invoke("broadcast-1") } returns Result.success(Unit)
        coEvery { getLatestBroadcastAnalysisUseCase.invoke("broadcast-1") } returnsMany listOf(
            Result.success(processingAnalysisResult),
            Result.success(processingAnalysisResult),
            Result.success(sampleAnalysisResult),
        )
        coEvery { getBroadcastHighlightsUseCase.invoke("broadcast-1") } returns Result.success(emptyList())

        viewModel = createViewModel()

        viewModel.startBroadcasting()
        viewModel.confirmBroadcastStarted()
        viewModel.stopBroadcasting(
            CompletedBroadcastReportSeed(
                broadcastId = "broadcast-1",
                durationSec = 30,
                ownerCount = 1,
                recordingFilePath = null,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BroadcastAnalysisStatus.SUCCEEDED, state.completedReport?.analysisStatus)
        coVerify(exactly = 3) { getLatestBroadcastAnalysisUseCase.invoke("broadcast-1") }
    }

    @Test
    fun `stopBroadcasting은 analysis job이 failed면 polling을 멈추고 실패 UI를 반영한다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery { broadcastStreamingUseCase.prepareBroadcastStreaming(any(), any(), any()) } returns Result.success(Unit)
        coEvery { broadcastStreamingUseCase.confirmBroadcastStarted("broadcast-1") } returns Result.success(
            preparedBroadcast.copy(status = BroadcastStatus.ON_AIR),
        )
        every { broadcastStreamingUseCase.startHeartbeat(eq("broadcast-1"), any()) } returns Job()
        coEvery { broadcastStreamingUseCase.stopBroadcast("broadcast-1") } returns Result.success(Unit)
        coEvery { deleteBroadcastUseCase.invoke("broadcast-1") } returns Result.success(Unit)
        coEvery { getLatestBroadcastAnalysisUseCase.invoke("broadcast-1") } returnsMany listOf(
            Result.success(processingAnalysisResult),
            Result.success(failedAnalysisResult),
        )
        coEvery { getBroadcastHighlightsUseCase.invoke("broadcast-1") } returns Result.success(emptyList())

        viewModel = createViewModel()

        viewModel.startBroadcasting()
        viewModel.confirmBroadcastStarted()
        viewModel.stopBroadcasting(
            CompletedBroadcastReportSeed(
                broadcastId = "broadcast-1",
                durationSec = 30,
                ownerCount = 1,
                recordingFilePath = null,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BroadcastAnalysisStatus.FAILED, state.completedReport?.analysisStatus)
        assertEquals("분석 실패", state.completedReport?.analysisErrorMessage)
        coVerify(exactly = 2) { getLatestBroadcastAnalysisUseCase.invoke("broadcast-1") }
    }

    @Test
    fun `분석 처리 중 리포트 닫기 시 analysis polling을 중지한다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery { broadcastStreamingUseCase.prepareBroadcastStreaming(any(), any(), any()) } returns Result.success(Unit)
        coEvery { broadcastStreamingUseCase.confirmBroadcastStarted("broadcast-1") } returns Result.success(
            preparedBroadcast.copy(status = BroadcastStatus.ON_AIR),
        )
        every { broadcastStreamingUseCase.startHeartbeat(eq("broadcast-1"), any()) } returns Job()
        coEvery { broadcastStreamingUseCase.stopBroadcast("broadcast-1") } returns Result.success(Unit)
        coEvery { getLatestBroadcastAnalysisUseCase.invoke("broadcast-1") } returns Result.success(processingAnalysisResult)

        viewModel = createViewModel()

        viewModel.startBroadcasting()
        viewModel.confirmBroadcastStarted()
        viewModel.stopBroadcasting(
            CompletedBroadcastReportSeed(
                broadcastId = "broadcast-1",
                durationSec = 30,
                ownerCount = 1,
                recordingFilePath = null,
            ),
        )

        assertEquals(BroadcastAnalysisStatus.PROCESSING, viewModel.uiState.value.completedReport?.analysisStatus)
        coVerify(exactly = 1) { getLatestBroadcastAnalysisUseCase.invoke("broadcast-1") }

        viewModel.dismissCompletedReport()
        advanceTimeBy(ANALYSIS_POLL_INTERVAL_TEST_MS * 3)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.completedReport)
        coVerify(exactly = 1) { getLatestBroadcastAnalysisUseCase.invoke("broadcast-1") }
    }

    @Test
    fun `cancelPreparingBroadcast 호출 시 생성된 방송을 삭제한다`() = runTest {
        coEvery { createBroadcastUseCase.invoke(any()) } returns Result.success(preparedBroadcast)
        coEvery { broadcastStreamingUseCase.prepareBroadcastStreaming(any(), any(), any()) } returns Result.success(Unit)
        coEvery { deleteBroadcastUseCase.invoke("broadcast-1") } returns Result.success(Unit)
        coEvery { broadcastStreamingUseCase.stopPreparedStreaming() } returns Unit

        viewModel = createViewModel()

        viewModel.startBroadcasting()
        viewModel.cancelPreparingBroadcast()

        assertEquals("", viewModel.uiState.value.broadcastId)
        assertFalse(viewModel.uiState.value.isPreparing)
        coVerify(exactly = 1) { deleteBroadcastUseCase.invoke("broadcast-1") }
    }

    private companion object {
        private const val ANALYSIS_POLL_INTERVAL_TEST_MS = 2_000L
    }
}
