package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import com.kmu_focus.focusandroid.feature.broadcast.BuildConfig
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastOutputMode
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.BroadcastStreamingUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.CompleteBroadcastAnalysisJobUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.CreateBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.CreateBroadcastAnalysisJobUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.DeleteBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.GetBroadcastHighlightsUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.GetLatestBroadcastAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val BROADCAST_ID_KEY = "broadcastId"
private const val STREAM_KEY_KEY = "streamKey"
private const val HLS_URL_KEY = "hlsUrl"

data class BroadcastCameraUiState(
    val broadcastId: String = "",
    val streamKey: String = "",
    val hlsUrl: String = "",
    val srtState: SrtConnectionState = SrtConnectionState.DISCONNECTED,
    val isPreparing: Boolean = false,
    val isBroadcasting: Boolean = false,
    val isStopping: Boolean = false,
    val error: String? = null,
    val completedReport: CompletedBroadcastReport? = null,
    val availableOutputModes: List<BroadcastOutputMode> = listOf(BroadcastOutputMode.CHZZK_RTMP),
    val selectedOutputMode: BroadcastOutputMode = BroadcastOutputMode.CHZZK_RTMP,
)

@HiltViewModel
class BroadcastCameraViewModel @Inject constructor(
    private val createBroadcastUseCase: CreateBroadcastUseCase,
    private val deleteBroadcastUseCase: DeleteBroadcastUseCase,
    private val broadcastStreamingUseCase: BroadcastStreamingUseCase,
    private val createBroadcastAnalysisJobUseCase: CreateBroadcastAnalysisJobUseCase,
    private val completeBroadcastAnalysisJobUseCase: CompleteBroadcastAnalysisJobUseCase,
    private val getLatestBroadcastAnalysisUseCase: GetLatestBroadcastAnalysisUseCase,
    private val getBroadcastHighlightsUseCase: GetBroadcastHighlightsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BroadcastCameraUiState(
            broadcastId = savedStateHandle[BROADCAST_ID_KEY] ?: "",
            streamKey = savedStateHandle[STREAM_KEY_KEY] ?: "",
            hlsUrl = savedStateHandle[HLS_URL_KEY] ?: "",
        ),
    )
    val uiState: StateFlow<BroadcastCameraUiState> = _uiState.asStateFlow()

    private var heartbeatJob: Job? = null
    private var startBroadcastJob: Job? = null

    val currentMuxerFactory: RealTimeRecorder.VideoMuxerFactory?
        get() = broadcastStreamingUseCase.currentMuxerFactory

    fun setAvailableOutputModes(outputModes: List<BroadcastOutputMode>) {
        val resolvedModes = outputModes.distinct().ifEmpty {
            listOf(BroadcastOutputMode.CHZZK_RTMP)
        }
        _uiState.update { current ->
            current.copy(
                availableOutputModes = resolvedModes,
                selectedOutputMode = current.selectedOutputMode.takeIf { it in resolvedModes }
                    ?: resolvedModes.first(),
            )
        }
    }

    fun selectOutputMode(outputMode: BroadcastOutputMode) {
        _uiState.update { current ->
            if (outputMode !in current.availableOutputModes) {
                current
            } else {
                current.copy(selectedOutputMode = outputMode)
            }
        }
    }

    fun updateSession(
        broadcastId: String,
        streamKey: String,
        hlsUrl: String = uiState.value.hlsUrl,
    ) {
        savedStateHandle[BROADCAST_ID_KEY] = broadcastId
        savedStateHandle[STREAM_KEY_KEY] = streamKey
        savedStateHandle[HLS_URL_KEY] = hlsUrl
        _uiState.update { current ->
            current.copy(
                broadcastId = broadcastId,
                streamKey = streamKey,
                hlsUrl = hlsUrl,
            )
        }
    }

    fun prepareBroadcasting() {
        startBroadcasting()
    }

    fun startBroadcasting() {
        val currentState = uiState.value
        if (currentState.isBroadcasting || currentState.isPreparing || currentState.isStopping) {
            return
        }

        _uiState.update { current ->
            current.copy(
                error = null,
                isPreparing = true,
                isStopping = false,
                srtState = SrtConnectionState.CONNECTING,
                completedReport = null,
            )
        }

        startBroadcastJob?.cancel()
        startBroadcastJob = viewModelScope.launch {
            createBroadcastUseCase(
                title = buildAutoBroadcastTitle(),
                outputMode = currentState.selectedOutputMode,
            )
                .onSuccess { broadcast ->
                    updateSession(
                        broadcastId = broadcast.broadcastId,
                        streamKey = broadcast.streamKey,
                        hlsUrl = broadcast.hlsUrl.orEmpty(),
                    )

                    broadcastStreamingUseCase.prepareBroadcastStreaming(
                        streamKey = broadcast.streamKey,
                        mediaMtxHost = BuildConfig.MEDIA_MTX_HOST,
                        mediaMtxPort = BuildConfig.MEDIA_MTX_PORT,
                    ).onFailure { throwable ->
                        val message = mergeErrorMessages(
                            primary = throwable.message ?: "송출 준비 실패",
                            secondary = deleteBroadcastUseCase(broadcast.broadcastId)
                                .exceptionOrNull()
                                ?.message,
                        )
                        clearSession(
                            error = message,
                            srtState = SrtConnectionState.ERROR,
                        )
                    }
                }
                .onFailure { throwable ->
                    clearSession(
                        error = throwable.message ?: "방송 생성 실패",
                        srtState = SrtConnectionState.ERROR,
                    )
                }
            startBroadcastJob = null
        }
    }

    fun markStreamingConnected() {
        _uiState.update { current ->
            if (!current.isPreparing && !current.isBroadcasting) {
                current
            } else {
                current.copy(srtState = SrtConnectionState.CONNECTED)
            }
        }
    }

    fun confirmBroadcastStarted(onFailure: () -> Unit = {}) {
        val currentState = uiState.value
        if (!currentState.isPreparing || currentState.isBroadcasting || currentState.broadcastId.isBlank()) {
            return
        }

        viewModelScope.launch {
            broadcastStreamingUseCase.confirmBroadcastStarted(
                broadcastId = currentState.broadcastId,
            ).onSuccess { broadcast ->
                heartbeatJob?.cancel()
                heartbeatJob = broadcastStreamingUseCase.startHeartbeat(
                    broadcastId = currentState.broadcastId,
                    scope = viewModelScope,
                )
                savedStateHandle[HLS_URL_KEY] = broadcast.hlsUrl.orEmpty()
                _uiState.update { state ->
                    state.copy(
                        hlsUrl = broadcast.hlsUrl.orEmpty(),
                        isPreparing = false,
                        isBroadcasting = true,
                        isStopping = false,
                        srtState = SrtConnectionState.CONNECTED,
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isPreparing = false,
                        isBroadcasting = false,
                        isStopping = false,
                        srtState = SrtConnectionState.ERROR,
                        error = throwable.message ?: "방송 시작 실패",
                    )
                }
                onFailure()
            }
        }
    }

    fun cancelPreparingBroadcast(
        clearError: Boolean = true,
        message: String? = null,
    ) {
        val currentState = uiState.value
        if (currentState.isStopping) {
            return
        }

        startBroadcastJob?.cancel()
        startBroadcastJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null

        val broadcastId = currentState.broadcastId
        val preservedError = message ?: if (clearError) null else currentState.error
        if (broadcastId.isBlank()) {
            clearSession(error = preservedError)
            return
        }

        _uiState.update { current ->
            current.copy(
                isPreparing = false,
                isBroadcasting = false,
                isStopping = true,
                srtState = SrtConnectionState.DISCONNECTED,
                error = null,
            )
        }

        viewModelScope.launch {
            val abortFailure = runCatching {
                broadcastStreamingUseCase.stopPreparedStreaming()
            }.exceptionOrNull()
            val deleteFailure = deleteBroadcastUseCase(broadcastId).exceptionOrNull()
            clearSession(
                error = mergeErrorMessages(
                    primary = abortFailure?.message ?: preservedError,
                    secondary = deleteFailure?.message,
                ),
                srtState = if (abortFailure != null || deleteFailure != null) {
                    SrtConnectionState.ERROR
                } else {
                    SrtConnectionState.DISCONNECTED
                },
            )
        }
    }

    fun stopBroadcasting() {
        stopBroadcasting(
            reportSeed = CompletedBroadcastReportSeed(
                broadcastId = uiState.value.broadcastId,
                durationSec = 1,
                ownerCount = 0,
                recordingFilePath = null,
            ),
        )
    }

    fun stopBroadcasting(reportSeed: CompletedBroadcastReportSeed) {
        val currentState = uiState.value
        if (currentState.isStopping) {
            return
        }
        if (currentState.isPreparing && !currentState.isBroadcasting) {
            cancelPreparingBroadcast()
            return
        }
        if (!currentState.isBroadcasting || currentState.broadcastId.isBlank()) {
            return
        }

        startBroadcastJob?.cancel()
        startBroadcastJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null

        val broadcastId = currentState.broadcastId
        _uiState.update { current ->
            current.copy(
                isPreparing = false,
                isBroadcasting = false,
                isStopping = true,
                error = null,
                srtState = SrtConnectionState.DISCONNECTED,
                completedReport = null,
            )
        }

        viewModelScope.launch {
            val stopFailure = broadcastStreamingUseCase.stopBroadcast(broadcastId).exceptionOrNull()
            val completedReport = if (stopFailure == null) {
                buildCompletedReport(
                    broadcastId = broadcastId,
                    seed = reportSeed.copy(broadcastId = broadcastId),
                )
            } else {
                null
            }
            val deleteFailure = deleteBroadcastUseCase(broadcastId).exceptionOrNull()
            clearSession(
                error = mergeErrorMessages(
                    primary = stopFailure?.message,
                    secondary = deleteFailure?.message,
                ),
                srtState = if (stopFailure != null || deleteFailure != null) {
                    SrtConnectionState.ERROR
                } else {
                    SrtConnectionState.DISCONNECTED
                },
                completedReport = completedReport,
            )
        }
    }

    fun dismissCompletedReport() {
        _uiState.update { current ->
            current.copy(completedReport = null)
        }
    }

    private fun clearSession(
        error: String? = null,
        srtState: SrtConnectionState = SrtConnectionState.DISCONNECTED,
        completedReport: CompletedBroadcastReport? = null,
    ) {
        val preservedModes = uiState.value.availableOutputModes
        val preservedSelection = uiState.value.selectedOutputMode
        savedStateHandle[BROADCAST_ID_KEY] = ""
        savedStateHandle[STREAM_KEY_KEY] = ""
        savedStateHandle[HLS_URL_KEY] = ""
        _uiState.update {
            BroadcastCameraUiState(
                srtState = srtState,
                error = error,
                completedReport = completedReport,
                availableOutputModes = preservedModes,
                selectedOutputMode = preservedSelection.takeIf { it in preservedModes }
                    ?: preservedModes.firstOrNull()
                    ?: BroadcastOutputMode.CHZZK_RTMP,
            )
        }
    }

    private fun buildAutoBroadcastTitle(): String {
        return "포커스방송"
    }

    private fun mergeErrorMessages(
        primary: String?,
        secondary: String?,
    ): String? {
        return when {
            primary.isNullOrBlank() && secondary.isNullOrBlank() -> null
            primary.isNullOrBlank() -> secondary
            secondary.isNullOrBlank() -> primary
            else -> "$primary / 정리 실패: $secondary"
        }
    }

    private suspend fun buildCompletedReport(
        broadcastId: String,
        seed: CompletedBroadcastReportSeed,
    ): CompletedBroadcastReport {
        val localReport = buildLocalCompletedBroadcastReport(
            seed = seed,
            analysisStatus = com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus.PROCESSING,
        )

        val createdJob = createBroadcastAnalysisJobUseCase(
            broadcastId = broadcastId,
            request = localReport.toCreateAnalysisJobRequest(seed),
        ).getOrElse {
            return localReport.copy(
                analysisStatus = com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus.FAILED,
            )
        }

        val seededReport = localReport.copy(
            analysisJobId = createdJob.analysisJobId,
            analysisStatus = createdJob.jobStatus,
        )

        val completedJob = completeBroadcastAnalysisJobUseCase(
            broadcastId = broadcastId,
            analysisJobId = createdJob.analysisJobId,
            request = seededReport.toCompleteAnalysisJobRequest(seed),
        ).getOrNull()

        val latestAnalysis = getLatestBroadcastAnalysisUseCase(broadcastId).getOrNull()
        val highlightMoments = getBroadcastHighlightsUseCase(broadcastId)
            .getOrDefault(emptyList())
            .map { it.toCompletedHighlightMoment() }

        if (latestAnalysis != null) {
            return latestAnalysis.toCompletedBroadcastReport(seed, highlightMoments)
        }

        return seededReport.copy(
            analysisStatus = completedJob?.jobStatus ?: com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus.FAILED,
            completedAtMillis = completedJob?.completedAt?.let(::parseAnalysisEpochMillis)
                ?: seed.completedAtMillis,
            highlightCount = maxOf(seededReport.highlightCount, highlightMoments.size),
            highlightMoments = highlightMoments,
        )
    }

    private fun parseAnalysisEpochMillis(raw: String): Long? {
        return runCatching {
            java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        }.recoverCatching {
            java.time.LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    override fun onCleared() {
        startBroadcastJob?.cancel()
        heartbeatJob?.cancel()
        super.onCleared()
    }
}
