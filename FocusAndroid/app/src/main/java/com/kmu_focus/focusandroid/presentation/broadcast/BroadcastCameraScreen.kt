package com.kmu_focus.focusandroid.presentation.broadcast

import android.content.Context
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.kmu_focus.focusandroid.core.ui.insets.focusSafeDrawingPadding
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSecondaryButton
import com.kmu_focus.focusandroid.core.media.domain.entity.PrivacyMode
import com.kmu_focus.focusandroid.feature.broadcast.domain.config.BroadcastSrtInputProfile
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastOutputMode
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.StreamingPlatformConnection
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.displayTitle
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.BroadcastCameraViewModel
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.CompletedBroadcastReportSeed
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.PostBroadcastReportSheet
import com.kmu_focus.focusandroid.feature.camera.domain.entity.LensFacing
import com.kmu_focus.focusandroid.feature.camera.domain.entity.RegisteredOwner
import com.kmu_focus.focusandroid.feature.camera.presentation.CameraScreen
import com.kmu_focus.focusandroid.feature.camera.presentation.CameraViewModel
import java.io.File
import kotlinx.coroutines.delay

private const val BROADCAST_START_DELAY_MS = 2_000L
private const val RECORDING_START_TIMEOUT_MS = 5_000L
private const val START_API_TIMEOUT_MS = 12_000L

