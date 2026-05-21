package com.kmu_focus.focusandroid.feature.account.domain.usecase

import com.kmu_focus.focusandroid.feature.account.domain.entity.YoutubeConnectionStatus
import com.kmu_focus.focusandroid.feature.account.domain.repository.AccountRepository
import javax.inject.Inject

class GetYoutubeConnectionStatusUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(): Result<YoutubeConnectionStatus> {
        return accountRepository.getYoutubeConnectionStatus()
    }
}
