package com.kmu_focus.focusandroid.core.metadata.domain.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMapperTest {

    @Test
    fun `isFaceMapLayout과 맞지 않으면 face가 드롭된다`() {
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

        assertTrue(frame.faces.isEmpty())
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
        assertEquals(265, frame.faces.first().tdmm.coeffs.size)
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
}
