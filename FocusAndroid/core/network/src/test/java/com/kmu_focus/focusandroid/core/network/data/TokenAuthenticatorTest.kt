package com.kmu_focus.focusandroid.core.network.data

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var tokenRefreshService: TokenRefreshService
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenStore = mockk(relaxed = true)
        tokenRefreshService = mockk(relaxed = true)
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, tokenRefreshService))
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `401 응답 시 토큰 리프레시를 시도하고 재요청한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "expired_token" andThen "new_token"
        coEvery { tokenRefreshService.refresh() } returns true

        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(2, mockWebServer.requestCount)

        val firstRequest = mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()
        assertEquals("Bearer expired_token", firstRequest.getHeader("Authorization"))
        assertEquals("Bearer new_token", secondRequest.getHeader("Authorization"))
    }

    @Test
    fun `401 응답 후 리프레시 실패 시 401을 그대로 반환한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "expired_token"
        coEvery { tokenRefreshService.refresh() } returns false

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(401, response.code)
        assertEquals(1, mockWebServer.requestCount)
    }
}
