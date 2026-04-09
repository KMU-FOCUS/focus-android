package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class StartBroadcastUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(
        broadcastId: String,
        avatarId: String,
    ): Result<Broadcast> {
        if (broadcastId.isBlank()) {
            return Result.failure(IllegalArgumentException("broadcastId는 비워둘 수 없습니다"))
        }
        if (avatarId.isBlank()) {
            return Result.failure(IllegalArgumentException("avatarId는 비워둘 수 없습니다"))
        }

        return broadcastRepository.startBroadcast(
            broadcastId = broadcastId,
            avatarId = avatarId,
        )
    }
}
