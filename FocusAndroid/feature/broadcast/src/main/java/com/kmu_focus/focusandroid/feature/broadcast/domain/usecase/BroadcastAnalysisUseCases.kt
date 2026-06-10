package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastHighlightCandidate
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class GetLatestBroadcastAnalysisUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(broadcastId: String): Result<BroadcastAnalysisResult> {
        return broadcastRepository.getLatestAnalysis(broadcastId)
    }
}

class GetBroadcastHighlightsUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(broadcastId: String): Result<List<BroadcastHighlightCandidate>> {
        return broadcastRepository.getHighlights(broadcastId)
    }
}
