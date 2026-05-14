package com.kmu_focus.focusandroid.feature.metadatareview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataPreviewParserTest {

    private val parser = MetadataPreviewParser()

    @Test
    fun parse_sortsFramesAndSelectsFrameByPlaybackPosition() {
        val parsed = parser.parse(
            """
            {
              "frames": [
                {
                  "session_id": "session-b",
                  "pts_us": 50000,
                  "faces": [
                    {
                      "tracking_id": 7,
                      "bbox": { "x": 40, "y": 20, "width": 30, "height": 40 },
                      "tdmm_raw": { "coeffs": [1.5, 2.5] }
                    }
                  ]
                },
                {
                  "session_id": "session-a",
                  "pts_us": 0,
                  "faces": [
                    {
                      "tracking_id": 3,
                      "bbox": { "x": 10, "y": 12, "width": 20, "height": 24 },
                      "tdmm_raw": { "coeffs": [0.1] }
                    },
                    {
                      "tracking_id": 4,
                      "bbox": { "x": 100, "y": 120, "width": 25, "height": 32 }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, parsed.frameCount)
        assertEquals(3, parsed.totalFaceCount)
        assertEquals("session-a", parsed.sessionId)
        assertEquals(125, parsed.coordinateWidth)
        assertEquals(152, parsed.coordinateHeight)

        val firstFrame = parsed.frameAt(positionMs = 10L)
        val secondFrame = parsed.frameAt(positionMs = 60L)
        val outOfWindowFrame = parsed.frameAt(positionMs = 200L)

        assertNotNull(firstFrame)
        assertEquals(2, firstFrame?.faces?.size)
        assertEquals(7, secondFrame?.faces?.singleOrNull()?.trackingId)
        assertNull(outOfWindowFrame)
    }

    @Test
    fun parse_supportsVideoExportMetadataFormat() {
        val parsed = parser.parse(
            """
            {
              "video_info": {
                "width": 1920,
                "height": 1080,
                "fps": 30.0,
                "format": "3dmm"
              },
              "frames": [
                {
                  "frame_number": 0,
                  "timestamp": 0.0,
                  "faces": [
                    {
                      "tracking_id": 11,
                      "bbox": [100, 120, 220, 180],
                      "3dmm": {
                        "id_coeffs": [1.0, 2.0],
                        "exp_coeffs": [3.0],
                        "pose": [4.0, 5.0],
                        "extra_coeffs": [6.0]
                      }
                    }
                  ]
                },
                {
                  "frame_number": 1,
                  "timestamp": 0.033333333,
                  "faces": []
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1920, parsed.coordinateWidth)
        assertEquals(1080, parsed.coordinateHeight)
        assertEquals(30.0f, parsed.fps)
        assertEquals(1, parsed.framesWithFacesCount)
        assertEquals(11, parsed.frameAt(0L)?.faces?.singleOrNull()?.trackingId)
        assertEquals(6, parsed.frameAt(0L)?.faces?.singleOrNull()?.tdmm?.coeffs?.size)
    }

    @Test
    fun parse_normalizesAbsoluteProjectTimestampsForPlaybackPreview() {
        val parsed = parser.parse(
            """
            {
              "frames": [
                {
                  "session_id": "session-a",
                  "pts_us": 1715700000000000,
                  "faces": [
                    {
                      "tracking_id": 1,
                      "bbox": { "x": 20, "y": 30, "width": 40, "height": 50 },
                      "tdmm_raw": { "coeffs": [0.1] }
                    }
                  ]
                },
                {
                  "session_id": "session-a",
                  "pts_us": 1715700000033000,
                  "faces": []
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(true, parsed.isPlaybackTimelineNormalized)
        assertEquals(1, parsed.frameAt(0L)?.faces?.size)
        assertEquals(0, parsed.frameAt(40L)?.faces?.size)
    }
}
