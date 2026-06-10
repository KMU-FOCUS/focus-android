package com.kmu_focus.focusandroid.presentation

import com.kmu_focus.focusandroid.core.ui.insets.FocusContentInsetMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MainShellViewModelTest {

    @Test
    fun `메인 셸은 항상 라이브 카메라 화면을 사용한다`() {
        val viewModel = MainShellViewModel()
        val state = viewModel.uiState.value

        assertEquals(MainShellDestination.LiveCamera, state.currentDestination)
        assertEquals(FocusContentInsetMode.EdgeToEdge, state.currentDestination.contentInsetMode)
    }
}
