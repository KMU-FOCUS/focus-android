package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class GetBroadcastListUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(
        page: Int,
        size: Int,
    ): Result<List<Broadcast>> {
        if (page < 0) {
            return Result.failure(IllegalArgumentException("page는 0 이상이어야 합니다"))
        }
        if (size <= 0) {
            return Result.failure(IllegalArgumentException("size는 1 이상이어야 합니다"))
        }

        return broadcastRepository.getBroadcastList(page = page, size = size)
    }
}
