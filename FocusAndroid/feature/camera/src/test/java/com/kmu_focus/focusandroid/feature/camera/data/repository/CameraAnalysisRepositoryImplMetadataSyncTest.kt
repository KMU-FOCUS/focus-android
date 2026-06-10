package com.kmu_focus.focusandroid.feature.camera.data.repository

import android.content.Context
import android.util.Log
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.TrackLabelState
import com.kmu_focus.focusandroid.core.media.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.media.domain.entity.FaceExport
import com.kmu_focus.focusandroid.core.media.domain.entity.FrameExport
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.nio.ByteBuffer
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraAnalysisRepositoryImplMetadataSyncTest {

    private lateinit var frameProcessor: FrameProcessor
    private lateinit var metadataRepository: CapturingMetadataRepository
    private lateinit var repository: CameraAnalysisRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        frameProcessor = mockk()
        metadataRepository = CapturingMetadataRepository()

        every {
            frameProcessor.process(
                rgbaBuffer = any(),
                width = any(),
                height = any(),
                timestampMs = any(),
                frameIndex = any(),
            )
        } answers {
            processedFrame(
                timestampMs = arg(3),
                frameNumber = arg<Int?>(4) ?: 0,
            )
        }

        repository = createRepository(
            metadataRepository = metadataRepository,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createRepository(
        metadataRepository: MetadataRepository,
        ioDispatcher: CoroutineDispatcher,
        realTimeRecorder: RealTimeRecorder = RealTimeRecorder(enableBackgroundDrain = false),
    ): CameraAnalysisRepositoryImpl {
        val context = mockk<Context>(relaxed = true)
        return CameraAnalysisRepositoryImpl(
            frameAnalyzer = CameraFrameAnalyzer(frameProcessor),
            ownerEnrollmentManager = OwnerEnrollmentManager(
                ownerAdder = mockk(relaxed = true),
                trackLabelState = mockk(relaxed = true),
                embeddingExtractor = mockk<ArcFaceEmbeddingExtractor>(relaxed = true),
                context = context,
            ),
            metadataSessionSynchronizer = CameraMetadataSessionSynchronizer(
                metadataRepositoryProvider = Provider { metadataRepository },
                realTimeRecorder = realTimeRecorder,
                context = context,
                ioDispatcher = ioDispatcher,
            ),
        )
    }

    @Test
    fun `recorder callback은 metadata synchronizer에 연결된다`() = runTest {
        val realTimeRecorder = RealTimeRecorder(enableBackgroundDrain = false)
        val repository = createRepository(
            metadataRepository = metadataRepository,
            ioDispatcher = UnconfinedTestDispatcher(),
            realTimeRecorder = realTimeRecorder,
        )
        repository.startMetadataSession(metadataRepository)
        repository.processFrame(frameTimestampUs = 1_000_000L)

        realTimeRecorder.onVideoPtsBaseSet?.invoke(1_000_000L)
        realTimeRecorder.onVideoSampleWritten?.invoke(1_000_000L, 0L)
        repository.closeMetadataSession()

        assertEquals(listOf(0L), metadataRepository.frames.map(FrameMetadata::ptsUs))
    }

    @Test
    fun `metadata 전송이 밀리면 중간 frame을 계속 쌓지 않고 최신 frame만 유지한다`() = runTest {
        val blockingRepository = BlockingMetadataRepository()
        val repository = createRepository(
            metadataRepository = blockingRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.startMetadataSession(blockingRepository)
        repository.setEncoderPtsBaseUs(1_000_000L)
        repository.processFrame(frameTimestampUs = 1_000_000L)
        repository.onVideoSampleWritten(rawPtsUs = 1_000_000L, rebasedPtsUs = 0L)
        repository.processFrame(frameTimestampUs = 1_033_333L)
        repository.onVideoSampleWritten(rawPtsUs = 1_033_333L, rebasedPtsUs = 33_333L)
        repository.processFrame(frameTimestampUs = 1_066_666L)
        repository.onVideoSampleWritten(rawPtsUs = 1_066_666L, rebasedPtsUs = 66_666L)
        runCurrent()

        val startedBeforeFirstSendCompletes = blockingRepository.startedPts.toList()
        blockingRepository.completeFirstSend()
        advanceUntilIdle()
        repository.closeMetadataSession()

        assertEquals(listOf(0L), startedBeforeFirstSendCompletes)
        assertEquals(
            listOf(0L, 66_666L),
            blockingRepository.completedPts,
        )
    }

    @Test
    fun `encoder pts base보다 과거인 pending metadata는 sample 콜백 때 전송하지 않는다`() = runTest {
        repository.startMetadataSession(metadataRepository)
        repository.processFrame(frameTimestampUs = 900_000L)
        repository.processFrame(frameTimestampUs = 1_000_000L)

        repository.setEncoderPtsBaseUs(1_000_000L)
        repository.onVideoSampleWritten(rawPtsUs = 1_000_000L, rebasedPtsUs = 0L)
        repository.processFrame(frameTimestampUs = 1_033_333L)
        repository.onVideoSampleWritten(rawPtsUs = 1_033_333L, rebasedPtsUs = 33_333L)
        repository.closeMetadataSession()

        assertEquals(
            listOf(0L, 33_333L),
            metadataRepository.frames.map(FrameMetadata::ptsUs),
        )
    }

    @Test
    fun `encoder pts base 이후 pending metadata는 sample 콜백 pts로 전송된다`() = runTest {
        repository.startMetadataSession(metadataRepository)
        repository.processFrame(frameTimestampUs = 1_000_000L)
        repository.processFrame(frameTimestampUs = 1_033_333L)

        repository.setEncoderPtsBaseUs(1_000_000L)
        repository.onVideoSampleWritten(rawPtsUs = 1_000_000L, rebasedPtsUs = 0L)
        repository.onVideoSampleWritten(rawPtsUs = 1_033_333L, rebasedPtsUs = 33_333L)
        repository.processFrame(frameTimestampUs = 1_066_666L)
        repository.onVideoSampleWritten(rawPtsUs = 1_066_666L, rebasedPtsUs = 66_666L)
        repository.closeMetadataSession()

        assertEquals(
            listOf(0L, 33_333L, 66_666L),
            metadataRepository.frames.map(FrameMetadata::ptsUs),
        )
    }

    @Test
    fun `video sample write 콜백 전에는 metadata를 전송하지 않는다`() = runTest {
        repository.startMetadataSession(metadataRepository)
        repository.setEncoderPtsBaseUs(1_000_000L)

        repository.processFrame(frameTimestampUs = 1_000_000L)
        repository.processFrame(frameTimestampUs = 1_033_333L)

        assertEquals(emptyList<Long>(), metadataRepository.frames.map(FrameMetadata::ptsUs))
        repository.closeMetadataSession()
    }

    @Test
    fun `실제 기록되지 않은 frame metadata는 다음 sample 콜백에서 버린다`() = runTest {
        repository.startMetadataSession(metadataRepository)
        repository.setEncoderPtsBaseUs(1_000_000L)
        repository.processFrame(frameTimestampUs = 1_000_000L)
        repository.processFrame(frameTimestampUs = 1_033_333L)

        repository.onVideoSampleWritten(rawPtsUs = 1_033_333L, rebasedPtsUs = 33_333L)
        repository.closeMetadataSession()

        assertEquals(
            listOf(33_333L),
            metadataRepository.frames.map(FrameMetadata::ptsUs),
        )
    }

    @Test
    fun `metadata pts는 sample write 콜백의 rebased pts를 그대로 사용한다`() = runTest {
        repository.startMetadataSession(metadataRepository)
        repository.setEncoderPtsBaseUs(1_000_001L)
        repository.processFrame(frameTimestampUs = 1_000_001L)
        repository.processFrame(frameTimestampUs = 1_033_334L)

        repository.onVideoSampleWritten(rawPtsUs = 1_000_001L, rebasedPtsUs = 0L)
        repository.onVideoSampleWritten(rawPtsUs = 1_033_334L, rebasedPtsUs = 33_333L)
        repository.closeMetadataSession()

        assertEquals(
            listOf(0L, 33_333L),
            metadataRepository.frames.map(FrameMetadata::ptsUs),
        )
    }

    private fun CameraAnalysisRepositoryImpl.processFrame(frameTimestampUs: Long) {
        processFrame(
            rgbaBuffer = ByteBuffer.allocateDirect(4 * 4 * 4),
            width = 4,
            height = 4,
            timestampMs = frameTimestampUs / 1_000L,
            timestampUs = frameTimestampUs,
        )
    }

    private fun processedFrame(
        timestampMs: Long,
        frameNumber: Int,
    ): ProcessedFrame {
        return ProcessedFrame(
            faces = emptyList(),
            frameWidth = 4,
            frameHeight = 4,
            timestampMs = timestampMs,
            frameExport = FrameExport(
                frameNumber = frameNumber,
                timestamp = timestampMs / 1_000.0,
                faces = listOf(
                    FaceExport(
                        trackingId = 10,
                        bbox = intArrayOf(1, 1, 2, 2),
                        isOwner = false,
                    ),
                ),
            ),
            trackingIds = listOf(10),
            faceLabels = listOf(false),
        )
    }

    private class CapturingMetadataRepository : MetadataRepository {
        val frames = mutableListOf<FrameMetadata>()

        override suspend fun sendFrame(metadata: FrameMetadata) {
            frames += metadata
        }

        override suspend fun close() = Unit
    }

    private class BlockingMetadataRepository : MetadataRepository {
        val startedPts = mutableListOf<Long>()
        val completedPts = mutableListOf<Long>()
        private val firstSendGate = CompletableDeferred<Unit>()
        private var sendCount = 0

        override suspend fun sendFrame(metadata: FrameMetadata) {
            startedPts += metadata.ptsUs
            sendCount++
            if (sendCount == 1) {
                firstSendGate.await()
            }
            completedPts += metadata.ptsUs
        }

        fun completeFirstSend() {
            firstSendGate.complete(Unit)
        }

        override suspend fun close() = Unit
    }
}
