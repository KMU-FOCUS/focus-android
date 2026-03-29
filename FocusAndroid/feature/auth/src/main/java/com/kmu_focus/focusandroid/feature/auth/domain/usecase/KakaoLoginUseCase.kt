package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.KakaoAuthRepository
import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import javax.inject.Inject

class KakaoLoginUseCase @Inject constructor(
    private val repository: KakaoAuthRepository,
    private val authSessionManager: AuthSessionManager,
) {
    suspend operator fun invoke(context: Any): Result<String> {
        val result = runCatching { repository.login(context) }
            .getOrElse {
                Result.failure(
                    AuthError.Unexpected(
                        message = it.message ?: "로그인 실패",
                        cause = it,
                    )
                )
            }

        authSessionManager.update(result.isSuccess)
        return result
    }
}
