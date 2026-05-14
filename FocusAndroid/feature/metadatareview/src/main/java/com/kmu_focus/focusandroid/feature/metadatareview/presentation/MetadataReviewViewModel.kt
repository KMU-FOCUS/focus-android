package com.kmu_focus.focusandroid.feature.metadatareview.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.feature.metadatareview.data.MetadataPreviewParser
import com.kmu_focus.focusandroid.feature.metadatareview.domain.model.ParsedMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MetadataReviewUiState(
    val videoUri: String? = null,
    val videoName: String? = null,
    val metadataName: String? = null,
    val parsedMetadata: ParsedMetadata? = null,
    val currentFrame: FrameMetadata? = null,
    val playbackPositionMs: Long = 0L,
    val isMetadataLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val frameCount: Int
        get() = parsedMetadata?.frameCount ?: 0

    val totalFaceCount: Int
        get() = parsedMetadata?.totalFaceCount ?: 0

    val currentFaceCount: Int
        get() = currentFrame?.faces?.size ?: 0
}

@HiltViewModel
class MetadataReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: MetadataPreviewParser,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetadataReviewUiState())
    val uiState: StateFlow<MetadataReviewUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        persistReadPermission(uri)
        val videoName = resolveDisplayName(uri) ?: "선택한 동영상"

        _uiState.update { current ->
            current.copy(
                videoUri = uri.toString(),
                videoName = videoName,
                playbackPositionMs = 0L,
                currentFrame = current.parsedMetadata?.frameAt(0L),
            )
        }
    }

    fun onMetadataSelected(uri: Uri) {
        persistReadPermission(uri)
        val metadataName = resolveDisplayName(uri) ?: "metadata.json"

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    metadataName = metadataName,
                    isMetadataLoading = true,
                    errorMessage = null,
                )
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val jsonText = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw IllegalArgumentException("선택한 JSON 파일을 열 수 없습니다.")

                    parser.parse(jsonText)
                }
            }

            _uiState.update { current ->
                result.fold(
                    onSuccess = { parsedMetadata ->
                        current.copy(
                            metadataName = metadataName,
                            parsedMetadata = parsedMetadata,
                            currentFrame = parsedMetadata.frameAt(current.playbackPositionMs),
                            isMetadataLoading = false,
                            errorMessage = null,
                        )
                    },
                    onFailure = { throwable ->
                        current.copy(
                            metadataName = metadataName,
                            parsedMetadata = null,
                            currentFrame = null,
                            isMetadataLoading = false,
                            errorMessage = throwable.message ?: "메타데이터를 읽지 못했습니다.",
                        )
                    },
                )
            }
        }
    }

    fun onPlaybackPositionChanged(positionMs: Long) {
        _uiState.update { current ->
            val normalizedPositionMs = positionMs.coerceAtLeast(0L)
            val currentFrame = current.parsedMetadata?.frameAt(normalizedPositionMs)

            if (
                current.playbackPositionMs == normalizedPositionMs &&
                current.currentFrame === currentFrame
            ) {
                current
            } else {
                current.copy(
                    playbackPositionMs = normalizedPositionMs,
                    currentFrame = currentFrame,
                )
            }
        }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(columnIndex)
            } else {
                null
            }
        }
    }
}
