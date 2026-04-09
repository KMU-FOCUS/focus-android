package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class DeleteBroadcastUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(broadcastId: String): Result<Unit> {
        if (broadcastId.isBlank()) {
            return Result.failure(IllegalArgumentException("broadcastId는 비워둘 수 없습니다"))
        }

        return broadcastRepository.deleteBroadcast(broadcastId)
    }
}
