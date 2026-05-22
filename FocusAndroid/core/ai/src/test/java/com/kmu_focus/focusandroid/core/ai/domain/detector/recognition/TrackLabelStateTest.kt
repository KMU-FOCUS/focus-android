package com.kmu_focus.focusandroid.core.ai.domain.detector.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLabelStateTest {

    private val classifier = OwnerOtherClassifier(
        provider = object : OwnerEmbeddingProvider {
            override fun getMasterEmbeddings(): List<List<FloatArray>> = emptyList()
        }
    )

    @Test
    fun `markOwner 호출 시 track이 즉시 owner 상태가 된다`() {
        val state = TrackLabelState(classifier = classifier)

        state.markOwner(trackId = 12)

        assertTrue(state.getLabel(12) == true)
        assertFalse(state.needsEmbeddingThisFrame(trackId = 12, isFrontal = true))
    }

    @Test
    fun `보이지 않게 된 pending track은 beginFrame에서 즉시 제거된다`() {
        val state = TrackLabelState(
            classifier = classifier,
            skipFrames = 0,
            collectFrames = 2,
        )

        state.recordFrameSeen(trackId = 7)
        state.addEmbedding(trackId = 7, embedding = floatArrayOf(1f, 0f, 0f))

        assertTrue(state.isPending(7))
        assertEquals(1, state.getEmbeddingCount(7))

        state.beginFrame(emptySet())

        assertFalse(state.isPending(7))
        assertEquals(0, state.getFramesSeen(7))
        assertEquals(0, state.getEmbeddingCount(7))
    }

    @Test
    fun `없는 track은 pending이 아니다`() {
        val state = TrackLabelState(classifier = classifier)

        assertFalse(state.isPending(999))
    }
}
