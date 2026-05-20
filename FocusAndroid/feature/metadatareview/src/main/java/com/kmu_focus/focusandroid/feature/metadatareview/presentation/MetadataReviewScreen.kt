package com.kmu_focus.focusandroid.feature.metadatareview.presentation

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosGradientBackground
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPanelHeader
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSectionCard
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSecondaryButton
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FaceData
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MetadataReviewScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MetadataReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val exoPlayer = rememberMetadataReviewPlayer()
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::onVideoSelected)
    }

    val metadataPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::onMetadataSelected)
    }

    LaunchedEffect(uiState.errorMessage) {
        val errorMessage = uiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        viewModel.consumeErrorMessage()
    }

    LaunchedEffect(uiState.videoUri) {
        isPlaying = false
        videoWidth = 0
        videoHeight = 0

        val videoUri = uiState.videoUri ?: run {
            exoPlayer.pause()
            exoPlayer.clearMediaItems()
            viewModel.onPlaybackPositionChanged(0L)
            return@LaunchedEffect
        }

        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
        exoPlayer.prepare()
        exoPlayer.pause()
        exoPlayer.seekTo(0L)
        viewModel.onPlaybackPositionChanged(0L)
    }

    LaunchedEffect(isPlaying, uiState.videoUri) {
        if (uiState.videoUri == null) {
            exoPlayer.pause()
            return@LaunchedEffect
        }

        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, uiState.videoUri) {
        if (!isPlaying || uiState.videoUri == null) return@LaunchedEffect

        while (true) {
            viewModel.onPlaybackPositionChanged(exoPlayer.currentPosition)
            delay(33L)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    viewModel.onPlaybackPositionChanged(exoPlayer.currentPosition)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }

        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        16f / 9f
    }
    val parsedMetadata = uiState.parsedMetadata
    val overlayWidth = when {
        videoWidth > 0 -> videoWidth
        parsedMetadata?.coordinateWidth?.let { it > 0 } == true -> parsedMetadata.coordinateWidth
        else -> 1
    }
    val overlayHeight = when {
        videoHeight > 0 -> videoHeight
        parsedMetadata?.coordinateHeight?.let { it > 0 } == true -> parsedMetadata.coordinateHeight
        else -> 1
    }

    FocusIosGradientBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        FocusIosSectionCard(modifier = Modifier.fillMaxWidth()) {
            FocusIosPanelHeader(
                title = "BBox 리뷰",
                subtitle = "영상과 메타데이터 JSON을 동시에 불러와 프레임 단위로 검수합니다.",
            )
            FocusIosSecondaryButton(
                text = "라이브 홈으로 돌아가기",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 4.dp,
            color = FocusIosPalette.Surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "BBox 리뷰",
                    style = MaterialTheme.typography.headlineSmall,
                    color = FocusIosPalette.Text,
                )
                Text(
                    text = "동영상과 메타데이터 JSON을 선택하면 재생 위치 기준으로 바운딩 박스를 겹쳐서 확인할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusIosPalette.TextMuted,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("동영상 선택")
                    }
                    OutlinedButton(
                        onClick = {
                            metadataPickerLauncher.launch(arrayOf("application/json", "text/*"))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("JSON 선택")
                    }
                }

                DocumentStatusRow(
                    label = "동영상",
                    value = uiState.videoName ?: "아직 선택되지 않았습니다.",
                )
                DocumentStatusRow(
                    label = "메타데이터",
                    value = uiState.metadataName ?: "아직 선택되지 않았습니다.",
                )

                if (uiState.isMetadataLoading) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "메타데이터를 읽는 중입니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (parsedMetadata != null) {
                    Text(
                        text = buildString {
                            append("프레임 ")
                            append(uiState.frameCount)
                            append("개 / 얼굴 ")
                            append(uiState.totalFaceCount)
                            append("개")
                            append(" / 얼굴 포함 프레임 ")
                            append(parsedMetadata.framesWithFacesCount)
                            append("개")
                            append(" / 좌표 ")
                            append(parsedMetadata.coordinateWidth)
                            append("x")
                            append(parsedMetadata.coordinateHeight)
                            parsedMetadata.fps?.let { fps ->
                                append(" / ")
                                append(String.format(Locale.US, "%.2f fps", fps))
                            }
                            if (parsedMetadata.isPlaybackTimelineNormalized) {
                                append(" / 시간축 보정")
                            }
                            parsedMetadata.sessionId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { sessionId ->
                                    append(" / session=")
                                    append(sessionId)
                                }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "미리보기",
                    style = MaterialTheme.typography.titleMedium,
                )

                if (uiState.videoUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(20.dp),
                            )
                            .padding(vertical = 40.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "동영상을 먼저 선택하세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black),
                    ) {
                        VideoPlayerSurface(
                            exoPlayer = exoPlayer,
                            modifier = Modifier.fillMaxSize(),
                        )
                        BoundingBoxOverlay(
                            faces = uiState.currentFrame?.faces.orEmpty(),
                            frameWidth = overlayWidth,
                            frameHeight = overlayHeight,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isPlaying) "일시정지" else "재생")
                        }
                        OutlinedButton(
                            onClick = {
                                isPlaying = false
                                exoPlayer.seekTo(0L)
                                viewModel.onPlaybackPositionChanged(0L)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("처음으로")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val nextPosition = (exoPlayer.currentPosition - SEEK_STEP_MS)
                                    .coerceAtLeast(0L)
                                exoPlayer.seekTo(nextPosition)
                                viewModel.onPlaybackPositionChanged(nextPosition)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("-5초")
                        }
                        OutlinedButton(
                            onClick = {
                                val maxPosition = exoPlayer.duration
                                    .takeIf { it > 0L }
                                    ?: Long.MAX_VALUE
                                val nextPosition = (exoPlayer.currentPosition + SEEK_STEP_MS)
                                    .coerceAtMost(maxPosition)
                                exoPlayer.seekTo(nextPosition)
                                viewModel.onPlaybackPositionChanged(nextPosition)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("+5초")
                        }
                    }

                    Text(
                        text = buildString {
                            append(formatDuration(uiState.playbackPositionMs))
                            append(" / 얼굴 ")
                            append(uiState.currentFaceCount)
                            append("명")
                            uiState.currentFrame?.let { frame ->
                                append(" / pts ")
                                append(frame.ptsUs / 1_000L)
                                append("ms")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (uiState.parsedMetadata == null && !uiState.isMetadataLoading) {
                        Text(
                            text = "JSON을 선택하면 현재 재생 위치에 대응하는 바운딩 박스가 화면 위에 표시됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun rememberMetadataReviewPlayer(): ExoPlayer {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    return exoPlayer
}

@Composable
private fun VideoPlayerSurface(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = modifier.background(Color.Black),
    )
}

@Composable
private fun DocumentStatusRow(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoundingBoxOverlay(
    faces: List<FaceData>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (frameWidth <= 0 || frameHeight <= 0) return@Canvas

        val scaleX = size.width / frameWidth
        val scaleY = size.height / frameHeight
        val strokeWidth = 3.dp.toPx()

        faces.forEach { face ->
            val color = OVERLAY_COLORS[(face.trackingId and Int.MAX_VALUE) % OVERLAY_COLORS.size]
            val left = face.bbox.x * scaleX
            val top = face.bbox.y * scaleY
            val width = face.bbox.width * scaleX
            val height = face.bbox.height * scaleY

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = strokeWidth),
            )

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = 30f
                    isAntiAlias = true
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }

                canvas.nativeCanvas.drawText(
                    "ID ${face.trackingId}",
                    left,
                    (top - 10f).coerceAtLeast(paint.textSize),
                    paint,
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val totalSeconds = safeDurationMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val millis = safeDurationMs % 1_000L

    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
}

private val OVERLAY_COLORS = listOf(
    Color(0xFFFF7043),
    Color(0xFF29B6F6),
    Color(0xFF66BB6A),
    Color(0xFFFFCA28),
)

private const val SEEK_STEP_MS = 5_000L
