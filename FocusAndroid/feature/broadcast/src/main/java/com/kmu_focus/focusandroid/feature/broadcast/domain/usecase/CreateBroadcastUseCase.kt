package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class CreateBroadcastUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(title: String): Result<Broadcast> {
        val normalizedTitle = title.normalizeBroadcastTitle()
        if (normalizedTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("방송 제목은 비워둘 수 없습니다"))
        }

        return broadcastRepository.createBroadcast(normalizedTitle)
    }
}

private const val MAX_BROADCAST_TITLE_LENGTH = 20
private val INVALID_BROADCAST_TITLE_CHARS = Regex("[^0-9A-Za-z가-힣\\s_-]")
private val MULTIPLE_WHITESPACE = Regex("\\s+")

private fun String.normalizeBroadcastTitle(): String {
    return replace(INVALID_BROADCAST_TITLE_CHARS, " ")
        .replace(MULTIPLE_WHITESPACE, " ")
        .trim()
        .take(MAX_BROADCAST_TITLE_LENGTH)
        .trim()
}
