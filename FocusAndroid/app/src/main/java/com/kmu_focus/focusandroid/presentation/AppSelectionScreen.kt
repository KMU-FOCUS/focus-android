package com.kmu_focus.focusandroid.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosGradientBackground
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPrimaryButton
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSectionCard
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSecondaryButton
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosStatusChip
import com.kmu_focus.focusandroid.feature.account.presentation.mypage.MyPageViewModel
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthScreen
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthSessionViewModel
import com.kmu_focus.focusandroid.feature.video.presentation.main.MainScreen

@Composable
fun AppSelectionScreen(
    modifier: Modifier = Modifier,
) {
    val authSessionViewModel: AuthSessionViewModel = hiltViewModel()
    val isLoggedIn by authSessionViewModel.isLoggedIn.collectAsStateWithLifecycle()
    var isVideoGuestMode by rememberSaveable { mutableStateOf(false) }

    if (!isLoggedIn && !isVideoGuestMode) {
        AuthScreen(
            onLoginSuccess = { },
            onContinueWithoutLogin = { isVideoGuestMode = true },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (!isLoggedIn) {
        MainScreen(
            onBackToModeSelection = { isVideoGuestMode = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    AuthenticatedFocusEntryScreen(modifier = modifier)
}

@Composable
private fun AuthenticatedFocusEntryScreen(
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isChzzkConnected = uiState.chzzkStatus?.connected == true

    LaunchedEffect(uiState.pendingExternalUrl) {
        val targetUrl = uiState.pendingExternalUrl ?: return@LaunchedEffect
        uriHandler.openUri(targetUrl)
        viewModel.consumePendingExternalUrl()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshChzzkStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isChzzkConnected) {
        MainShellScreen(modifier = modifier.fillMaxSize())
        return
    }

    FocusChzzkGateScreen(
        modifier = modifier.fillMaxSize(),
        channelName = uiState.chzzkStatus?.channelName,
        watchUrl = uiState.chzzkStatus?.watchUrl,
        isLoading = uiState.isChzzkLoading || uiState.isChzzkActionInProgress,
        isAwaitingConnection = uiState.isAwaitingChzzkConnection,
        errorMessage = uiState.error,
        onConnect = viewModel::startChzzkConnect,
        onRefresh = viewModel::refreshChzzkStatus,
        onLogout = viewModel::logout,
        isLoggingOut = uiState.isLoggingOut,
    )
}

@Composable
private fun FocusChzzkGateScreen(
    channelName: String?,
    watchUrl: String?,
    isLoading: Boolean,
    isAwaitingConnection: Boolean,
    errorMessage: String?,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    isLoggingOut: Boolean,
    modifier: Modifier = Modifier,
) {
    FocusIosGradientBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                FocusIosStatusChip(
                    text = "카카오 로그인 완료",
                    containerColor = FocusIosPalette.SecondarySoft,
                    contentColor = FocusIosPalette.Secondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    text = "치지직 연동이 필요해요",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = FocusIosPalette.Text,
                )
                androidx.compose.material3.Text(
                    text = "카카오 로그인은 완료됐고, 이제 치지직 채널만 연결하면 바로 방송을 시작할 수 있어요.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = FocusIosPalette.TextMuted,
                )
            }

            FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                GateInfoRow(title = "카카오 로그인", value = "완료")
                GateInfoRow(title = "치지직 채널", value = channelName ?: "아직 연결되지 않음")
                if (!watchUrl.isNullOrBlank()) {
                    GateInfoRow(title = "연결 채널", value = watchUrl)
                }
                if (isAwaitingConnection) {
                    FocusIosStatusChip(
                        text = "연동 완료 후 상태 확인 대기 중",
                        containerColor = FocusIosPalette.WarningSoft,
                        contentColor = FocusIosPalette.Warning,
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text(
                        text = errorMessage,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = FocusIosPalette.Danger,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            FocusIosPrimaryButton(
                text = if (isLoading) "연동 페이지 준비 중..." else "치지직 연동하기",
                onClick = onConnect,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            FocusIosSecondaryButton(
                text = "연동 완료 후 상태 다시 확인",
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            FocusIosSecondaryButton(
                text = if (isLoggingOut) "로그아웃 중..." else "로그아웃",
                onClick = onLogout,
                enabled = !isLoggingOut,
                modifier = Modifier.fillMaxWidth(),
                accentColor = FocusIosPalette.Danger,
            )
        }
    }
}

@Composable
private fun GateInfoRow(
    title: String,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        androidx.compose.material3.Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = FocusIosPalette.TextMuted,
        )
        androidx.compose.material3.Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = FocusIosPalette.Text,
        )
    }
}
