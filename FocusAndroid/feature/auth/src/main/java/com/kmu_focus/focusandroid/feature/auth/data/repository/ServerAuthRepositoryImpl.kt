package com.kmu_focus.focusandroid.feature.auth.data.repository

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import com.kmu_focus.focusandroid.feature.auth.data.remote.AuthApi
import com.kmu_focus.focusandroid.feature.auth.data.remote.dto.KakaoLoginRequest
import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.ServerAuthRepository
import java.io.IOException
import javax.inject.Inject
import retrofit2.Response

class ServerAuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) : ServerAuthRepository {

    override suspend fun loginWithKakaoToken(
        kakaoAccessToken: String,
    ): Result<Unit> {
        return try {
            val response = authApi.kakaoLogin(
                KakaoLoginRequest(code = kakaoAccessToken),
            )
            val body = response.body()
            val tokenData = body?.data

            when {
                response.isSuccessful && body?.success == true && tokenData != null -> {
                    tokenStore.save(
                        accessToken = tokenData.accessToken,
                        refreshToken = tokenData.refreshToken,
                    )
                    Result.success(Unit)
                }

                response.isSuccessful -> {
                    Result.failure(
                        AuthError.Network(
                            body?.message?.takeIf { it.isNotBlank() } ?: "서버 로그인 실패",
                        )
                    )
                }

                else -> {
                    Result.failure(AuthError.Network(response.extractErrorMessage()))
                }
            }
        } catch (exception: IOException) {
            Result.failure(
                AuthError.Network(
                    message = exception.message ?: "네트워크 오류",
                    cause = exception,
                )
            )
        } catch (throwable: Throwable) {
            Result.failure(
                AuthError.Unexpected(
                    message = throwable.message ?: "로그인 실패",
                    cause = throwable,
                )
            )
        }
    }

    private fun Response<*>.extractErrorMessage(): String {
        return errorBody()?.string()?.takeIf { it.isNotBlank() }
            ?: message().takeIf { it.isNotBlank() }
            ?: "서버 로그인 실패"
    }
}
