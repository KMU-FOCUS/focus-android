package com.kmu_focus.focusandroid.feature.video.presentation.videoupload

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPrimaryButton

@Composable
fun VideoUploadScreen(
    onVideoSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoUploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun handleSelectedVideo(uri: Uri) {
        val uriString = uri.toString()
        viewModel.selectVideo(uriString)
        onVideoSelected(uriString)
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        handleSelectedVideo(uri)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FocusIosPrimaryButton(
            text = "갤러리에서 동영상 선택",
            onClick = {
                val galleryIntent = Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ).apply {
                    type = "video/*"
                }
                galleryPickerLauncher.launch(galleryIntent)
            },
            modifier = Modifier.fillMaxWidth()
        )

        when {
            uiState.isLoading -> {
                CircularProgressIndicator(color = FocusIosPalette.Primary)
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    color = FocusIosPalette.Danger,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
