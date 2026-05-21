package com.kmu_focus.focusandroid.feature.account.domain.repository

import com.kmu_focus.focusandroid.feature.account.domain.entity.ChzzkConnectionStatus
import com.kmu_focus.focusandroid.feature.account.domain.entity.UserProfile
import com.kmu_focus.focusandroid.feature.account.domain.entity.YoutubeConnectionStatus

interface AccountRepository {
    suspend fun getCurrentUser(): Result<UserProfile>
    suspend fun logout(): Result<Unit>
    suspend fun getChzzkConnectionStatus(): Result<ChzzkConnectionStatus>
    suspend fun getChzzkConnectUrl(): Result<String>
    suspend fun disconnectChzzk(): Result<Unit>
    suspend fun getYoutubeConnectionStatus(): Result<YoutubeConnectionStatus>
    suspend fun getYoutubeConnectUrl(): Result<String>
    suspend fun disconnectYoutube(): Result<Unit>
}
