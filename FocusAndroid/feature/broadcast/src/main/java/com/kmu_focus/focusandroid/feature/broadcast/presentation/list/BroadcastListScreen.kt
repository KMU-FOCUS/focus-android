package com.kmu_focus.focusandroid.feature.broadcast.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus

@Composable
fun BroadcastListScreen(
    onNavigateToCreate: () -> Unit = {},
    onNavigateToBroadcast: (broadcastId: String) -> Unit = {},
    viewModel: BroadcastListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCreate) {
                Text("+")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "방송 목록",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Button(onClick = viewModel::refresh) {
                    Text("새로고침")
                }
            }

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.broadcasts,
                        key = { it.broadcastId },
                    ) { broadcast ->
                        BroadcastItemCard(
                            broadcast = broadcast,
                            onDelete = { viewModel.deleteBroadcast(broadcast.broadcastId) },
                            onNavigateToBroadcast = { onNavigateToBroadcast(broadcast.broadcastId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastItemCard(
    broadcast: Broadcast,
    onDelete: () -> Unit,
    onNavigateToBroadcast: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = broadcast.status == BroadcastStatus.ON_AIR, onClick = onNavigateToBroadcast),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = broadcast.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "방장: ${broadcast.memberName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                StatusBadge(status = broadcast.status)
            }

            broadcast.hlsUrl?.let {
                Text(
                    text = "HLS: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onNavigateToBroadcast,
                    enabled = broadcast.status == BroadcastStatus.ON_AIR,
                ) {
                    Text("방송 입장")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("삭제")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: BroadcastStatus) {
    val (label, iconText, color) = when (status) {
        BroadcastStatus.READY -> Triple("대기", "R", Color(0xFF8E24AA))
        BroadcastStatus.ON_AIR -> Triple("방송 중", "O", Color(0xFFD32F2F))
        BroadcastStatus.ENDED -> Triple("종료", "E", Color(0xFF455A64))
        BroadcastStatus.ERROR -> Triple("오류", "X", Color(0xFFF57C00))
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(color = color, shape = CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = iconText,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
