package com.kmu_focus.focusandroid.feature.auth.presentation

import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.usecase.AutoLoginUseCase
import com.kmu_focus.focusandroid.feature.auth.domain.usecase.KakaoLoginUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var kakaoLoginUseCase: KakaoLoginUseCase
    private lateinit var autoLoginUseCase: AutoLoginUseCase
    private lateinit var viewModel: AuthViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        kakaoLoginUseCase = mockk()
        autoLoginUseCase = mockk()
        viewModel = AuthViewModel(kakaoLoginUseCase, autoLoginUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태는 Loading이다`() {
        assertEquals(AuthUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `자동 로그인 성공 시 Success 상태가 된다`() = runTest {
        coEvery { autoLoginUseCase() } returns Result.success(true)

        viewModel.tryAutoLogin()

        assertEquals(AuthUiState.Success, viewModel.uiState.value)
        coVerify(exactly = 1) { autoLoginUseCase() }
    }

    @Test
    fun `자동 로그인 토큰 만료 시 NeedLogin 상태가 된다`() = runTest {
        coEvery { autoLoginUseCase() } returns Result.success(false)

        viewModel.tryAutoLogin()

        assertEquals(AuthUiState.NeedLogin, viewModel.uiState.value)
    }

    @Test
    fun `자동 로그인 실패 시 NeedLogin 상태가 된다`() = runTest {
        coEvery { autoLoginUseCase() } returns Result.failure(AuthError.TokenMissing)

        viewModel.tryAutoLogin()

        assertEquals(AuthUiState.NeedLogin, viewModel.uiState.value)
    }

    @Test
    fun `자동 로그인 중 네트워크 오류 시 Error 상태가 된다`() = runTest {
        coEvery { autoLoginUseCase() } returns Result.failure(
            AuthError.Network("네트워크 오류")
        )

        viewModel.tryAutoLogin()

        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("네트워크 오류", (state as AuthUiState.Error).message)
    }

    @Test
    fun `카카오 로그인 성공 시 Success 상태가 된다`() = runTest {
        val context = mockk<Any>()
        coEvery { kakaoLoginUseCase(context) } returns Result.success("access_token_123")

        viewModel.kakaoLogin(context)

        assertEquals(AuthUiState.Success, viewModel.uiState.value)
    }

    @Test
    fun `카카오 로그인 실패 시 Error 상태가 된다`() = runTest {
        val context = mockk<Any>()
        coEvery { kakaoLoginUseCase(context) } returns Result.failure(
            AuthError.UserCancelled
        )

        viewModel.kakaoLogin(context)

        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("로그인 취소", (state as AuthUiState.Error).message)
    }

    @Test
    fun `카카오 로그인 중 Loading 상태를 거친다`() = runTest {
        val context = mockk<Any>()
        val states = mutableListOf<AuthUiState>()
        coEvery { kakaoLoginUseCase(context) } coAnswers {
            states.add(viewModel.uiState.value)
            Result.success("token")
        }

        viewModel.kakaoLogin(context)

        assertTrue(states.contains(AuthUiState.Loading))
    }

    @Test
    fun `로그인 후 다시 로그인 시도가 가능하다`() = runTest {
        val context = mockk<Any>()
        coEvery { kakaoLoginUseCase(context) } returns Result.failure(
            AuthError.Unexpected("첫 번째 실패")
        )

        viewModel.kakaoLogin(context)
        assertTrue(viewModel.uiState.value is AuthUiState.Error)

        coEvery { kakaoLoginUseCase(context) } returns Result.success("token")

        viewModel.kakaoLogin(context)
        assertEquals(AuthUiState.Success, viewModel.uiState.value)
    }
}
