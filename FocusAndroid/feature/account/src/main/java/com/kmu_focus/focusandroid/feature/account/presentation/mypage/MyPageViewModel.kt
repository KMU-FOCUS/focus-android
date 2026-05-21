package com.kmu_focus.focusandroid.feature.account.presentation.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.feature.account.domain.entity.ChzzkConnectionStatus
import com.kmu_focus.focusandroid.feature.account.domain.entity.UserProfile
import com.kmu_focus.focusandroid.feature.account.domain.entity.YoutubeConnectionStatus
import com.kmu_focus.focusandroid.feature.account.domain.usecase.DisconnectChzzkUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.DisconnectYoutubeUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.GetCurrentUserUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.GetChzzkConnectUrlUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.GetChzzkConnectionStatusUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.GetYoutubeConnectUrlUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.GetYoutubeConnectionStatusUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyPageUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val profileError: String? = null,
    val isChzzkLoading: Boolean = true,
    val chzzkStatus: ChzzkConnectionStatus? = null,
    val chzzkError: String? = null,
    val isYoutubeLoading: Boolean = true,
    val youtubeStatus: YoutubeConnectionStatus? = null,
    val youtubeError: String? = null,
    val isPlatformActionInProgress: Boolean = false,
    val isAwaitingChzzkConnection: Boolean = false,
    val isAwaitingYoutubeConnection: Boolean = false,
    val pendingExternalUrl: String? = null,
    val isLoggingOut: Boolean = false,
    val isLoggedOut: Boolean = false,
    val actionError: String? = null,
) {
    val error: String?
        get() = actionError ?: profileError ?: chzzkError ?: youtubeError
}

