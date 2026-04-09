package com.kmu_focus.focusandroid.feature.broadcast.presentation.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CreateBroadcastScreen(
    viewModel: CreateBroadcastViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Create Broadcast",
            style = MaterialTheme.typography.headlineSmall,
        )

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

        Button(
            onClick = viewModel::createBroadcast,
            enabled = !uiState.isCreating,
        ) {
            Text("방송 생성")
        }

        Button(
            onClick = viewModel::startBroadcast,
            enabled = !uiState.isCreating && uiState.createdBroadcast != null,
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