@Composable
fun BroadcastCameraScreen(
    availableOutputModes: List<BroadcastOutputMode>,
    platformConnections: List<StreamingPlatformConnection>,
    isPlatformActionInProgress: Boolean,
    onConnectPlatform: (BroadcastOutputMode) -> Unit,
    onDisconnectPlatform: (BroadcastOutputMode) -> Unit,
    onRefreshPlatforms: () -> Unit,
    onRootBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BroadcastCameraViewModel = hiltViewModel(),
    cameraViewModel: CameraViewModel = hiltViewModel(),
) {
    KeepImmersiveNavigationBars()

    val uiState by viewModel.uiState.collectAsState()
    val cameraUiState by cameraViewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var hasStartedRecorder by rememberSaveable(uiState.broadcastId) { mutableStateOf(false) }
    var hasRequestedServerStart by rememberSaveable(uiState.broadcastId) { mutableStateOf(false) }
    var isMenuPresented by rememberSaveable { mutableStateOf(false) }
    var liveStartedAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingOwnerDeletion by remember { mutableStateOf<RegisteredOwner?>(null) }

    LaunchedEffect(cameraUiState.isCameraActive) {
        if (cameraUiState.isCameraActive && !cameraUiState.isDetecting) {
            cameraViewModel.startDetection()
        }
    }

    LaunchedEffect(availableOutputModes) {
        viewModel.setAvailableOutputModes(availableOutputModes)
    }

    LaunchedEffect(uiState.isPreparing, uiState.isBroadcasting) {
        if (!uiState.isPreparing && !uiState.isBroadcasting) {
            hasStartedRecorder = false
            hasRequestedServerStart = false
        }
    }

    LaunchedEffect(
        uiState.isPreparing,
        hasStartedRecorder,
        cameraUiState.isRecording,
        viewModel.currentMuxerFactory,
        uiState.broadcastId,
    ) {
        val srtMuxerFactory = viewModel.currentMuxerFactory ?: return@LaunchedEffect
        if (!uiState.isPreparing || hasStartedRecorder || cameraUiState.isRecording) {
            return@LaunchedEffect
        }

        hasStartedRecorder = true
        cameraViewModel.startBroadcastRecording(
            width = BroadcastSrtInputProfile.WIDTH,
            height = BroadcastSrtInputProfile.HEIGHT,
            muxerFactory = srtMuxerFactory,
            metadataRepository = viewModel.createLiveMetadataRepository(),
            sessionId = uiState.broadcastId,
            encoderConfig = BroadcastSrtInputProfile.encoderConfig,
        )
    }

    LaunchedEffect(
        uiState.isPreparing,
        hasStartedRecorder,
        cameraUiState.isRecording,
    ) {
        if (!uiState.isPreparing || !hasStartedRecorder || cameraUiState.isRecording) {
            return@LaunchedEffect
        }

        delay(RECORDING_START_TIMEOUT_MS)
        if (uiState.isPreparing && hasStartedRecorder && !cameraUiState.isRecording) {
            viewModel.cancelPreparingBroadcast(message = "SRT 송출을 시작하지 못했습니다")
        }
    }

    LaunchedEffect(
        uiState.isPreparing,
        cameraUiState.isRecording,
        uiState.broadcastId,
    ) {
        if (!uiState.isPreparing || !cameraUiState.isRecording || hasRequestedServerStart) {
            return@LaunchedEffect
        }

        hasRequestedServerStart = true
        viewModel.markStreamingConnected()
        delay(BROADCAST_START_DELAY_MS)
        viewModel.confirmBroadcastStarted {
            cameraViewModel.stopRecording()
            viewModel.cancelPreparingBroadcast(clearError = false)
        }
    }

    LaunchedEffect(
        uiState.isPreparing,
        hasRequestedServerStart,
        uiState.isBroadcasting,
        uiState.broadcastId,
    ) {
        if (!uiState.isPreparing || !hasRequestedServerStart || uiState.isBroadcasting) {
            return@LaunchedEffect
        }

        delay(START_API_TIMEOUT_MS)
        if (uiState.isPreparing && hasRequestedServerStart && !uiState.isBroadcasting) {
            if (cameraUiState.isRecording) {
                cameraViewModel.stopRecording()
            }
            viewModel.cancelPreparingBroadcast(message = "방송 시작이 지연되고 있습니다")
        }
    }

    LaunchedEffect(uiState.isBroadcasting) {
        if (uiState.isBroadcasting && liveStartedAtMillis == null) {
            liveStartedAtMillis = System.currentTimeMillis()
        }
    }

    LaunchedEffect(cameraUiState.savedOriginalClipUri) {
        if (cameraUiState.savedOriginalClipUri == null) {
            return@LaunchedEffect
        }
        Toast.makeText(context, "원본 클립을 저장했습니다", Toast.LENGTH_SHORT).show()
        cameraViewModel.clearOriginalClipSaveMessage()
    }

    LaunchedEffect(cameraUiState.originalClipSaveError) {
        val message = cameraUiState.originalClipSaveError ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        cameraViewModel.clearOriginalClipSaveMessage()
    }

    LaunchedEffect(
        uiState.isPreparing,
        uiState.isBroadcasting,
        uiState.isStopping,
        uiState.error,
        uiState.completedReport,
    ) {
        if (uiState.completedReport == null) {
            return@LaunchedEffect
        }
        if (uiState.isPreparing || uiState.isBroadcasting || uiState.isStopping) {
            return@LaunchedEffect
        }
        if (uiState.error == null) {
            isMenuPresented = false
        }
        liveStartedAtMillis = null
    }

    val requestStopWithReport = {
        val startedAt = liveStartedAtMillis ?: System.currentTimeMillis()
        val reportSeed = CompletedBroadcastReportSeed(
            broadcastId = uiState.broadcastId.ifBlank { "unknown" },
            durationSec = ((System.currentTimeMillis() - startedAt) / 1000L).toInt(),
            ownerCount = cameraUiState.registeredOwnerThumbnails.size,
            recordingFilePath = null,
        )
        cameraViewModel.stopRecording()
        viewModel.stopBroadcasting(reportSeed)
    }

    val showBroadcastExitBlockedMessage = {
        Toast.makeText(context, "방송 중에는 종료할 수 없습니다", Toast.LENGTH_SHORT).show()
    }

    val closeCurrentSession = {
        if (cameraUiState.isRecording) {
            if (uiState.isBroadcasting) {
                requestStopWithReport()
            } else {
                cameraViewModel.stopRecording()
            }
        } else if (uiState.isPreparing) {
            viewModel.cancelPreparingBroadcast()
        } else if (uiState.isBroadcasting) {
            requestStopWithReport()
        }
    }

    BackHandler {
        when {
            pendingOwnerDeletion != null -> pendingOwnerDeletion = null
            isMenuPresented -> isMenuPresented = false
            uiState.completedReport != null && !uiState.isPreparing && !uiState.isBroadcasting && !uiState.isStopping -> {
                viewModel.dismissCompletedReport()
            }
            uiState.isStopping -> Unit
            uiState.isBroadcasting -> {
                showBroadcastExitBlockedMessage()
            }
            uiState.isPreparing || cameraUiState.isRecording -> {
                closeCurrentSession()
            }
            else -> onRootBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraScreen(
            onRecordingComplete = {},
            modifier = Modifier.fillMaxSize(),
            onBack = {
                when {
                    uiState.isBroadcasting -> showBroadcastExitBlockedMessage()
                    uiState.isPreparing || cameraUiState.isRecording -> closeCurrentSession()
                    else -> onRootBack()
                }
            },
            showDetectionControl = false,
            showRecordingControl = false,
            showMenuButton = false,
            showStatusPanel = false,
            lockLandscapeOrientation = true,
            viewModel = cameraViewModel,
        )

        if (isMenuPresented) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable { isMenuPresented = false },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .focusSafeDrawingPadding(
                    sides = WindowInsetsSides.Top + WindowInsetsSides.Start,
                    start = 22.dp,
                    top = 18.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.isBroadcasting) {
                OverlayChip(
                    text = "LIVE",
                    containerColor = Color(0xFFD14343).copy(alpha = 0.88f),
                    contentColor = Color.White,
                )
            }
            OverlayChip(
                text = if (cameraUiState.isDetecting) "Camera Ready" else "Camera Loading",
                containerColor = Color.Black.copy(alpha = 0.24f),
                contentColor = Color.White,
            )
            OverlayChip(
                text = cameraUiState.privacyMode.displayTitle(),
                containerColor = Color.Black.copy(alpha = 0.24f),
                contentColor = Color.White,
            )
            if (cameraUiState.registeredOwnerThumbnails.isNotEmpty()) {
                OverlayChip(
                    text = "Owner ${cameraUiState.registeredOwnerThumbnails.size}",
                    containerColor = Color.Black.copy(alpha = 0.24f),
                    contentColor = Color.White,
                )
            }
        }

        MenuButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .focusSafeDrawingPadding(
                    sides = WindowInsetsSides.Top + WindowInsetsSides.End,
                    top = 22.dp,
                    end = 22.dp,
                ),
            onClick = { isMenuPresented = !isMenuPresented },
        )

        FloatingBroadcastAction(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .focusSafeDrawingPadding(
                    sides = WindowInsetsSides.Bottom + WindowInsetsSides.End,
                    end = 24.dp,
                    bottom = 26.dp,
                ),
            label = when {
                uiState.isStopping -> "방송 종료 중..."
                uiState.isBroadcasting -> "방송 종료하기"
                uiState.isPreparing -> "방송 시작 중..."
                else -> "방송 시작하기"
            },
            enabled = !uiState.isPreparing && !uiState.isStopping,
            onClick = {
                when {
                    uiState.isStopping -> Unit
                    uiState.isBroadcasting -> {
                        requestStopWithReport()
                    }
                    else -> viewModel.startBroadcasting()
                }
            },
        )

        val transientStatus = when {
            uiState.error != null -> uiState.error
            uiState.isStopping -> "방송을 정리하는 중입니다."
            uiState.isPreparing -> "방송을 생성하고 송출을 준비하는 중입니다."
            else -> null
        }
        if (transientStatus != null) {
            TransientOverlayChip(
                text = transientStatus,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .focusSafeDrawingPadding(
                        sides = WindowInsetsSides.Bottom,
                        bottom = 100.dp,
                    ),
            )
        }

        AnimatedVisibility(
            visible = isMenuPresented,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            BroadcastMenuPanel(
                lensFacing = cameraUiState.lensFacing,
                privacyMode = cameraUiState.privacyMode,
                registeredOwners = cameraUiState.registeredOwners,
                platformConnections = platformConnections,
                selectedOutputMode = uiState.selectedOutputMode,
                isPlatformActionInProgress = isPlatformActionInProgress,
                platformSelectionEnabled = !uiState.isPreparing && !uiState.isBroadcasting && !uiState.isStopping,
                canSaveOriginalClip = uiState.isBroadcasting &&
                    cameraUiState.isOriginalClipBuffering &&
                    cameraUiState.isRecording,
                isSavingOriginalClip = cameraUiState.isSavingOriginalClip,
                onDismiss = { isMenuPresented = false },
                onSelectLensFacing = { target ->
                    if (cameraUiState.lensFacing != target) {
                        cameraViewModel.switchLensFacing()
                    }
                },
                onSelectPrivacyMode = cameraViewModel::setPrivacyMode,
                onSelectOutputMode = viewModel::selectOutputMode,
                onConnectPlatform = onConnectPlatform,
                onRequestDeleteOwner = { pendingOwnerDeletion = it },
                onSaveOriginalClip = cameraViewModel::saveOriginalClip,
            )
        }

        if (uiState.completedReport != null && !uiState.isPreparing && !uiState.isBroadcasting && !uiState.isStopping) {
            PostBroadcastReportSheet(
                report = uiState.completedReport!!,
                onDismiss = viewModel::dismissCompletedReport,
            )
        }

        pendingOwnerDeletion?.let { owner ->
            AlertDialog(
                onDismissRequest = { pendingOwnerDeletion = null },
                title = {
                    Text(text = "Owner 삭제")
                },
                text = {
                    Text(text = "이 Owner를 삭제할까요?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            cameraViewModel.removeRegisteredOwner(owner)
                            pendingOwnerDeletion = null
                        },
                    ) {
                        Text(text = "삭제", color = FocusIosPalette.Danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingOwnerDeletion = null }) {
                        Text(text = "취소")
                    }
                },
            )
        }
    }
}

