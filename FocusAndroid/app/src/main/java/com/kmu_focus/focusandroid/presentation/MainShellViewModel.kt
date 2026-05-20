package com.kmu_focus.focusandroid.presentation

import androidx.lifecycle.ViewModel
import com.kmu_focus.focusandroid.core.ui.insets.FocusContentInsetMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MainShellDestination {
    data object LiveCamera : MainShellDestination
}

internal val MainShellDestination.contentInsetMode: FocusContentInsetMode
    get() = FocusContentInsetMode.EdgeToEdge

data class MainShellUiState(
    val currentDestination: MainShellDestination = MainShellDestination.LiveCamera,
)

@HiltViewModel
class MainShellViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MainShellUiState())
    val uiState: StateFlow<MainShellUiState> = _uiState.asStateFlow()
}
