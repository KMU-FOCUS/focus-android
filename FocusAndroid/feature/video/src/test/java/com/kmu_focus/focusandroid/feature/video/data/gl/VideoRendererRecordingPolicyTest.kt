package com.kmu_focus.focusandroid.core.media.data.gl

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRendererRecordingPolicyTest {

    @Test
    fun `recording이 비활성이면 프레임이 있어도 제출하지 않는다`() {
        val frame = processedFrameWithNoFaces()

        val shouldSubmit = shouldSubmitFrameForRecording(
            recordingEnabled = false,
            processedFrame = frame
        )

        assertFalse(shouldSubmit)
    }

    @Test
    fun `recording이 활성이어도 processedFrame이 없으면 제출하지 않는다`() {
        val shouldSubmit = shouldSubmitFrameForRecording(
            recordingEnabled = true,
            processedFrame = null
        )

        assertFalse(shouldSubmit)
    }

    @Test
    fun `recording이 활성이고 얼굴이 없어도 프레임을 제출한다`() {
        val frame = processedFrameWithNoFaces()

        val shouldSubmit = shouldSubmitFrameForRecording(
            recordingEnabled = true,
            processedFrame = frame
        )

        assertTrue(shouldSubmit)
    }

    @Test
    fun `recording이 활성이고 얼굴이 있으면 프레임을 제출한다`() {
        val frame = ProcessedFrame(
            faces = listOf(
                DetectedFace(
                    x = 10,
                    y = 20,
                    width = 80,
                    height = 80,
                    confidence = 0.9f
                )
            ),
            frameWidth = 1280,
            frameHeight = 720,
            timestampMs = 1000L
        )

        val shouldSubmit = shouldSubmitFrameForRecording(
            recordingEnabled = true,
            processedFrame = frame
        )

        assertTrue(shouldSubmit)
    }

    @Test
    fun `인코더 버퍼 인덱스는 0에서 1로 토글된다`() {
        val resolved = nextEncoderBufferIndex(0)

        assertEquals(1, resolved)
    }

    @Test
    fun `인코더 버퍼 인덱스는 1에서 0으로 토글된다`() {
        val resolved = nextEncoderBufferIndex(1)

        assertEquals(0, resolved)
    }

    @Test
    fun `인코더 버퍼 인덱스는 연속 호출 시 0과 1을 반복한다`() {
        val first = nextEncoderBufferIndex(0)
        val second = nextEncoderBufferIndex(first)
        val third = nextEncoderBufferIndex(second)

        assertEquals(1, first)
        assertEquals(0, second)
        assertEquals(1, third)
    }

    @Test
    fun `인코더 timestamp는 microsecond 단위로 정렬된다`() {
        val resolved = resolveMonotonicEncoderTimestampNs(
            frameTimestampNs = 1_234_567_890L,
            lastEncoderTimestampNs = Long.MIN_VALUE,
            fallbackTimestampNs = 9_000L,
        )

        assertEquals(1_234_567_000L, resolved)
    }

    @Test
    fun `인코더 timestamp는 같은 microsecond가 반복되면 1us 증가한다`() {
        val resolved = resolveMonotonicEncoderTimestampNs(
            frameTimestampNs = 1_234_567_999L,
            lastEncoderTimestampNs = 1_234_567_000L,
            fallbackTimestampNs = 9_000L,
        )

        assertEquals(1_234_568_000L, resolved)
    }

    @Test
    fun `인코더 timestamp fallback도 microsecond 단위로 정렬된다`() {
        val resolved = resolveMonotonicEncoderTimestampNs(
            frameTimestampNs = 0L,
            lastEncoderTimestampNs = Long.MIN_VALUE,
            fallbackTimestampNs = 9_876_543L,
        )

        assertEquals(9_876_000L, resolved)
    }

    @Test
    fun `프레임 타임스탬프가 뒤로 가면 분석 파이프라인 reset으로 판단한다`() {
        val shouldReset = hasAnalysisTimestampReset(
            lastFrameTimestampNs = 5_000_000_000L,
            frameTimestampNs = 1_000_000L,
        )

        assertTrue(shouldReset)
    }

    @Test
    fun `프레임 타임스탬프가 증가하면 분석 파이프라인 reset이 아니다`() {
        val shouldReset = hasAnalysisTimestampReset(
            lastFrameTimestampNs = 1_000_000L,
            frameTimestampNs = 5_000_000_000L,
        )

        assertFalse(shouldReset)
    }

    @Test
    fun `privacy mask 영역은 얼굴 원을 감싸는 union 영역으로 계산된다`() {
        val region = calculatePrivacyMaskRegion(
            ellipses = listOf(
                EllipseParams(
                    centerX = 0.50f,
                    centerY = 0.50f,
                    radiusX = 0.20f,
                    radiusY = 0.10f,
                    angle = 0f,
                )
            ),
            viewWidth = 200,
            viewHeight = 100,
        )

        assertEquals(0.23f, region?.regionRect?.minX ?: 0f, 0.0001f)
        assertEquals(0.32f, region?.regionRect?.minY ?: 0f, 0.0001f)
        assertEquals(0.77f, region?.regionRect?.maxX ?: 0f, 0.0001f)
        assertEquals(0.68f, region?.regionRect?.maxY ?: 0f, 0.0001f)
    }

    @Test
    fun `privacy mask는 위치는 현재 프레임을 따르고 크기만 완만하게 안정화한다`() {
        val stabilized = stabilizePrivacyEllipses(
            previousEllipses = listOf(
                EllipseParams(
                    centerX = 0.40f,
                    centerY = 0.42f,
                    radiusX = 0.18f,
                    radiusY = 0.24f,
                    angle = 0.10f,
                    topClip = -0.55f,
                    leftRadiusX = 0.17f,
                    rightRadiusX = 0.21f,
                )
            ),
            currentEllipses = listOf(
                EllipseParams(
                    centerX = 0.50f,
                    centerY = 0.52f,
                    radiusX = 0.22f,
                    radiusY = 0.28f,
                    angle = 0.20f,
                    topClip = -0.45f,
                    leftRadiusX = 0.20f,
                    rightRadiusX = 0.25f,
                )
            ),
            shapeSmoothingFactor = 0.72f,
        ).single()

        assertEquals(0.496f, stabilized.centerX, 0.0001f)
        assertEquals(0.516f, stabilized.centerY, 0.0001f)
        assertEquals(0.2088f, stabilized.radiusX, 0.0001f)
        assertEquals(0.2688f, stabilized.radiusY, 0.0001f)
        assertEquals(0.138f, stabilized.angle, 0.0001f)
        assertEquals(-0.478f, stabilized.topClip, 0.0001f)
        assertEquals(0.1916f, stabilized.leftRadiusX, 0.0001f)
        assertEquals(0.2388f, stabilized.rightRadiusX, 0.0001f)
    }

    @Test
    fun `privacy mask는 너무 멀리 이동한 얼굴이면 이전 프레임과 섞지 않는다`() {
        val current = EllipseParams(
            centerX = 0.82f,
            centerY = 0.80f,
            radiusX = 0.12f,
            radiusY = 0.14f,
            angle = 0.05f,
            leftRadiusX = 0.11f,
            rightRadiusX = 0.13f,
        )

        val stabilized = stabilizePrivacyEllipses(
            previousEllipses = listOf(
                EllipseParams(
                    centerX = 0.12f,
                    centerY = 0.18f,
                    radiusX = 0.12f,
                    radiusY = 0.14f,
                    angle = 0.01f,
                    leftRadiusX = 0.11f,
                    rightRadiusX = 0.13f,
                )
            ),
            currentEllipses = listOf(current),
            shapeSmoothingFactor = 0.72f,
        ).single()

        assertEquals(current, stabilized)
    }

    @Test
    fun `녹화 중이 아니면 프리뷰는 현재 프레임을 그대로 사용한다`() {
        val selection = resolvePreviewFrameSelection(
            recordingEnabled = false,
            processedFrame = processedFrameWithNoFaces(),
            currentPreviewTextureId = 11,
            analysisPreviewTextureId = 22,
            previousPreviewTextureId = 33,
            wasSynchronized = true,
        )

        assertEquals(11, selection.textureId)
        assertFalse(selection.isSynchronized)
    }

    @Test
    fun `녹화 중이고 분석 프레임이 있으면 프리뷰는 분석 완료 프레임으로 전환된다`() {
        val selection = resolvePreviewFrameSelection(
            recordingEnabled = true,
            processedFrame = processedFrameWithNoFaces(),
            currentPreviewTextureId = 11,
            analysisPreviewTextureId = 22,
            previousPreviewTextureId = 33,
            wasSynchronized = false,
        )

        assertEquals(22, selection.textureId)
        assertTrue(selection.isSynchronized)
    }

    @Test
    fun `동기화가 이미 성립된 뒤 분석 프레임이 잠시 비면 이전 동기화 프레임을 유지한다`() {
        val selection = resolvePreviewFrameSelection(
            recordingEnabled = true,
            processedFrame = null,
            currentPreviewTextureId = 11,
            analysisPreviewTextureId = 22,
            previousPreviewTextureId = 33,
            wasSynchronized = true,
        )

        assertEquals(33, selection.textureId)
        assertTrue(selection.isSynchronized)
    }

    @Test
    fun `녹화 시작 직후 아직 분석 프레임이 없으면 현재 프레임을 유지한다`() {
        val selection = resolvePreviewFrameSelection(
            recordingEnabled = true,
            processedFrame = null,
            currentPreviewTextureId = 11,
            analysisPreviewTextureId = 22,
            previousPreviewTextureId = 0,
            wasSynchronized = false,
        )

        assertEquals(11, selection.textureId)
        assertFalse(selection.isSynchronized)
    }

    private fun processedFrameWithNoFaces(): ProcessedFrame = ProcessedFrame(
        faces = emptyList(),
        frameWidth = 1280,
        frameHeight = 720,
        timestampMs = 1000L
    )
}