@Composable
private fun KeepImmersiveNavigationBars() {
    val view = LocalView.current
    val activity = view.context as? Activity

    DisposableEffect(view, activity) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousBehavior = controller.systemBarsBehavior
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())

            onDispose {
                controller.show(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }
}

@Composable
private fun MenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0369A1).copy(alpha = 0.78f),
        shadowElevation = 10.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "≡",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun OverlayChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FloatingBroadcastAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = when {
        label.contains("종료") -> listOf(FocusIosPalette.Danger, FocusIosPalette.DangerBright)
        label.contains("시작") -> listOf(FocusIosPalette.Secondary, Color(0xFF10B981))
        else -> listOf(FocusIosPalette.Primary, FocusIosPalette.PrimaryBright)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        shadowElevation = if (enabled) 12.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (enabled) colors else {
                            listOf(
                                Color(0xFF64748B),
                                Color(0xFF94A3B8),
                            )
                        },
                    ),
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 38.dp, vertical = 16.dp),
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BroadcastMenuPanel(
    lensFacing: LensFacing,
    privacyMode: PrivacyMode,
    registeredOwners: List<RegisteredOwner>,
    platformConnections: List<StreamingPlatformConnection>,
    selectedOutputMode: BroadcastOutputMode,
    isPlatformActionInProgress: Boolean,
    platformSelectionEnabled: Boolean,
    canSaveOriginalClip: Boolean,
    isSavingOriginalClip: Boolean,
    onDismiss: () -> Unit,
    onSelectLensFacing: (LensFacing) -> Unit,
    onSelectPrivacyMode: (PrivacyMode) -> Unit,
    onSelectOutputMode: (BroadcastOutputMode) -> Unit,
    onConnectPlatform: (BroadcastOutputMode) -> Unit,
    onRequestDeleteOwner: (RegisteredOwner) -> Unit,
    onSaveOriginalClip: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(340.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 18.dp,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 30.dp, start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FocusIosSecondaryButton(
                        text = "닫기",
                        onClick = onDismiss,
                        modifier = Modifier.width(88.dp),
                    )
                }

                PanelSection(
                    title = "Owner 관리",
                    subtitle = "화면에 그대로 유지할 스트리머 프로필",
                ) {
                    if (registeredOwners.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(
                                items = registeredOwners,
                                key = { "${it.ownerId}-${it.trackId}-${it.thumbnailPath}" },
                            ) { owner ->
                                OwnerThumbnailCard(
                                    path = owner.thumbnailPath,
                                    onClick = { onRequestDeleteOwner(owner) },
                                )
                            }
                        }
                    } else {
                        EmptyPanelHint(text = "등록된 Owner가 없습니다.")
                    }
                }

                PanelSection(
                    title = "카메라 전환",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LensToggleChip(
                            text = "전면 카메라",
                            selected = lensFacing == LensFacing.FRONT,
                            onClick = { onSelectLensFacing(LensFacing.FRONT) },
                        )
                        LensToggleChip(
                            text = "후면 카메라",
                            selected = lensFacing == LensFacing.BACK,
                            onClick = { onSelectLensFacing(LensFacing.BACK) },
                        )
                    }
                }

                PanelSection(
                    title = "개인정보 처리",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrivacyToggleChip(
                            text = PrivacyMode.Avatar.displayTitle(),
                            selected = privacyMode == PrivacyMode.Avatar,
                            onClick = { onSelectPrivacyMode(PrivacyMode.Avatar) },
                        )
                        PrivacyToggleChip(
                            text = PrivacyMode.Mosaic.displayTitle(),
                            selected = privacyMode == PrivacyMode.Mosaic,
                            onClick = { onSelectPrivacyMode(PrivacyMode.Mosaic) },
                        )
                        PrivacyToggleChip(
                            text = PrivacyMode.Original.displayTitle(),
                            selected = privacyMode == PrivacyMode.Original,
                            onClick = { onSelectPrivacyMode(PrivacyMode.Original) },
                        )
                    }
                }

                PlatformIconRow(
                    platformConnections = platformConnections,
                    selectedOutputMode = selectedOutputMode,
                    platformSelectionEnabled = platformSelectionEnabled,
                    isPlatformActionInProgress = isPlatformActionInProgress,
                    onSelectOutputMode = onSelectOutputMode,
                    onConnectPlatform = onConnectPlatform,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SaveClipButton(
                enabled = canSaveOriginalClip && !isSavingOriginalClip,
                isSaving = isSavingOriginalClip,
                onClick = onSaveOriginalClip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun PrivacyMode.displayTitle(): String = when (this) {
    PrivacyMode.Avatar -> "아바타"
    PrivacyMode.Mosaic -> "블러"
    PrivacyMode.Original -> "비활성화"
}

@Composable
private fun PanelSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black.copy(alpha = 0.9f),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.58f),
                )
            }
        }
        content()
    }
}

