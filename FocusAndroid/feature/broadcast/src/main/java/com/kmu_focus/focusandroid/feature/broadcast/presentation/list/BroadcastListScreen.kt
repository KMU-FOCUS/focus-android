package com.kmu_focus.focusandroid.feature.broadcast.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosGradientBackground
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosMenuRow
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPanelHeader
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPrimaryButton
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSectionCard
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSecondaryButton
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast

@Composable
fun BroadcastListScreen(
    onBack: () -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    onNavigateToBroadcast: (broadcastId: String, streamKey: String, title: String, hlsUrl: String) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: BroadcastListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val totalCount = uiState.broadcasts.size

    FocusIosGradientBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                FocusIosPanelHeader(
                    title = "방송 실험실",
                    subtitle = "기존 방송 목록/삭제/수동 진입 로직을 여기서 계속 테스트할 수 있습니다.",
                )
                Text(
                    text = "등록된 방송 ${totalCount}개",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FocusIosPalette.Text,
                )
                FocusIosPrimaryButton(
                    text = "새 방송 만들기",
                    onClick = onNavigateToCreate,
                    modifier = Modifier.fillMaxWidth(),
                )
                FocusIosSecondaryButton(
                    text = "라이브 홈으로 돌아가기",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
                FocusIosSecondaryButton(
                    text = "목록 새로고침",
                    onClick = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = FocusIosPalette.Primary)
                    }
                }

                uiState.broadcasts.isEmpty() -> {
                    FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "생성된 방송이 없습니다.",
                            style = MaterialTheme.typography.titleMedium,
                            color = FocusIosPalette.Text,
                        )
                        Text(
                            text = "메인 라이브 홈은 자동 생성 방식을 쓰지만, 이 실험실에서는 목록 관리 기능을 그대로 확인할 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FocusIosPalette.TextMuted,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.broadcasts, key = { it.broadcastId }) { broadcast ->
                            BroadcastRow(
                                broadcast = broadcast,
                                onOpen = {
                                    onNavigateToBroadcast(
                                        broadcast.broadcastId,
                                        broadcast.streamKey,
                                        broadcast.title,
                                        broadcast.hlsUrl.orEmpty(),
                                    )
                                },
                                onDelete = { viewModel.deleteBroadcast(broadcast.broadcastId) },
                            )
                        }
                    }
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
        }
    }
}

@Composable
private fun BroadcastRow(
    broadcast: Broadcast,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = broadcast.title,
            style = MaterialTheme.typography.titleLarge,
            color = FocusIosPalette.Text,
        )
        Text(
            text = "방장 ${broadcast.memberName}",
            style = MaterialTheme.typography.bodyMedium,
            color = FocusIosPalette.TextMuted,
        )
        broadcast.watchUrl?.let { watchUrl ->
            FocusIosMenuRow(
                title = "시청 URL",
                subtitle = watchUrl,
                onClick = onOpen,
                leading = {
                    Text(
                        text = "W",
                        color = FocusIosPalette.Primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
        }
        Text(
            text = "상태 ${broadcast.liveStatus.name} · Stream ${broadcast.streamKey}",
            style = MaterialTheme.typography.bodySmall,
            color = FocusIosPalette.TextMuted,
        )
        FocusIosPrimaryButton(
            text = "이 세션으로 라이브 홈 열기",
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
        )
        FocusIosSecondaryButton(
            text = "방송 삭제",
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            accentColor = FocusIosPalette.Danger,
        )
    }
}
