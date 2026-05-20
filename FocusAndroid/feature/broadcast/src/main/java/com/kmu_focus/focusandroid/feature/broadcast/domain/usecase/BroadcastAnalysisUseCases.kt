package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisJob
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastHighlightCandidate
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.CompleteBroadcastAnalysisJob
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.CreateBroadcastAnalysisJob
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class CreateBroadcastAnalysisJobUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(
        broadcastId: String,
        request: CreateBroadcastAnalysisJob,
    ): Result<BroadcastAnalysisJob> {
        return broadcastRepository.createAnalysisJob(broadcastId, request)
    }
}

class CompleteBroadcastAnalysisJobUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(
        broadcastId: String,
        analysisJobId: String,
        request: CompleteBroadcastAnalysisJob,
    ): Result<BroadcastAnalysisJob> {
        return broadcastRepository.completeAnalysisJob(broadcastId, analysisJobId, request)
    }
}

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
