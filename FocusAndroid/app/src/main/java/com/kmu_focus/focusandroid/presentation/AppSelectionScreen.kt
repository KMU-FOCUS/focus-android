package com.kmu_focus.focusandroid.presentation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthScreen
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthSessionViewModel
import com.kmu_focus.focusandroid.feature.broadcast.presentation.create.CreateBroadcastScreen
import com.kmu_focus.focusandroid.feature.broadcast.presentation.list.BroadcastListScreen
import com.kmu_focus.focusandroid.feature.camera.presentation.CameraScreen
import com.kmu_focus.focusandroid.feature.video.presentation.main.MainScreen
import com.kmu_focus.focusandroid.feature.video.presentation.videosave.VideoSaveViewModel
import java.io.File

enum class AppMode {
    VIDEO,
    CAMERA,
    BROADCAST_LIST,
    BROADCAST_CREATE,
    BROADCAST_CAMERA,
}

@Composable
fun AppSelectionScreen(
    modifier: Modifier = Modifier,
) {
    val authSessionViewModel: AuthSessionViewModel = hiltViewModel()
    val isLoggedIn by authSessionViewModel.isLoggedIn.collectAsStateWithLifecycle()
    var selectedMode by rememberSaveable { mutableStateOf<AppMode?>(null) }
    var activeBroadcastId by rememberSaveable { mutableStateOf("") }
    var activeStreamKey by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            selectedMode = null
            activeBroadcastId = ""
            activeStreamKey = ""
        }
    }

    LaunchedEffect(selectedMode, activeBroadcastId, activeStreamKey) {
        if (
            selectedMode == AppMode.BROADCAST_CAMERA &&
            (activeBroadcastId.isBlank() || activeStreamKey.isBlank())
        ) {
            selectedMode = AppMode.BROADCAST_LIST
        }
    }

    if (!isLoggedIn) {
        AuthScreen(
            onLoginSuccess = { },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val context = LocalContext.current
    val saveViewModel: VideoSaveViewModel = hiltViewModel()
    val saveUiState by saveViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(saveUiState.savedFilePath, saveUiState.error) {
        val savedPath = saveUiState.savedFilePath
        val error = saveUiState.error
        when {
            !savedPath.isNullOrBlank() -> {
                Toast.makeText(context, "갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
                saveViewModel.reset()
            }

            !error.isNullOrBlank() -> {
                Toast.makeText(context, "저장 실패: $error", Toast.LENGTH_SHORT).show()
                saveViewModel.reset()
            }
        }
    }

    when (selectedMode) {
        AppMode.VIDEO -> {
            MainScreen(
                modifier = modifier.fillMaxSize(),
                onBackToModeSelection = { selectedMode = null },
            )
        }

        AppMode.CAMERA -> {
            CameraScreen(
                onRecordingComplete = { file ->
                    saveViewModel.saveRecording(file, file.absolutePath)
                },
                onBack = { selectedMode = null },
                modifier = modifier,
            )
        }

        AppMode.BROADCAST_LIST -> {
            BroadcastListScreen(
                onNavigateToCreate = { selectedMode = AppMode.BROADCAST_CREATE },
                onNavigateToBroadcast = { broadcastId ->
                    Toast.makeText(
                        context,
                        "시청자용 방송 입장은 아직 연결되지 않았습니다. broadcastId=$broadcastId",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }

        AppMode.BROADCAST_CREATE -> {
            CreateBroadcastScreen(
                onBack = { selectedMode = AppMode.BROADCAST_LIST },
                onBroadcastStarted = { broadcastId, streamKey ->
                    activeBroadcastId = broadcastId
                    activeStreamKey = streamKey
                    selectedMode = AppMode.BROADCAST_CAMERA
                },
            )
        }

        AppMode.BROADCAST_CAMERA -> {
            BroadcastCameraHost(
                broadcastId = activeBroadcastId,
                streamKey = activeStreamKey,
                modifier = modifier,
                onBack = {
                    selectedMode = AppMode.BROADCAST_LIST
                    activeBroadcastId = ""
                    activeStreamKey = ""
                },
                onRecordingComplete = { file ->
                    saveViewModel.saveRecording(file, file.absolutePath)
                },
            )
        }

        null -> {
            SelectionButtons(
                modifier = modifier,
                onVideoSelected = { selectedMode = AppMode.VIDEO },
                onCameraSelected = { selectedMode = AppMode.CAMERA },
                onBroadcastSelected = { selectedMode = AppMode.BROADCAST_LIST },
            )
        }
    }
}

@Composable
private fun SelectionButtons(
    modifier: Modifier = Modifier,
    onVideoSelected: () -> Unit,
    onCameraSelected: () -> Unit,
    onBroadcastSelected: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("분석 모드를 선택하세요.")
        Button(
            onClick = onVideoSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("동영상 분석")
        }
        Button(
            onClick = onCameraSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("카메라 분석")
        }
        Button(
            onClick = onBroadcastSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("방송")
        }
    }
}

@Composable
private fun BroadcastCameraHost(
    broadcastId: String,
    streamKey: String,
    onRecordingComplete: (File) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CameraScreen(
            onRecordingComplete = onRecordingComplete,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.72f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "방송 모드",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Text(
                    text = "Broadcast ID: $broadcastId",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
                Text(
                    text = "Stream Key: $streamKey",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
        }
    }
}