@Composable
private fun OwnerThumbnailCard(
    path: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .size(width = 116.dp, height = 132.dp)
                .background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = "Owner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.44f))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "삭제",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun LensToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF0369A1) else Color.Black.copy(alpha = 0.05f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = if (selected) Color.White else Color.Black.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PrivacyToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF10B981) else Color.Black.copy(alpha = 0.05f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = if (selected) Color.White else Color.Black.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SaveClipButton(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled || isSaving) FocusIosPalette.Danger else Color(0xFF9CA3AF),
        shadowElevation = if (enabled) 8.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isSaving) "저장 중..." else "클립 저장",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlatformIconRow(
    platformConnections: List<StreamingPlatformConnection>,
    selectedOutputMode: BroadcastOutputMode,
    platformSelectionEnabled: Boolean,
    isPlatformActionInProgress: Boolean,
    onSelectOutputMode: (BroadcastOutputMode) -> Unit,
    onConnectPlatform: (BroadcastOutputMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
    ) {
        platformConnections.forEach { connection ->
            PlatformLogoButton(
                connection = connection,
                selected = connection.connected && connection.outputMode == selectedOutputMode,
                enabled = !isPlatformActionInProgress && platformSelectionEnabled,
                onClick = {
                    if (connection.connected) {
                        onSelectOutputMode(connection.outputMode)
                    } else {
                        onConnectPlatform(connection.outputMode)
                    }
                },
            )
        }
    }
}

