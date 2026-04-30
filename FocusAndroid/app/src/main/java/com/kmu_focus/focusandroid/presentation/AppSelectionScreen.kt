package com.kmu_focus.focusandroid.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthScreen
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthSessionViewModel

@Composable
fun AppSelectionScreen(
    modifier: Modifier = Modifier,
) {
    val authSessionViewModel: AuthSessionViewModel = hiltViewModel()
    val isLoggedIn by authSessionViewModel.isLoggedIn.collectAsStateWithLifecycle()

    if (!isLoggedIn) {
        AuthScreen(
            onLoginSuccess = { },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    MainShellScreen(modifier = modifier)
}
