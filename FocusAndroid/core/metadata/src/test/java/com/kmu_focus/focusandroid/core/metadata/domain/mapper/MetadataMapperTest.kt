package com.kmu_focus.focusandroid.core.metadata.domain.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMapperTest {

    @Test
    fun `isFaceMapLayout과 맞지 않으면 face는 유지되고 tdmm만 생략된다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 1.0,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 10,
                    bbox = intArrayOf(100, 120, 60, 70),
                    idCoeffs = FloatArray(80) { 0.1f }, // invalid: expected 219
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = false,
                )
            ),
        )

        assertEquals(1, frame.faces.size)
        assertEquals(10, frame.faces.first().trackingId)
        assertNull(frame.faces.first().tdmm)
    }

    @Test
    fun `유효한 FaceMap 레이아웃은 face에 포함된다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 1.234567,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 11,
                    bbox = intArrayOf(10, 20, 30, 40),
                    idCoeffs = FloatArray(219) { 0.1f },
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = false,
                )
            ),
        )

        assertEquals(1, frame.faces.size)
        assertEquals(11, frame.faces.first().trackingId)
        assertEquals(265, frame.faces.first().tdmm!!.coeffs.size)
        assertEquals(1_234_567L, frame.ptsUs)
    }

    @Test
    fun `analysis 좌표 bbox를 source video pixel space로 역변환한다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 0.5,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 7,
                    bbox = intArrayOf(80, 60, 160, 120),
                    idCoeffs = FloatArray(219) { 0.1f },
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = false,
                )
            ),
            coordinateSpace = MetadataMapper.CoordinateSpace(
                analysisWidth = 400,
                analysisHeight = 300,
                sourceWidth = 200,
                sourceHeight = 100,
            ),
        )

        val bbox = frame.faces.first().bbox
        assertEquals(40, bbox.x)
        assertEquals(5, bbox.y)
        assertEquals(80, bbox.width)
        assertEquals(60, bbox.height)
    }

    @Test
    fun `isOwner true인 얼굴은 전송에서 제외된다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 1.0,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 1,
                    bbox = intArrayOf(0, 0, 10, 10),
                    idCoeffs = FloatArray(219) { 0.1f },
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = true,
                ),
                MetadataMapper.FaceExportPayload(
                    trackingId = 2,
                    bbox = intArrayOf(0, 0, 10, 10),
                    idCoeffs = FloatArray(219) { 0.1f },
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = false,
                ),
            ),
        )

        assertEquals(1, frame.faces.size)
        assertEquals(2, frame.faces.first().trackingId)
    }

    @Test
    fun `3dmm coeffs가 없어도 bbox는 유지되고 tdmm만 생략된다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 1.0,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 21,
                    bbox = intArrayOf(50, 60, 70, 80),
                    idCoeffs = null,
                    expCoeffs = null,
                    pose = null,
                    extraCoeffs = null,
                    isOwner = false,
                )
            ),
        )

        assertEquals(1, frame.faces.size)
        assertEquals(21, frame.faces.first().trackingId)
        assertNull(frame.faces.first().tdmm)
        assertEquals(50, frame.faces.first().bbox.x)
        assertEquals(60, frame.faces.first().bbox.y)
        assertEquals(70, frame.faces.first().bbox.width)
        assertEquals(80, frame.faces.first().bbox.height)
    }

    @Test
    fun `isOwner null인 PENDING 얼굴은 전송에서 제외된다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 1.0,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 5,
                    bbox = intArrayOf(0, 0, 10, 10),
                    idCoeffs = FloatArray(219) { 0.1f },
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = null,
                )
            ),
        )

        assertTrue(frame.faces.isEmpty())
    }

    @Test
    fun `source 좌표 정보가 없으면 bbox를 그대로 유지한다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 0.5,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 8,
                    bbox = intArrayOf(12, 34, 56, 78),
                    idCoeffs = FloatArray(219) { 0.1f },
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = false,
                )
            ),
        )

        val bbox = frame.faces.first().bbox
        assertEquals(12, bbox.x)
        assertEquals(34, bbox.y)
        assertEquals(56, bbox.width)
        assertEquals(78, bbox.height)
    }

    @Test
    fun `bbox 기준 frame geometry를 metadata에 포함한다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 0.5,
            faces = emptyList(),
            frameWidth = 1280,
            frameHeight = 720,
            rotation = 450,
            mirrored = true,
        )

        assertEquals(1280, frame.frameWidth)
        assertEquals(720, frame.frameHeight)
        assertEquals(90, frame.rotation)
        assertTrue(frame.mirrored)
    }
}
