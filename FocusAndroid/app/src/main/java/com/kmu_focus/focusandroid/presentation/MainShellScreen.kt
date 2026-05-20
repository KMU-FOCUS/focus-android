package com.kmu_focus.focusandroid.presentation

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.core.ui.insets.focusContentPadding
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.BroadcastCameraScreen

@Composable
fun MainShellScreen(
    modifier: Modifier = Modifier,
    viewModel: MainShellViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusContentPadding(
                scaffoldPadding = androidx.compose.foundation.layout.PaddingValues(),
                mode = uiState.currentDestination.contentInsetMode,
            ),
    ) {
        BroadcastCameraScreen(
            modifier = Modifier.fillMaxSize(),
            onRootBack = {
                activity?.moveTaskToBack(true)
            },
        )
    }
}
