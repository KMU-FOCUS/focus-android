package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.KakaoAuthRepository
import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoLoginUseCaseTest {

    private lateinit var kakaoAuthRepository: KakaoAuthRepository
    private lateinit var authSessionManager: AuthSessionManager
    private lateinit var useCase: AutoLoginUseCase

    @Before
    fun setup() {
        kakaoAuthRepository = mockk()
        authSessionManager = AuthSessionManager()
        useCase = AutoLoginUseCase(kakaoAuthRepository, authSessionManager)
    }

    @Test
    fun `저장된 카카오 토큰이 유효하면 true를 반환한다`() = runTest {
        coEvery { kakaoAuthRepository.validateToken() } returns Result.success(true)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        assertTrue(authSessionManager.isLoggedIn.value)
        coVerify(exactly = 1) { kakaoAuthRepository.validateToken() }
    }

    @Test
    fun `저장된 카카오 토큰이 만료되면 false를 반환한다`() = runTest {
        coEvery { kakaoAuthRepository.validateToken() } returns Result.success(false)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull() == true)
        assertFalse(authSessionManager.isLoggedIn.value)
    }

    @Test
    fun `토큰이 없으면 실패를 반환한다`() = runTest {
        coEvery { kakaoAuthRepository.validateToken() } returns Result.failure(
            AuthError.TokenMissing
        )

        val result = useCase()

        assertTrue(result.isFailure)
        assertFalse(authSessionManager.isLoggedIn.value)
    }

    @Test
    fun `토큰 검증 중 네트워크 오류 시 실패를 반환한다`() = runTest {
        coEvery { kakaoAuthRepository.validateToken() } returns Result.failure(
            AuthError.Network("네트워크 오류")
        )

        val result = useCase()

        assertTrue(result.isFailure)
        assertFalse(authSessionManager.isLoggedIn.value)
    }
}
