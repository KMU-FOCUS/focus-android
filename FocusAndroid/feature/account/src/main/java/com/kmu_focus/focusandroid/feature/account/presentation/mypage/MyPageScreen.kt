package com.kmu_focus.focusandroid.feature.account.presentation.mypage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosGradientBackground
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPanelHeader
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPrimaryButton
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSectionCard
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSecondaryButton
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosStatusChip
import com.kmu_focus.focusandroid.feature.account.domain.entity.ChzzkConnectionStatus

@Composable
fun MyPageScreen(
    onLoggedOut: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLoggedOut()
        }
    }

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

    FocusIosGradientBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                FocusIosPanelHeader(
                    title = "계정 / 치지직 상태",
                    subtitle = "계정 정보와 치지직 연결 상태를 확인할 수 있어요.",
                )
                FocusIosSecondaryButton(
                    text = "라이브 홈으로 돌아가기",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (uiState.isLoading) {
                FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = FocusIosPalette.Primary)
                    Text(
                        text = "계정 정보를 불러오는 중입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FocusIosPalette.TextMuted,
                    )
                }
            } else {
                FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = uiState.profile?.name ?: "이름 없음",
                        style = MaterialTheme.typography.headlineSmall,
                        color = FocusIosPalette.Text,
                    )
                    Text(
                        text = uiState.profile?.email ?: "이메일 정보 없음",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FocusIosPalette.TextMuted,
                    )
                }
            }

            FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "치지직 채널 연동",
                    style = MaterialTheme.typography.titleLarge,
                    color = FocusIosPalette.Text,
                )
                when {
                    uiState.isChzzkLoading -> {
                        CircularProgressIndicator(color = FocusIosPalette.Primary)
                        Text(
                            text = "연동 상태를 확인하는 중입니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FocusIosPalette.TextMuted,
                        )
                    }

                    else -> {
                        ChzzkStatusSection(status = uiState.chzzkStatus)
                    }
                }
                FocusIosPrimaryButton(
                    text = if (uiState.isChzzkActionInProgress) "연동 처리 중..." else "치지직 연동하기",
                    onClick = viewModel::startChzzkConnect,
                    enabled = !uiState.isChzzkActionInProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                FocusIosSecondaryButton(
                    text = "연동 상태 새로고침",
                    onClick = viewModel::refreshChzzkStatus,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.chzzkStatus?.connected == true) {
                    FocusIosSecondaryButton(
                        text = "치지직 연동 해제",
                        onClick = viewModel::disconnectChzzk,
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = FocusIosPalette.Warning,
                    )
                }
            }

            uiState.error?.let { error ->
                FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FocusIosPalette.Danger,
                    )
                }
            }

            FocusIosSecondaryButton(
                text = "전체 정보 새로고침",
                onClick = viewModel::refreshAll,
                modifier = Modifier.fillMaxWidth(),
            )
            FocusIosSecondaryButton(
                text = if (uiState.isLoggingOut) "로그아웃 중..." else "로그아웃",
                onClick = viewModel::logout,
                enabled = !uiState.isLoggingOut,
                modifier = Modifier.fillMaxWidth(),
                accentColor = FocusIosPalette.Danger,
            )
        }
    }
}

@Composable
private fun ChzzkStatusSection(
    status: ChzzkConnectionStatus?,
) {
    val connected = status?.connected == true
    FocusIosStatusChip(
        text = if (connected) "연동 완료" else "연동 필요",
        containerColor = if (connected) Color(0xFFE8F6F1) else Color(0xFFFFF3E0),
        contentColor = if (connected) FocusIosPalette.Secondary else FocusIosPalette.Warning,
    )
    Text(
        text = status?.channelName ?: "연결된 채널이 없습니다.",
        style = MaterialTheme.typography.bodyLarge,
        color = FocusIosPalette.Text,
    )
    if (!status?.watchUrl.isNullOrBlank()) {
        Text(
            text = status?.watchUrl.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = FocusIosPalette.TextMuted,
        )
    }
}
