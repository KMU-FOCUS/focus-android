package com.kmu_focus.focusandroid.core.ai.domain.detector.recognition

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
}
