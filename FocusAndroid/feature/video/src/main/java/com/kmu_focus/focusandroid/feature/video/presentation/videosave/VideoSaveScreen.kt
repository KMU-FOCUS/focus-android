package com.kmu_focus.focusandroid.feature.video.presentation.videosave

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette

@Composable
fun VideoSaveScreen(
    videoUri: String,
    modifier: Modifier = Modifier,
    viewModel: VideoSaveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.transcodeProgress,
        label = "transcodeProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            uiState.isSaving -> {
                Text(
                    text = "트랜스코딩 중... ${(uiState.transcodeProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusIosPalette.Text,
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    color = FocusIosPalette.Danger,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.savedFilePath != null -> {
                Text(
                    text = "저장 완료",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusIosPalette.Secondary,
                )
            }
            else -> {
                Text(
                    text = "동영상 재생이 끝나면 자동으로 저장됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = FocusIosPalette.TextMuted,
                )
            }
        }
    }
}
