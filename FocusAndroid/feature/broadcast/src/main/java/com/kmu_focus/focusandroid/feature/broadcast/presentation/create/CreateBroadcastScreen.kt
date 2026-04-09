package com.kmu_focus.focusandroid.feature.broadcast.presentation.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus

@Composable
fun CreateBroadcastScreen(
    onBroadcastStarted: (broadcastId: String, streamKey: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: CreateBroadcastViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var handledStartedBroadcastId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        uiState.createdBroadcast?.broadcastId,
        uiState.createdBroadcast?.status,
        uiState.createdBroadcast?.streamKey,
    ) {
        val broadcast = uiState.createdBroadcast ?: return@LaunchedEffect
        if (
            broadcast.status == BroadcastStatus.ON_AIR &&
            broadcast.streamKey.isNotBlank() &&
            handledStartedBroadcastId != broadcast.broadcastId
        ) {
            handledStartedBroadcastId = broadcast.broadcastId
            onBroadcastStarted(broadcast.broadcastId, broadcast.streamKey)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "방송 생성",
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedButton(onClick = onBack) {
                Text("뒤로")
            }
        }

        OutlinedTextField(
            value = uiState.title,
            onValueChange = viewModel::updateTitle,
            label = { Text("제목") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = uiState.avatarId,
            onValueChange = viewModel::selectAvatar,
            label = { Text("아바타 ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "아바타 선택",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarButton(
                    avatarId = "avatar-a",
                    selectedAvatarId = uiState.avatarId,
                    onSelect = viewModel::selectAvatar,
                )
                AvatarButton(
                    avatarId = "avatar-b",
                    selectedAvatarId = uiState.avatarId,
                    onSelect = viewModel::selectAvatar,
                )
                AvatarButton(
                    avatarId = "avatar-c",
                    selectedAvatarId = uiState.avatarId,
                    onSelect = viewModel::selectAvatar,
                )
            }
        }

        Button(
            onClick = viewModel::createBroadcast,
            enabled = !uiState.isCreating,
        ) {
            Text("방송 생성")
        }

        Button(
            onClick = viewModel::startBroadcast,
            enabled = !uiState.isCreating &&
                uiState.createdBroadcast != null &&
                uiState.avatarId.isNotBlank(),
        ) {
            Text("방송 시작")
        }

        if (uiState.isCreating) {
            CircularProgressIndicator()
        }

        uiState.createdBroadcast?.let { broadcast ->
            Text("방송 ID: ${broadcast.broadcastId}")
            Text("상태: ${broadcast.status}")
            Text("스트림 키: ${broadcast.streamKey}")
            broadcast.hlsUrl?.let { Text("HLS: $it") }
        }

        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AvatarButton(
    avatarId: String,
    selectedAvatarId: String,
    onSelect: (String) -> Unit,
) {
    val selected = avatarId == selectedAvatarId
    if (selected) {
        Button(onClick = { onSelect(avatarId) }) {
            Text(avatarId)
        }
    } else {
        OutlinedButton(onClick = { onSelect(avatarId) }) {
            Text(avatarId)
        }
    }
}
