package com.kmu_focus.focusandroid.presentation

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.R
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthScreen
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthSessionViewModel
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.BroadcastCameraScreen
import com.kmu_focus.focusandroid.feature.broadcast.presentation.create.CreateBroadcastScreen
import com.kmu_focus.focusandroid.feature.broadcast.presentation.list.BroadcastListScreen
import com.kmu_focus.focusandroid.feature.camera.presentation.CameraScreen
import com.kmu_focus.focusandroid.feature.video.presentation.main.MainScreen
import com.kmu_focus.focusandroid.feature.video.presentation.videosave.VideoSaveViewModel
import kotlinx.coroutines.delay

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
    var activeHlsUrl by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            selectedMode = null
            activeBroadcastId = ""
            activeStreamKey = ""
            activeHlsUrl = ""
        }
    }

    LaunchedEffect(selectedMode, activeBroadcastId, activeStreamKey) {
        if (
            selectedMode == AppMode.BROADCAST_CAMERA &&
            (activeBroadcastId.isBlank() || activeStreamKey.isBlank())
        ) {
            selectedMode = AppMode.BROADCAST_LIST
            activeHlsUrl = ""
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
                onNavigateToCamera = { broadcastId, streamKey ->
                    activeBroadcastId = broadcastId
                    activeStreamKey = streamKey
                    activeHlsUrl = ""
                    selectedMode = AppMode.BROADCAST_CAMERA
                },
            )
        }

        AppMode.BROADCAST_CAMERA -> {
            BroadcastCameraScreen(
                broadcastId = activeBroadcastId,
                streamKey = activeStreamKey,
                hlsUrl = activeHlsUrl,
                onBack = {
                    selectedMode = AppMode.BROADCAST_LIST
                    activeBroadcastId = ""
                    activeStreamKey = ""
                    activeHlsUrl = ""
                },
            )
        }

        null -> {
            StartSelectionScreen(
                modifier = modifier,
                onVideoSelected = { selectedMode = AppMode.VIDEO },
                onCameraSelected = { selectedMode = AppMode.CAMERA },
                onBroadcastSelected = { selectedMode = AppMode.BROADCAST_LIST },
            )
        }
    }
}

@Composable
private fun StartSelectionScreen(
    modifier: Modifier = Modifier,
    onVideoSelected: () -> Unit,
    onCameraSelected: () -> Unit,
    onBroadcastSelected: () -> Unit,
) {
    val slides = remember {
        listOf(
            IntroCopy(
                title = "스트리머는 그대로, 배경 인물은 안전하게",
                subtitle = "초상권 걱정 없는 스마트 라이브 스트리밍",
            ),
            IntroCopy(
                title = "방송중 스쳐가는 사람까지",
                subtitle = "자동으로 감지해 아바타로 전환해요",
            ),
            IntroCopy(
                title = "복잡한 설정 없이 바로 시작하는",
                subtitle = "안전한 라이브 스트리밍",
            ),
        )
    }
    var selectedSlide by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(slides.size) {
        while (true) {
            delay(3_200L)
            selectedSlide = (selectedSlide + 1) % slides.size
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.focus_ios_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.16f),
                            Color.Black.copy(alpha = 0.34f),
                            Color.Black.copy(alpha = 0.74f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FOCUS",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                PageIndicator(
                    pageCount = slides.size,
                    selectedPage = selectedSlide,
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.35f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = slides[selectedSlide].title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = slides[selectedSlide].subtitle,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryGradientButton(
                text = "방송 준비하기",
                onClick = onBroadcastSelected,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GhostModeButton(
                    text = "카메라 분석",
                    onClick = onCameraSelected,
                    modifier = Modifier.weight(1f),
                )
                GhostModeButton(
                    text = "동영상 분석",
                    onClick = onVideoSelected,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PrimaryGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        shadowElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF7C3AED),
                            Color(0xFF2563EB),
                        ),
                    ),
                )
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GhostModeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    selectedPage: Int,
    activeColor: Color,
    inactiveColor: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == selectedPage) 22.dp else 8.dp)
                    .height(8.dp)
                    .background(
                        color = if (index == selectedPage) activeColor else inactiveColor,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
        }
    }
}

private data class IntroCopy(
    val title: String,
    val subtitle: String,
)
