package com.kmu_focus.focusandroid.core.media.data.processor

import android.graphics.Bitmap
import com.kmu_focus.focusandroid.core.ai.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.core.ai.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.core.ai.domain.detector.Facial3DMMExtractor
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerEmbeddingProvider
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerOtherClassifier
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.TrackLabelState
import com.kmu_focus.focusandroid.core.ai.domain.detector.tracking.FaceTracker
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.ai.domain.entity.Face3DMMCoeffs
import com.kmu_focus.focusandroid.core.ai.domain.entity.Face3DMMResult
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceRect
import com.kmu_focus.focusandroid.core.ai.domain.entity.Point2f
import com.kmu_focus.focusandroid.core.media.domain.entity.PrivacyMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FrameProcessorTest {

    private data class RecognitionCropRect(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )

    private val faceDetector: FaceDetector = mockk()
    private val facial3DMMExtractor: Facial3DMMExtractor = mockk(relaxed = true)
    private val faceTracker: FaceTracker = mockk(relaxed = true) {
        every { update(any(), any()) } answers { firstArg<List<IntArray>>().indices.toList() }
    }
    private val config = DetectionConfig()
    private val trackLabelState = mockk<TrackLabelState>(relaxed = true)
    private val embeddingExtractor = mockk<com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor>(relaxed = true)

    private val frameProcessor = FrameProcessor(
        faceDetector, config, facial3DMMExtractor, faceTracker,
        trackLabelState, embeddingExtractor
    )

    @Test
    fun `process 호출 시 FaceDetector detectFaces를 호출함`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        frameProcessor.process(bitmap, 1000L)

        verify(exactly = 1) { faceDetector.detectFaces(bitmap) }
    }

    @Test
    fun `검출된 얼굴이 ProcessedFrame에 담김`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 1920
            every { height } returns 1080
        }
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f),
            DetectedFace(200, 300, 80, 80, 0.85f)
        )
        every { faceDetector.detectFaces(bitmap) } returns faces
        every { facial3DMMExtractor.extract3DMM(any(), any()) } returns null

        val result = frameProcessor.process(bitmap, 2000L)

        assertEquals(2, result.faces.size)
        assertEquals(0.95f, result.faces[0].confidence, 0.001f)
    }

    @Test
    fun `검출 결과가 없으면 빈 리스트가 반환됨`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        val result = frameProcessor.process(bitmap, 0L)

        assertTrue(result.faces.isEmpty())
    }

    @Test
    fun `얼굴이 없어도 beginFrame으로 pending 정리를 호출한다`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        frameProcessor.process(bitmap, 0L)

        verify(exactly = 1) { trackLabelState.beginFrame(match { it.isEmpty() }) }
    }

    @Test
    fun `frameWidth와 frameHeight가 Bitmap 크기와 일치함`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 1280
            every { height } returns 720
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        val result = frameProcessor.process(bitmap, 500L)

        assertEquals(1280, result.frameWidth)
        assertEquals(720, result.frameHeight)
    }

    @Test
    fun `timestampMs가 정확히 전달됨`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        val result = frameProcessor.process(bitmap, 12345L)

        assertEquals(12345L, result.timestampMs)
    }

    @Test
    fun `confidence가 임계값 미만인 얼굴은 필터링됨`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 1920
            every { height } returns 1080
        }
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.8f),
            DetectedFace(200, 300, 80, 80, 0.3f)
        )
        every { faceDetector.detectFaces(bitmap) } returns faces
        every { facial3DMMExtractor.extract3DMM(any(), any()) } returns null

        val result = frameProcessor.process(bitmap, 0L)

        assertEquals(1, result.faces.size)
        assertEquals(0.8f, result.faces[0].confidence, 0.001f)
    }

    @Test
    fun `frameIndex가 있고 얼굴이 있으면 frameExport에 3dmm 포함`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.9f),
            DetectedFace(200, 100, 80, 80, 0.85f)
        )
        every { faceDetector.detectFaces(bitmap) } returns faces
        every {
            facial3DMMExtractor.extract3DMM(bitmap, any())
        } returns Face3DMMResult(
            vertices = emptyList(),
            faceRect = FaceRect(10, 20, 110, 120),
            coeffs = Face3DMMCoeffs(floatArrayOf(1f, 2f), floatArrayOf(3f), floatArrayOf(4f))
        )

        val result = frameProcessor.process(bitmap, 2000L, frameIndex = 5)

        assertNotNull(result.frameExport)
        assertEquals(5, result.frameExport!!.frameNumber)
        assertEquals(2.0, result.frameExport!!.timestamp, 0.001)
        assertEquals(2, result.frameExport!!.faces.size)
        assertEquals(2, result.frameExport!!.faces[0].idCoeffs!!.size)
        assertEquals(1, result.frameExport!!.faces[0].expCoeffs!!.size)
        assertEquals(1, result.frameExport!!.faces[0].pose!!.size)
    }

    @Test
    fun `frameIndex가 있고 얼굴이 있으면 faceTracker update 호출`() {
        val bitmap = mockk<Bitmap> { every { width } returns 640; every { height } returns 480 }
        val faces = listOf(DetectedFace(10, 20, 100, 100, 0.9f))
        every { faceDetector.detectFaces(bitmap) } returns faces
        every { facial3DMMExtractor.extract3DMM(bitmap, any()) } returns Face3DMMResult(
            vertices = emptyList(),
            faceRect = FaceRect(10, 20, 110, 120),
            coeffs = Face3DMMCoeffs(floatArrayOf(1f), floatArrayOf(2f), floatArrayOf(3f))
        )
        every { faceTracker.update(any(), any()) } returns listOf(0)

        frameProcessor.process(bitmap, 1000L, frameIndex = 7)

        io.mockk.verify(exactly = 1) { faceTracker.update(any(), any()) }
    }

    @Test
    fun `Avatar 메타데이터용 3DMM은 bbox가 큰 얼굴 5개만 추출하고 export한다`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        val faces = listOf(
            DetectedFace(0, 0, 100, 100, 0.99f),
            DetectedFace(10, 10, 90, 90, 0.98f),
            DetectedFace(20, 20, 80, 80, 0.97f),
            DetectedFace(30, 30, 70, 70, 0.96f),
            DetectedFace(40, 40, 60, 60, 0.95f),
            DetectedFace(50, 50, 10, 10, 0.94f),
        )
        every { faceDetector.detectFaces(bitmap) } returns faces
        every { faceTracker.update(any(), any()) } answers { firstArg<List<IntArray>>().indices.toList() }
        every { facial3DMMExtractor.extract3DMM(bitmap, any()) } returns Face3DMMResult(
            vertices = emptyList(),
            faceRect = FaceRect(0, 0, 100, 100),
            coeffs = Face3DMMCoeffs(floatArrayOf(1f), floatArrayOf(2f), floatArrayOf(3f)),
        )

        val result = frameProcessor.process(bitmap, 1000L, frameIndex = 9)

        verify(exactly = 5) { facial3DMMExtractor.extract3DMM(bitmap, any()) }
        assertNotNull(result.frameExport)
        assertEquals(5, result.frameExport!!.faces.size)
        assertFalse(result.frameExport!!.faces.any { it.bbox.contentEquals(intArrayOf(50, 50, 10, 10)) })
    }

    @Test
    fun `frameIndex가 null이면 frameExport는 null`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        every { faceDetector.detectFaces(bitmap) } returns listOf(DetectedFace(0, 0, 50, 50, 0.9f))
        every { facial3DMMExtractor.extract3DMM(any(), any()) } returns null

        val result = frameProcessor.process(bitmap, 1000L, frameIndex = null)

        assertNull(result.frameExport)
    }

    @Test
    fun `커스텀 config의 confidenceThreshold가 적용됨`() {
        val customConfig = DetectionConfig(confidenceThreshold = 0.9f)
        val customProcessor = FrameProcessor(
            faceDetector, customConfig, facial3DMMExtractor, faceTracker,
            trackLabelState, embeddingExtractor
        )
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f),
            DetectedFace(200, 300, 80, 80, 0.85f)
        )
        every { faceDetector.detectFaces(bitmap) } returns faces
        every { facial3DMMExtractor.extract3DMM(any(), any()) } returns null

        val result = customProcessor.process(bitmap, 0L)

        assertEquals(1, result.faces.size)
        assertEquals(0.95f, result.faces[0].confidence, 0.001f)
    }

    @Test
    fun `strict queue는 한 track의 collectFrames가 끝날 때까지 같은 얼굴만 연속 판정한다`() {
        val localFaceDetector: FaceDetector = mockk()
        val local3dmmExtractor: Facial3DMMExtractor = mockk(relaxed = true)
        val localFaceTracker: FaceTracker = mockk(relaxed = true)
        val localEmbeddingExtractor = mockk<com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor>()
        val localTrackLabelState = TrackLabelState(
            classifier = OwnerOtherClassifier(
                provider = object : OwnerEmbeddingProvider {
                    override fun getMasterEmbeddings(): List<List<FloatArray>> =
                        listOf(listOf(floatArrayOf(1f, 0f, 0f)))
                }
            ),
            skipFrames = 0,
            collectFrames = 3,
        )
        val localProcessor = FrameProcessor(
            localFaceDetector,
            config,
            local3dmmExtractor,
            localFaceTracker,
            localTrackLabelState,
            localEmbeddingExtractor,
        ).apply {
            setPrivacyMode(PrivacyMode.Original)
        }
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        val faceA = DetectedFace(100, 100, 100, 100, 0.99f, frontalLandmarks(offsetX = 100f, offsetY = 100f))
        val faceB = DetectedFace(300, 100, 100, 100, 0.98f, frontalLandmarks(offsetX = 300f, offsetY = 100f))
        val cropA = mockk<Bitmap>(relaxed = true)
        val cropB = mockk<Bitmap>(relaxed = true)
        val recognitionOrder = mutableListOf<String>()

        every { localFaceDetector.detectFaces(bitmap) } returnsMany listOf(
            listOf(faceA, faceB),
            listOf(faceA, faceB),
            listOf(faceA, faceB),
            listOf(faceA, faceB),
        )
        every { localFaceTracker.update(any(), any()) } returns listOf(10, 20)
        every { localEmbeddingExtractor.extractEmbedding(any()) } answers {
            recognitionOrder += when (firstArg<Bitmap>()) {
                cropA -> "A"
                cropB -> "B"
                else -> error("unexpected crop bitmap")
            }
            floatArrayOf(1f, 0f, 0f)
        }

        withMockedRecognitionCropFactory(
            bitmap = bitmap,
            cropsByRect = mapOf(
                RecognitionCropRect(50, 50, 200, 200) to cropA,
                RecognitionCropRect(250, 50, 200, 200) to cropB,
            ),
        ) {
            repeat(4) { frame ->
                localProcessor.process(bitmap, 1000L + frame, frameIndex = frame + 1)
            }
        }

        assertEquals(listOf("A", "A", "A", "B"), recognitionOrder)
    }

    @Test
    fun `strict queue head가 사라지면 다음 대기 track이 바로 판정을 이어받는다`() {
        val localFaceDetector: FaceDetector = mockk()
        val local3dmmExtractor: Facial3DMMExtractor = mockk(relaxed = true)
        val localFaceTracker: FaceTracker = mockk(relaxed = true)
        val localEmbeddingExtractor = mockk<com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor>()
        val localTrackLabelState = TrackLabelState(
            classifier = OwnerOtherClassifier(
                provider = object : OwnerEmbeddingProvider {
                    override fun getMasterEmbeddings(): List<List<FloatArray>> =
                        listOf(listOf(floatArrayOf(1f, 0f, 0f)))
                }
            ),
            skipFrames = 0,
            collectFrames = 3,
        )
        val localProcessor = FrameProcessor(
            localFaceDetector,
            config,
            local3dmmExtractor,
            localFaceTracker,
            localTrackLabelState,
            localEmbeddingExtractor,
        ).apply {
            setPrivacyMode(PrivacyMode.Original)
        }
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        val faceA = DetectedFace(100, 100, 100, 100, 0.99f, frontalLandmarks(offsetX = 100f, offsetY = 100f))
        val faceB = DetectedFace(300, 100, 100, 100, 0.98f, frontalLandmarks(offsetX = 300f, offsetY = 100f))
        val cropA = mockk<Bitmap>(relaxed = true)
        val cropB = mockk<Bitmap>(relaxed = true)
        val recognitionOrder = mutableListOf<String>()

        every { localFaceDetector.detectFaces(bitmap) } returnsMany listOf(
            listOf(faceA, faceB),
            listOf(faceB),
        )
        every {
            localFaceTracker.update(match { it.size == 2 }, any())
        } returns listOf(10, 20)
        every {
            localFaceTracker.update(match { it.size == 1 && it[0][0] == 300 }, any())
        } returns listOf(20)
        every { localEmbeddingExtractor.extractEmbedding(any()) } answers {
            recognitionOrder += when (firstArg<Bitmap>()) {
                cropA -> "A"
                cropB -> "B"
                else -> error("unexpected crop bitmap")
            }
            floatArrayOf(1f, 0f, 0f)
        }

        withMockedRecognitionCropFactory(
            bitmap = bitmap,
            cropsByRect = mapOf(
                RecognitionCropRect(50, 50, 200, 200) to cropA,
                RecognitionCropRect(250, 50, 200, 200) to cropB,
            ),
        ) {
            localProcessor.process(bitmap, 1000L, frameIndex = 1)
            localProcessor.process(bitmap, 1001L, frameIndex = 2)
        }

        assertEquals(listOf("A", "B"), recognitionOrder)
    }

    @Test
    fun `strict queue head가 비정면이면 다음 프레임에 다음 대기 track을 판정한다`() {
        val localFaceDetector: FaceDetector = mockk()
        val local3dmmExtractor: Facial3DMMExtractor = mockk(relaxed = true)
        val localFaceTracker: FaceTracker = mockk(relaxed = true)
        val localEmbeddingExtractor = mockk<com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor>()
        val localTrackLabelState = TrackLabelState(
            classifier = OwnerOtherClassifier(
                provider = object : OwnerEmbeddingProvider {
                    override fun getMasterEmbeddings(): List<List<FloatArray>> =
                        listOf(listOf(floatArrayOf(1f, 0f, 0f)))
                }
            ),
            skipFrames = 0,
            collectFrames = 3,
        )
        val localProcessor = FrameProcessor(
            localFaceDetector,
            config,
            local3dmmExtractor,
            localFaceTracker,
            localTrackLabelState,
            localEmbeddingExtractor,
        ).apply {
            setPrivacyMode(PrivacyMode.Original)
        }
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        val faceA = DetectedFace(100, 100, 100, 100, 0.99f, nonFrontalLandmarks(offsetX = 100f, offsetY = 100f))
        val faceB = DetectedFace(300, 100, 100, 100, 0.98f, frontalLandmarks(offsetX = 300f, offsetY = 100f))
        val cropA = mockk<Bitmap>(relaxed = true)
        val cropB = mockk<Bitmap>(relaxed = true)
        val recognitionOrder = mutableListOf<String>()

        every { localFaceDetector.detectFaces(bitmap) } returnsMany listOf(
            listOf(faceA, faceB),
            listOf(faceA, faceB),
        )
        every { localFaceTracker.update(any(), any()) } returns listOf(10, 20)
        every { localEmbeddingExtractor.extractEmbedding(any()) } answers {
            recognitionOrder += when (firstArg<Bitmap>()) {
                cropA -> "A"
                cropB -> "B"
                else -> error("unexpected crop bitmap")
            }
            floatArrayOf(1f, 0f, 0f)
        }

        withMockedRecognitionCropFactory(
            bitmap = bitmap,
            cropsByRect = mapOf(
                RecognitionCropRect(50, 50, 200, 200) to cropA,
                RecognitionCropRect(250, 50, 200, 200) to cropB,
            ),
        ) {
            localProcessor.process(bitmap, 1000L, frameIndex = 1)
            localProcessor.process(bitmap, 1001L, frameIndex = 2)
        }

        assertEquals(listOf("B"), recognitionOrder)
        assertEquals(0, localTrackLabelState.getEmbeddingCount(10))
        assertEquals(1, localTrackLabelState.getEmbeddingCount(20))
    }

    @Test
    fun `strict queue head 임베딩 실패 시 다음 프레임에 다음 대기 track을 판정한다`() {
        val localFaceDetector: FaceDetector = mockk()
        val local3dmmExtractor: Facial3DMMExtractor = mockk(relaxed = true)
        val localFaceTracker: FaceTracker = mockk(relaxed = true)
        val localEmbeddingExtractor = mockk<com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor>()
        val localTrackLabelState = TrackLabelState(
            classifier = OwnerOtherClassifier(
                provider = object : OwnerEmbeddingProvider {
                    override fun getMasterEmbeddings(): List<List<FloatArray>> =
                        listOf(listOf(floatArrayOf(1f, 0f, 0f)))
                }
            ),
            skipFrames = 0,
            collectFrames = 3,
        )
        val localProcessor = FrameProcessor(
            localFaceDetector,
            config,
            local3dmmExtractor,
            localFaceTracker,
            localTrackLabelState,
            localEmbeddingExtractor,
        ).apply {
            setPrivacyMode(PrivacyMode.Original)
        }
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        val faceA = DetectedFace(100, 100, 100, 100, 0.99f, frontalLandmarks(offsetX = 100f, offsetY = 100f))
        val faceB = DetectedFace(300, 100, 100, 100, 0.98f, frontalLandmarks(offsetX = 300f, offsetY = 100f))
        val cropA = mockk<Bitmap>(relaxed = true)
        val cropB = mockk<Bitmap>(relaxed = true)
        val recognitionAttempts = mutableListOf<String>()

        every { localFaceDetector.detectFaces(bitmap) } returnsMany listOf(
            listOf(faceA, faceB),
            listOf(faceA, faceB),
        )
        every { localFaceTracker.update(any(), any()) } returns listOf(10, 20)
        every { localEmbeddingExtractor.extractEmbedding(any()) } answers {
            when (firstArg<Bitmap>()) {
                cropA -> {
                    recognitionAttempts += "A"
                    null
                }
                cropB -> {
                    recognitionAttempts += "B"
                    floatArrayOf(1f, 0f, 0f)
                }
                else -> error("unexpected crop bitmap")
            }
        }

        withMockedRecognitionCropFactory(
            bitmap = bitmap,
            cropsByRect = mapOf(
                RecognitionCropRect(50, 50, 200, 200) to cropA,
                RecognitionCropRect(250, 50, 200, 200) to cropB,
            ),
        ) {
            localProcessor.process(bitmap, 1000L, frameIndex = 1)
            localProcessor.process(bitmap, 1001L, frameIndex = 2)
        }

        assertEquals(listOf("A", "B"), recognitionAttempts)
        assertEquals(0, localTrackLabelState.getEmbeddingCount(10))
        assertEquals(1, localTrackLabelState.getEmbeddingCount(20))
    }

    // --- ByteBuffer 오버로드 테스트 ---

    private fun frontalLandmarks(offsetX: Float = 0f, offsetY: Float = 0f): FaceLandmarks5 =
        FaceLandmarks5(
            rightEye = Point2f(offsetX + 30f, offsetY + 30f),
            leftEye = Point2f(offsetX + 70f, offsetY + 30f),
            nose = Point2f(offsetX + 50f, offsetY + 50f),
            rightMouth = Point2f(offsetX + 35f, offsetY + 75f),
            leftMouth = Point2f(offsetX + 65f, offsetY + 75f),
        )

    private fun nonFrontalLandmarks(offsetX: Float = 0f, offsetY: Float = 0f): FaceLandmarks5 =
        FaceLandmarks5(
            rightEye = Point2f(offsetX + 30f, offsetY + 30f),
            leftEye = Point2f(offsetX + 70f, offsetY + 30f),
            nose = Point2f(offsetX + 80f, offsetY + 50f),
            rightMouth = Point2f(offsetX + 35f, offsetY + 75f),
            leftMouth = Point2f(offsetX + 65f, offsetY + 75f),
        )

    private fun <T> withMockedRecognitionCropFactory(
        bitmap: Bitmap,
        cropsByRect: Map<RecognitionCropRect, Bitmap>,
        block: () -> T,
    ): T {
        mockkStatic(Bitmap::class)
        every {
            Bitmap.createBitmap(bitmap, any(), any(), any(), any())
        } answers {
            val rect = RecognitionCropRect(
                left = arg(1),
                top = arg(2),
                width = arg(3),
                height = arg(4),
            )
            cropsByRect[rect] ?: error("unexpected recognition crop rect: $rect")
        }
        return try {
            block()
        } finally {
            unmockkStatic(Bitmap::class)
        }
    }

    private fun createRGBABuffer(width: Int, height: Int): ByteBuffer {
        val size = width * height * 4
        return ByteBuffer.allocateDirect(size).apply {
            order(ByteOrder.nativeOrder())
            for (i in 0 until size) put(128.toByte())
            flip()
        }
    }

    private fun <T> withMockedBitmapFactory(width: Int, height: Int, block: () -> T): T {
        val mockBitmap = mockk<Bitmap>(relaxed = true) {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
        }
        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(width, height, any()) } returns mockBitmap
        return try {
            block()
        } finally {
            unmockkStatic(Bitmap::class)
        }
    }

    @Test
    fun `ByteBuffer process 호출 시 FaceDetector detectFaces를 호출함`() {
        val buffer = createRGBABuffer(640, 480)
        every { faceDetector.detectFaces(any<Bitmap>()) } returns emptyList()

        withMockedBitmapFactory(640, 480) {
            frameProcessor.process(buffer, 640, 480, 1000L)
        }

        verify(exactly = 1) { faceDetector.detectFaces(any<Bitmap>()) }
    }

    @Test
    fun `ByteBuffer process 결과의 frameWidth와 frameHeight가 파라미터와 일치함`() {
        val buffer = createRGBABuffer(1280, 720)
        every { faceDetector.detectFaces(any<Bitmap>()) } returns emptyList()

        val result = withMockedBitmapFactory(1280, 720) {
            frameProcessor.process(buffer, 1280, 720, 500L)
        }

        assertEquals(1280, result.frameWidth)
        assertEquals(720, result.frameHeight)
    }

    @Test
    fun `ByteBuffer process에서도 confidence 필터링이 적용됨`() {
        val buffer = createRGBABuffer(640, 480)
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.8f),
            DetectedFace(200, 300, 80, 80, 0.3f)
        )
        every { faceDetector.detectFaces(any<Bitmap>()) } returns faces
        every { facial3DMMExtractor.extract3DMM(any(), any()) } returns null

        val result = withMockedBitmapFactory(640, 480) {
            frameProcessor.process(buffer, 640, 480, 0L)
        }

        assertEquals(1, result.faces.size)
        assertEquals(0.8f, result.faces[0].confidence, 0.001f)
    }

    @Test
    fun `ByteBuffer process에서 timestampMs가 정확히 전달됨`() {
        val buffer = createRGBABuffer(640, 480)
        every { faceDetector.detectFaces(any<Bitmap>()) } returns emptyList()

        val result = withMockedBitmapFactory(640, 480) {
            frameProcessor.process(buffer, 640, 480, 99999L)
        }

        assertEquals(99999L, result.timestampMs)
    }
}
