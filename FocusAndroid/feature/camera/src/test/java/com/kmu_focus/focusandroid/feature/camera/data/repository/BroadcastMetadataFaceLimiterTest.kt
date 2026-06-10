package com.kmu_focus.focusandroid.feature.camera.data.repository

import com.kmu_focus.focusandroid.core.metadata.domain.mapper.MetadataMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastMetadataFaceLimiterTest {

    @Test
    fun `non-owner 전송 대상이 5명을 넘으면 bbox가 작은 얼굴부터 제외한다`() {
        val faces = listOf(
            payload(trackingId = 1, size = 120, isOwner = false),
            payload(trackingId = 2, size = 110, isOwner = false),
            payload(trackingId = 3, size = 100, isOwner = false),
            payload(trackingId = 4, size = 90, isOwner = false),
            payload(trackingId = 5, size = 80, isOwner = false),
            payload(trackingId = 6, size = 70, isOwner = false),
        )

        val limited = limitBroadcastMetadataFacesForStreaming(
            faces = faces,
            maxFaceCount = 5,
        )

        assertEquals(
            listOf(1, 2, 3, 4, 5),
            limited.filter { it.isOwner == false }.map { it.trackingId },
        )
    }

    @Test
    fun `owner와 pending은 유지하고 non-owner만 최대 5명으로 제한한다`() {
        val faces = listOf(
            payload(trackingId = 100, size = 40, isOwner = true),
            payload(trackingId = 101, size = 35, isOwner = null),
            payload(trackingId = 1, size = 120, isOwner = false),
            payload(trackingId = 2, size = 110, isOwner = false),
            payload(trackingId = 3, size = 100, isOwner = false),
            payload(trackingId = 4, size = 90, isOwner = false),
            payload(trackingId = 5, size = 80, isOwner = false),
            payload(trackingId = 6, size = 70, isOwner = false),
        )

        val limited = limitBroadcastMetadataFacesForStreaming(
            faces = faces,
            maxFaceCount = 5,
        )

        assertTrue(limited.any { it.trackingId == 100 && it.isOwner == true })
        assertTrue(limited.any { it.trackingId == 101 && it.isOwner == null })
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            limited.filter { it.isOwner == false }.map { it.trackingId },
        )
    }

    @Test
    fun `전송 중인 얼굴은 슬롯을 유지하고 비워진 슬롯만 재사용한다`() {
        val allocator = BroadcastMetadataTrackingSlotAllocator(maxSlots = 5)

        val firstFrame = allocator.remapForTransmission(
            listOf(
                payload(trackingId = 10, size = 120, isOwner = false),
                payload(trackingId = 11, size = 110, isOwner = false),
                payload(trackingId = 12, size = 100, isOwner = false),
                payload(trackingId = 13, size = 90, isOwner = false),
                payload(trackingId = 14, size = 80, isOwner = false),
            ),
        )

        assertEquals(listOf(0, 1, 2, 3, 4), firstFrame.map { it.trackingId })

        val secondFrame = allocator.remapForTransmission(
            listOf(
                payload(trackingId = 11, size = 110, isOwner = false),
                payload(trackingId = 12, size = 100, isOwner = false),
                payload(trackingId = 13, size = 90, isOwner = false),
                payload(trackingId = 14, size = 80, isOwner = false),
                payload(trackingId = 57, size = 70, isOwner = false),
            ),
        )

        assertEquals(listOf(1, 2, 3, 4, 0), secondFrame.map { it.trackingId })
    }

    @Test
    fun `입력 순서가 바뀌어도 살아있는 얼굴의 슬롯은 유지한다`() {
        val allocator = BroadcastMetadataTrackingSlotAllocator(maxSlots = 5)

        allocator.remapForTransmission(
            listOf(
                payload(trackingId = 30, size = 120, isOwner = false),
                payload(trackingId = 31, size = 110, isOwner = false),
                payload(trackingId = 32, size = 100, isOwner = false),
            ),
        )

        val reorderedFrame = allocator.remapForTransmission(
            listOf(
                payload(trackingId = 32, size = 100, isOwner = false),
                payload(trackingId = 30, size = 120, isOwner = false),
                payload(trackingId = 31, size = 110, isOwner = false),
            ),
        )

        assertEquals(listOf(2, 0, 1), reorderedFrame.map { it.trackingId })
    }

    @Test
    fun `owner와 pending은 슬롯을 점유하지 않는다`() {
        val allocator = BroadcastMetadataTrackingSlotAllocator(maxSlots = 5)

        val firstFrame = allocator.remapForTransmission(
            listOf(
                payload(trackingId = 900, size = 50, isOwner = true),
                payload(trackingId = 901, size = 45, isOwner = null),
                payload(trackingId = 40, size = 120, isOwner = false),
                payload(trackingId = 41, size = 110, isOwner = false),
            ),
        )

        assertEquals(listOf(900, 901, 0, 1), firstFrame.map { it.trackingId })

        val secondFrame = allocator.remapForTransmission(
            listOf(
                payload(trackingId = 42, size = 100, isOwner = false),
            ),
        )

        assertEquals(listOf(0), secondFrame.map { it.trackingId })
    }

    private fun payload(
        trackingId: Int,
        size: Int,
        isOwner: Boolean?,
    ): MetadataMapper.FaceExportPayload {
        return MetadataMapper.FaceExportPayload(
            trackingId = trackingId,
            bbox = intArrayOf(0, 0, size, size),
            idCoeffs = FloatArray(219) { 0.1f },
            expCoeffs = FloatArray(39) { 0.2f },
            pose = FloatArray(6) { 0.3f },
            extraCoeffs = FloatArray(1) { 0.4f },
            isOwner = isOwner,
        )
    }
}
