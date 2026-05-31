package com.kmu_focus.focusandroid.feature.broadcast.presentation.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosGradientBackground
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPanelHeader
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPrimaryButton
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSectionCard
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSecondaryButton

@Composable
fun CreateBroadcastScreen(
    onNavigateToCamera: (broadcastId: String, streamKey: String, title: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CreateBroadcastViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FocusIosGradientBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                FocusIosPanelHeader(
                    title = "방송 실험실",
                    subtitle = "방송 정보를 입력하고 송출 준비를 시작할 수 있어요.",
                )
                FocusIosSecondaryButton(
                    text = "라이브 홈으로 돌아가기",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "새 방송 생성",
                    style = MaterialTheme.typography.titleLarge,
                    color = FocusIosPalette.Text,
                )
                Text(
                    text = "메인 UX에서는 자동 생성되지만, 이 화면에서는 기존 수동 생성 흐름을 계속 테스트할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusIosPalette.TextMuted,
                )
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text("방송 제목") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                FocusIosPrimaryButton(
                    text = if (uiState.isCreating) "방송 생성 중..." else "방송 생성",
                    onClick = viewModel::createBroadcast,
                    enabled = !uiState.isCreating,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.isCreating) {
                    CircularProgressIndicator(color = FocusIosPalette.Primary)
                }
            }

            uiState.createdBroadcast?.let { broadcast ->
                FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "생성 완료",
                        style = MaterialTheme.typography.titleLarge,
                        color = FocusIosPalette.Text,
                    )
                    BroadcastMetaRow(label = "방송 제목", value = broadcast.title)
                    BroadcastMetaRow(label = "Broadcast ID", value = broadcast.broadcastId)
                    BroadcastMetaRow(label = "라이브 상태", value = broadcast.liveStatus.name)
                    broadcast.hlsUrl?.let { hlsUrl ->
                        BroadcastMetaRow(label = "HLS", value = hlsUrl)
                    }
                    FocusIosPrimaryButton(
                        text = "이 세션으로 라이브 홈 열기",
                        onClick = {
                            onNavigateToCamera(
                                broadcast.broadcastId,
                                broadcast.streamKey,
                                broadcast.title,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
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
        }
    }
}

@Composable
private fun BroadcastMetaRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FocusIosPalette.TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = FocusIosPalette.Text,
        )
    }
}
