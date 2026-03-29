package com.kmu_focus.focusandroid.feature.auth.data.repository

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.core.network.dto.AppTokenResponse
import com.kmu_focus.focusandroid.feature.auth.data.remote.AuthApi
import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ServerAuthRepositoryImplTest {

    private lateinit var authApi: AuthApi
    private lateinit var tokenStore: TokenStore
    private lateinit var repository: ServerAuthRepositoryImpl

    @Before
    fun setup() {
        authApi = mockk()
        tokenStore = mockk(relaxed = true)
        repository = ServerAuthRepositoryImpl(authApi, tokenStore)
    }

    @Test
    fun `로그인 성공 시 토큰을 저장하고 성공을 반환한다`() = runTest {
        val tokenResponse = AppTokenResponse("server_access", "server_refresh")
        val apiResponse = ApiResponse(success = true, message = "OK", data = tokenResponse)
        coEvery { authApi.kakaoLogin(any()) } returns Response.success(apiResponse)

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isSuccess)
        coVerify { tokenStore.save("server_access", "server_refresh") }
    }

    @Test
    fun `서버가 success false를 반환하면 실패한다`() = runTest {
        val apiResponse = ApiResponse<AppTokenResponse>(
            success = false,
            message = "외부 서버와 통신 과정 중 에러가 발생했습니다.",
            data = null,
        )
        coEvery { authApi.kakaoLogin(any()) } returns Response.success(apiResponse)

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.Network)
    }

    @Test
    fun `서버가 400을 반환하면 실패한다`() = runTest {
        coEvery { authApi.kakaoLogin(any()) } returns Response.error(
            400,
            """{"success":false,"message":"잘못된 요청","errorTitle":"InvalidInputValue","errorCode":400}"""
                .toResponseBody(),
        )

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
    }

    @Test
    fun `서버가 500을 반환하면 실패한다`() = runTest {
        coEvery { authApi.kakaoLogin(any()) } returns Response.error(
            500,
            "Internal Server Error".toResponseBody(),
        )

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
    }

    @Test
    fun `네트워크 예외 발생 시 실패한다`() = runTest {
        coEvery { authApi.kakaoLogin(any()) } throws java.io.IOException("네트워크 끊김")

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.Network)
    }

    @Test
    fun `응답 data가 null이면 실패한다`() = runTest {
        val apiResponse = ApiResponse<AppTokenResponse>(
            success = true,
            message = "OK",
            data = null,
        )
        coEvery { authApi.kakaoLogin(any()) } returns Response.success(apiResponse)

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
    }
}
