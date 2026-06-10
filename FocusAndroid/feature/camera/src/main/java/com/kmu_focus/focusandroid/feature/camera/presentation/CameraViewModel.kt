package com.kmu_focus.focusandroid.feature.camera.presentation

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.camera.domain.entity.LensFacing
import com.kmu_focus.focusandroid.feature.camera.domain.usecase.CameraAnalysisUseCase
import com.kmu_focus.focusandroid.feature.camera.domain.usecase.CameraRecordingUseCase
import com.kmu_focus.focusandroid.core.media.di.IoDispatcher
import com.kmu_focus.focusandroid.core.media.domain.entity.EncoderConfig
import com.kmu_focus.focusandroid.core.media.api.recorder.VideoMuxerFactory
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.core.media.domain.entity.PrivacyMode
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import com.kmu_focus.focusandroid.feature.camera.domain.entity.RegisteredOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class CameraUiState(
    val isCameraActive: Boolean = false,
    val isDetecting: Boolean = false,
    val isRecording: Boolean = false,
    val lensFacing: LensFacing = LensFacing.BACK,
    val privacyMode: PrivacyMode = PrivacyMode.Avatar,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val faceLabels: List<Boolean?> = emptyList(),
    val trackingIds: List<Int> = emptyList(),
    val previewWidth: Int = 0,
    val previewHeight: Int = 0,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val recordingFile: File? = null,
    val isOriginalClipBuffering: Boolean = false,
    val isSavingOriginalClip: Boolean = false,
    val savedOriginalClipUri: String? = null,
    val originalClipSaveError: String? = null,
    val registeredOwners: List<RegisteredOwner> = emptyList(),
) {
    val registeredOwnerThumbnails: List<String>
        get() = registeredOwners.map { it.thumbnailPath }
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraAnalysisUseCase: CameraAnalysisUseCase,
    private val cameraRecordingUseCase: CameraRecordingUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    @Volatile
    private var encoderSurfaceDispatcher: ((Surface?, Int, Int) -> Unit)? = null

    @Volatile
    private var originalClipSurfaceDispatcher: ((Surface?, Int, Int) -> Unit)? = null

    @Volatile
    private var currentEncoderSurface: Surface? = null

    @Volatile
    private var currentEncoderWidth: Int = 0

    @Volatile
    private var currentEncoderHeight: Int = 0

    @Volatile
    private var currentRecordingFile: File? = null

    @Volatile
    private var currentOriginalClipSurface: Surface? = null

    @Volatile
    private var currentOriginalClipWidth: Int = 0

    @Volatile
    private var currentOriginalClipHeight: Int = 0

    private val manualOwnerTrackIds = linkedSetOf<Int>()

    @Volatile
    private var pendingOwnerRegistrationTrackId: Int? = null

    fun setPrivacyMode(mode: PrivacyMode) {
        if (_uiState.value.privacyMode == mode) return
        cameraAnalysisUseCase.setPrivacyMode(mode)
        _uiState.value = _uiState.value.copy(privacyMode = mode)
    }

    fun setEncoderSurfaceDispatcher(dispatcher: ((Surface?, Int, Int) -> Unit)?) {
        encoderSurfaceDispatcher = dispatcher
        dispatcher?.invoke(currentEncoderSurface, currentEncoderWidth, currentEncoderHeight)
    }

    fun setOriginalClipSurfaceDispatcher(dispatcher: ((Surface?, Int, Int) -> Unit)?) {
        originalClipSurfaceDispatcher = dispatcher
        dispatcher?.invoke(currentOriginalClipSurface, currentOriginalClipWidth, currentOriginalClipHeight)
    }

    fun startCamera() {
        if (_uiState.value.isCameraActive) return
        _uiState.value = _uiState.value.copy(isCameraActive = true)
    }

    fun updatePreviewResolution(
        width: Int,
        height: Int,
    ) {
        if (width <= 0 || height <= 0) return
        cameraAnalysisUseCase.updateSourceFrameSize(width, height)
        val currentState = _uiState.value
        if (currentState.previewWidth == width && currentState.previewHeight == height) {
            return
        }
        _uiState.value = currentState.copy(
            previewWidth = width,
            previewHeight = height,
        )
    }

    fun stopCamera() {
        stopRecordingInternal(saveRecordingFile = false)
        cameraAnalysisUseCase.updateSourceFrameSize(0, 0)
        manualOwnerTrackIds.clear()
        pendingOwnerRegistrationTrackId = null
        _uiState.value = _uiState.value.copy(
            isCameraActive = false,
            isDetecting = false,
            isRecording = false,
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList(),
            previewWidth = 0,
            previewHeight = 0,
            registeredOwners = emptyList(),
            isOriginalClipBuffering = false,
            isSavingOriginalClip = false,
            savedOriginalClipUri = null,
            originalClipSaveError = null,
        )
        cameraAnalysisUseCase.clearProcessingThreadCache()
    }

    fun startDetection() {
        if (!_uiState.value.isCameraActive) return
        if (_uiState.value.isDetecting) return
        _uiState.value = _uiState.value.copy(isDetecting = true)
    }

    fun stopDetection() {
        stopRecordingInternal(saveRecordingFile = false)
        cameraAnalysisUseCase.updateSourceFrameSize(0, 0)
        manualOwnerTrackIds.clear()
        pendingOwnerRegistrationTrackId = null
        _uiState.value = _uiState.value.copy(
            isDetecting = false,
            isRecording = false,
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList(),
            previewWidth = 0,
            previewHeight = 0,
            registeredOwners = emptyList(),
            isOriginalClipBuffering = false,
            isSavingOriginalClip = false,
        )
    }

    fun processFrameSync(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        frameTimestampNs: Long = android.os.SystemClock.elapsedRealtimeNanos(),
    ): ProcessedFrame? {
        val currentState = _uiState.value
        if (!currentState.isCameraActive || !currentState.isDetecting) return null

        val result = cameraAnalysisUseCase.processFrame(
            rgbaBuffer = buffer,
            width = width,
            height = height,
            timestampMs = frameTimestampNs / 1_000_000L,
            timestampUs = frameTimestampNs / 1_000L,
        )

        val pendingTrackId = pendingOwnerRegistrationTrackId
        if (pendingTrackId != null && result.trackingIds.contains(pendingTrackId)) {
            pendingOwnerRegistrationTrackId = null
            val regResult = cameraAnalysisUseCase.registerOwnerFromFrame(
                rgbaBuffer = buffer,
                width = width,
                height = height,
                trackId = pendingTrackId,
                processedFrame = result,
            )
            if (regResult.success) {
                manualOwnerTrackIds.add(pendingTrackId)
                val ownerId = regResult.ownerId
                val thumbnailPath = regResult.thumbnailPath
                if (ownerId != null && thumbnailPath != null) {
                    _uiState.value = _uiState.value.copy(
                        registeredOwners = _uiState.value.registeredOwners + RegisteredOwner(
                            ownerId = ownerId,
                            trackId = pendingTrackId,
                            thumbnailPath = thumbnailPath,
                        ),
                    )
                }
            }
        }

        val mergedLabels = result.faces.indices.map { index ->
            val trackId = result.trackingIds.getOrNull(index) ?: index
            if (trackId in manualOwnerTrackIds) {
                true
            } else {
                result.faceLabels.getOrNull(index)
            }
        }
        _uiState.value = _uiState.value.copy(
            detectedFaces = result.faces,
            faceLabels = mergedLabels,
            trackingIds = result.trackingIds,
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight,
        )
        return result.copy(faceLabels = mergedLabels)
    }

    fun startRecording(width: Int, height: Int) {
        val currentState = _uiState.value
        if (!currentState.isCameraActive || !currentState.isDetecting || currentState.isRecording) return

        viewModelScope.launch(ioDispatcher) {
            cameraAnalysisUseCase.startMetadataSession()
            val startResult = cameraRecordingUseCase.startRecording(
                width = width,
                height = height,
                onSurfaceReady = { encoderSurface, targetWidth, targetHeight ->
                    currentEncoderSurface = encoderSurface.surface
                    currentEncoderWidth = targetWidth
                    currentEncoderHeight = targetHeight
                    encoderSurfaceDispatcher?.invoke(
                        currentEncoderSurface,
                        currentEncoderWidth,
                        currentEncoderHeight,
                    )
                },
            )
            startResult.fold(
                onSuccess = { file ->
                    currentRecordingFile = file
                    _uiState.value = _uiState.value.copy(isRecording = true)
                },
                onFailure = {
                    currentRecordingFile = null
                    clearEncoderSurface()
                    _uiState.value = _uiState.value.copy(isRecording = false)
                },
            )
            if (startResult.isFailure) {
                cameraAnalysisUseCase.closeMetadataSession()
            }
        }
    }

    fun startBroadcastRecording(
        width: Int,
        height: Int,
        muxerFactory: VideoMuxerFactory,
        metadataRepository: MetadataRepository,
        sessionId: String,
        encoderConfig: EncoderConfig? = null,
    ) {
        val currentState = _uiState.value
        if (!currentState.isCameraActive || !currentState.isDetecting || currentState.isRecording) return

        viewModelScope.launch(ioDispatcher) {
            cameraAnalysisUseCase.setBroadcastSourceOverride(width = width, height = height)
            cameraAnalysisUseCase.startMetadataSession(
                repository = metadataRepository,
                sessionId = sessionId,
            )
            val startResult = cameraRecordingUseCase.startBroadcastRecording(
                width = width,
                height = height,
                muxerFactory = muxerFactory,
                encoderConfig = encoderConfig,
                onSurfaceReady = { encoderSurface, targetWidth, targetHeight ->
                    currentEncoderSurface = encoderSurface.surface
                    currentEncoderWidth = targetWidth
                    currentEncoderHeight = targetHeight
                    encoderSurfaceDispatcher?.invoke(
                        currentEncoderSurface,
                        currentEncoderWidth,
                        currentEncoderHeight,
                    )
                },
            )
            startResult.fold(
                onSuccess = {
                    currentRecordingFile = null
                    val clipBufferStarted = startOriginalClipBufferInternal(
                        width = width,
                        height = height,
                        encoderConfig = encoderConfig,
                    )
                    _uiState.value = _uiState.value.copy(
                        isRecording = true,
                        isOriginalClipBuffering = clipBufferStarted,
                        recordingFile = null,
                        originalClipSaveError = null,
                    )
                },
                onFailure = {
                    currentRecordingFile = null
                    clearEncoderSurface()
                    clearOriginalClipSurface()
                    cameraRecordingUseCase.stopOriginalClipBuffer()
                    cameraAnalysisUseCase.setBroadcastSourceOverride(0, 0)
                    _uiState.value = _uiState.value.copy(
                        isRecording = false,
                        isOriginalClipBuffering = false,
                        isSavingOriginalClip = false,
                    )
                },
            )
            if (startResult.isFailure) {
                cameraAnalysisUseCase.closeMetadataSession()
            }
        }
    }

    fun stopRecording() {
        stopRecordingInternal(saveRecordingFile = true)
    }

    fun saveOriginalClip() {
        val state = _uiState.value
        if (!state.isRecording || !state.isOriginalClipBuffering || state.isSavingOriginalClip) {
            return
        }

        _uiState.value = state.copy(
            isSavingOriginalClip = true,
            savedOriginalClipUri = null,
            originalClipSaveError = null,
        )
        viewModelScope.launch(ioDispatcher) {
            val result = cameraRecordingUseCase.saveOriginalClipToGallery()
            _uiState.value = _uiState.value.copy(
                isSavingOriginalClip = false,
                savedOriginalClipUri = result.getOrNull(),
                originalClipSaveError = result.exceptionOrNull()?.message ?: if (result.isFailure) {
                    "원본 클립 저장 실패"
                } else {
                    null
                },
            )
        }
    }

    fun switchLensFacing() {
        cameraAnalysisUseCase.updateSourceFrameSize(0, 0)
        manualOwnerTrackIds.clear()
        pendingOwnerRegistrationTrackId = null

        val nextLens = when (_uiState.value.lensFacing) {
            LensFacing.FRONT -> LensFacing.BACK
            LensFacing.BACK -> LensFacing.FRONT
        }
        _uiState.value = _uiState.value.copy(
            lensFacing = nextLens,
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList(),
            previewWidth = 0,
            previewHeight = 0,
            registeredOwners = emptyList(),
        )
        cameraAnalysisUseCase.clearProcessingThreadCache()
    }

    fun registerOwnerByTrackId(
        trackId: Int,
        fallbackFaceIndex: Int? = null,
    ) {
        val state = _uiState.value
        if (!state.isCameraActive || !state.isDetecting) return
        val resolvedTrackId = resolveTrackId(trackId, fallbackFaceIndex, state.trackingIds)

        pendingOwnerRegistrationTrackId = resolvedTrackId

        _uiState.value = state.copy(
            faceLabels = state.faceLabels.mapIndexed { index, currentLabel ->
                val currentTrackId = state.trackingIds.getOrNull(index) ?: index
                if (currentTrackId == resolvedTrackId || fallbackFaceIndex == index) {
                    true
                } else {
                    currentLabel
                }
            },
        )
    }

    fun clearRecordingFile() {
        _uiState.value = _uiState.value.copy(recordingFile = null)
    }

    fun clearOriginalClipSaveMessage() {
        _uiState.value = _uiState.value.copy(
            savedOriginalClipUri = null,
            originalClipSaveError = null,
        )
    }

    fun removeRegisteredOwner(owner: RegisteredOwner) {
        val removed = cameraAnalysisUseCase.removeOwner(
            ownerId = owner.ownerId,
            trackId = owner.trackId,
            thumbnailPath = owner.thumbnailPath,
        )
        if (!removed) return

        manualOwnerTrackIds.remove(owner.trackId)
        if (pendingOwnerRegistrationTrackId == owner.trackId) {
            pendingOwnerRegistrationTrackId = null
        }

        val currentState = _uiState.value
        val remainingOwners = currentState.registeredOwners
            .filterNot {
                it.ownerId == owner.ownerId &&
                    it.trackId == owner.trackId &&
                    it.thumbnailPath == owner.thumbnailPath
            }
            .mapIndexed { index, item ->
                item.copy(ownerId = index)
            }

        _uiState.value = currentState.copy(
            registeredOwners = remainingOwners,
            faceLabels = currentState.faceLabels.mapIndexed { index, currentLabel ->
                val currentTrackId = currentState.trackingIds.getOrNull(index) ?: index
                if (currentTrackId == owner.trackId) {
                    false
                } else {
                    currentLabel
                }
            },
        )
    }

    fun clearProcessingThreadCache() {
        cameraAnalysisUseCase.clearProcessingThreadCache()
    }

    fun resetSessionState() {
        manualOwnerTrackIds.clear()
        pendingOwnerRegistrationTrackId = null
        _uiState.value = _uiState.value.copy(
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList(),
            registeredOwners = emptyList(),
        )
        cameraAnalysisUseCase.resetSessionState()
    }

    override fun onCleared() {
        try {
            stopRecordingInternal(saveRecordingFile = false, forceSynchronous = true)
            cameraAnalysisUseCase.clearProcessingThreadCache()
        } finally {
            super.onCleared()
        }
    }

    private fun stopRecordingInternal(
        saveRecordingFile: Boolean,
        forceSynchronous: Boolean = false,
    ) {
        val wasRecording = _uiState.value.isRecording
        val fileToEmit = if (saveRecordingFile) currentRecordingFile else null

        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isOriginalClipBuffering = false,
            isSavingOriginalClip = false,
        )
        clearEncoderSurface()
        clearOriginalClipSurface()

        if (!wasRecording) {
            if (!saveRecordingFile) {
                currentRecordingFile = null
            }
            cameraRecordingUseCase.stopOriginalClipBuffer()
            return
        }

        val stopAction: suspend () -> Unit = {
            cameraRecordingUseCase.stopOriginalClipBuffer()
            cameraRecordingUseCase.stopRecording()
            cameraAnalysisUseCase.closeMetadataSession()
            cameraAnalysisUseCase.setBroadcastSourceOverride(0, 0)
            currentRecordingFile = null
            _uiState.value = _uiState.value.copy(
                isOriginalClipBuffering = false,
                isSavingOriginalClip = false,
            )
            if (fileToEmit != null) {
                _uiState.value = _uiState.value.copy(recordingFile = fileToEmit)
            }
        }

        if (forceSynchronous) {
            runBlocking {
                withContext(ioDispatcher + NonCancellable) {
                    stopAction()
                }
            }
        } else {
            viewModelScope.launch(ioDispatcher) {
                stopAction()
            }
        }
    }

    private fun clearEncoderSurface() {
        currentEncoderSurface = null
        currentEncoderWidth = 0
        currentEncoderHeight = 0
        encoderSurfaceDispatcher?.invoke(null, 0, 0)
    }

    private fun clearOriginalClipSurface() {
        currentOriginalClipSurface = null
        currentOriginalClipWidth = 0
        currentOriginalClipHeight = 0
        originalClipSurfaceDispatcher?.invoke(null, 0, 0)
    }

    private fun startOriginalClipBufferInternal(
        width: Int,
        height: Int,
        encoderConfig: EncoderConfig?,
    ): Boolean {
        val result = cameraRecordingUseCase.startOriginalClipBuffer(
            width = width,
            height = height,
            encoderConfig = encoderConfig,
            onSurfaceReady = { encoderSurface, targetWidth, targetHeight ->
                currentOriginalClipSurface = encoderSurface.surface
                currentOriginalClipWidth = targetWidth
                currentOriginalClipHeight = targetHeight
                originalClipSurfaceDispatcher?.invoke(
                    currentOriginalClipSurface,
                    currentOriginalClipWidth,
                    currentOriginalClipHeight,
                )
            },
        )
        if (result.isFailure) {
            clearOriginalClipSurface()
        }
        return result.isSuccess
    }

    private fun resolveTrackId(
        trackId: Int,
        fallbackFaceIndex: Int?,
        trackingIds: List<Int>,
    ): Int {
        if (trackId in trackingIds) return trackId
        val fallbackIndex = fallbackFaceIndex ?: return trackId
        return trackingIds.getOrElse(fallbackIndex) { trackId }
    }
}