@HiltViewModel
class MyPageViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getChzzkConnectionStatusUseCase: GetChzzkConnectionStatusUseCase,
    private val getChzzkConnectUrlUseCase: GetChzzkConnectUrlUseCase,
    private val disconnectChzzkUseCase: DisconnectChzzkUseCase,
    private val getYoutubeConnectionStatusUseCase: GetYoutubeConnectionStatusUseCase,
    private val getYoutubeConnectUrlUseCase: GetYoutubeConnectUrlUseCase,
    private val disconnectYoutubeUseCase: DisconnectYoutubeUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState())
    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        refreshPlatformStatuses()
    }

    fun loadUserProfile() {
        _uiState.update { current ->
            current.copy(
                isLoading = true,
                profileError = null,
            )
        }

        viewModelScope.launch {
            getCurrentUserUseCase()
                .onSuccess { profile ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            profile = profile,
                            profileError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            profile = null,
                            profileError = throwable.message ?: "내 정보 조회 실패",
                        )
                    }
                }
        }
    }

    fun logout() {
        if (_uiState.value.isLoggingOut) {
            return
        }

        _uiState.update { current ->
            current.copy(
                isLoggingOut = true,
                actionError = null,
            )
        }

        viewModelScope.launch {
            logoutUseCase()
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            isLoggingOut = false,
                            isLoggedOut = true,
                            actionError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoggingOut = false,
                            isLoggedOut = false,
                            actionError = throwable.message ?: "로그아웃 실패",
                        )
                    }
                }
        }
    }

    fun refreshAll() {
        loadUserProfile()
        refreshPlatformStatuses()
    }

    fun refreshPlatformStatuses() {
        refreshChzzkStatus()
        refreshYoutubeStatus()
    }

    fun refreshChzzkStatus() {
        _uiState.update { current ->
            current.copy(
                isChzzkLoading = true,
                chzzkError = null,
            )
        }

        viewModelScope.launch {
            getChzzkConnectionStatusUseCase()
                .onSuccess { status ->
                    _uiState.update { current ->
                        current.copy(
                            isChzzkLoading = false,
                            chzzkStatus = status,
                            chzzkError = null,
                            isAwaitingChzzkConnection = current.isAwaitingChzzkConnection && !status.connected,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isChzzkLoading = false,
                            chzzkStatus = null,
                            chzzkError = throwable.message ?: "치지직 연동 상태 조회 실패",
                        )
                    }
                }
        }
    }

    fun refreshYoutubeStatus() {
        _uiState.update { current ->
            current.copy(
                isYoutubeLoading = true,
                youtubeError = null,
            )
        }

        viewModelScope.launch {
            getYoutubeConnectionStatusUseCase()
                .onSuccess { status ->
                    _uiState.update { current ->
                        current.copy(
                            isYoutubeLoading = false,
                            youtubeStatus = status,
                            youtubeError = null,
                            isAwaitingYoutubeConnection = current.isAwaitingYoutubeConnection && !status.connected,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isYoutubeLoading = false,
                            youtubeStatus = null,
                            youtubeError = throwable.message ?: "유튜브 연동 상태 조회 실패",
                        )
                    }
                }
        }
    }

    fun startChzzkConnect() {
        if (_uiState.value.isPlatformActionInProgress) {
            return
        }

        _uiState.update { current ->
            current.copy(
                isPlatformActionInProgress = true,
                actionError = null,
            )
        }

        viewModelScope.launch {
            getChzzkConnectUrlUseCase()
                .onSuccess { url ->
                    _uiState.update { current ->
                        current.copy(
                            isPlatformActionInProgress = false,
                            isAwaitingChzzkConnection = true,
                            isAwaitingYoutubeConnection = false,
                            pendingExternalUrl = url,
                            actionError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isPlatformActionInProgress = false,
                            isAwaitingChzzkConnection = false,
                            pendingExternalUrl = null,
                            actionError = throwable.message ?: "치지직 연동 URL 조회 실패",
                        )
                    }
                }
        }
    }

    fun startYoutubeConnect() {
        if (_uiState.value.isPlatformActionInProgress) {
            return
        }

        _uiState.update { current ->
            current.copy(
                isPlatformActionInProgress = true,
                actionError = null,
            )
        }

        viewModelScope.launch {
            getYoutubeConnectUrlUseCase()
                .onSuccess { url ->
                    _uiState.update { current ->
                        current.copy(
                            isPlatformActionInProgress = false,
                            isAwaitingYoutubeConnection = true,
                            isAwaitingChzzkConnection = false,
                            pendingExternalUrl = url,
                            actionError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isPlatformActionInProgress = false,
                            isAwaitingYoutubeConnection = false,
                            pendingExternalUrl = null,
                            actionError = throwable.message ?: "유튜브 연동 URL 조회 실패",
                        )
                    }
                }
        }
    }

    fun consumePendingExternalUrl() {
        _uiState.update { current ->
            current.copy(pendingExternalUrl = null)
        }
    }

    fun disconnectChzzk() {
        if (_uiState.value.isPlatformActionInProgress) {
            return
        }

        _uiState.update { current ->
            current.copy(
                isPlatformActionInProgress = true,
                actionError = null,
            )
        }

        viewModelScope.launch {
            disconnectChzzkUseCase()
                .onSuccess {
                    refreshChzzkStatus()
                    _uiState.update { current ->
                        current.copy(
                            isPlatformActionInProgress = false,
                            isAwaitingChzzkConnection = false,
                            actionError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isPlatformActionInProgress = false,
                            actionError = throwable.message ?: "치지직 연동 해제 실패",
                        )
                    }
                }
        }
    }

    fun disconnectYoutube() {
        if (_uiState.value.isPlatformActionInProgress) {
            return
        }

        _uiState.update { current ->
            current.copy(
                isPlatformActionInProgress = true,
                actionError = null,
            )
        }

        viewModelScope.launch {
            disconnectYoutubeUseCase()
                .onSuccess {
                    refreshYoutubeStatus()
                    _uiState.update { current ->
                        current.copy(
                            isPlatformActionInProgress = false,
                            isAwaitingYoutubeConnection = false,
                            actionError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isPlatformActionInProgress = false,
                            actionError = throwable.message ?: "유튜브 연동 해제 실패",
                        )
                    }
                }
        }
    }
}