@Composable
private fun PlatformLogoButton(
    connection: StreamingPlatformConnection,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.04f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = when {
                selected -> FocusIosPalette.Primary
                connection.connected -> Color.Black.copy(alpha = 0.10f)
                else -> Color.Black.copy(alpha = 0.08f)
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (connection.outputMode) {
                BroadcastOutputMode.CHZZK_RTMP -> ChzzkPlatformLogo(connected = connection.connected)
                BroadcastOutputMode.YOUTUBE_RTMP -> YoutubePlatformLogo(connected = connection.connected)
            }
        }
    }
}

@Composable
private fun ChzzkPlatformLogo(
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (connected) Color(0xFF03C75A) else Color(0xFFD1D5DB)
    val symbol = if (connected) Color.White else Color(0xFF6B7280)
    Canvas(modifier = modifier.size(36.dp)) {
        drawRoundRect(
            color = background,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.24f),
        )
        val mark = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.23f)
            lineTo(size.width * 0.62f, size.height * 0.23f)
            lineTo(size.width * 0.50f, size.height * 0.44f)
            lineTo(size.width * 0.72f, size.height * 0.44f)
            lineTo(size.width * 0.40f, size.height * 0.78f)
            lineTo(size.width * 0.50f, size.height * 0.56f)
            lineTo(size.width * 0.28f, size.height * 0.56f)
            close()
        }
        drawPath(path = mark, color = symbol)
    }
}

@Composable
private fun YoutubePlatformLogo(
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (connected) Color(0xFFFF0033) else Color(0xFFD1D5DB)
    val symbol = if (connected) Color.White else Color(0xFF6B7280)
    Canvas(modifier = modifier.size(width = 44.dp, height = 32.dp)) {
        drawRoundRect(
            color = background,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.34f),
        )
        val play = Path().apply {
            moveTo(size.width * 0.42f, size.height * 0.28f)
            lineTo(size.width * 0.42f, size.height * 0.72f)
            lineTo(size.width * 0.72f, size.height * 0.50f)
            close()
        }
        drawPath(path = play, color = symbol)
    }
}

@Composable
private fun EmptyPanelHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Black.copy(alpha = 0.58f),
    )
}

@Composable
private fun TransientOverlayChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.34f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
