package com.kmu_focus.focusandroid.feature.auth.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.tryAutoLogin()
    }

    when (val state = uiState) {
        AuthUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        AuthUiState.NeedLogin -> {
            AuthActionContent(
                modifier = modifier,
                message = "카카오 로그인이 필요합니다.",
                onClick = { viewModel.kakaoLogin(context) },
            )
        }

        AuthUiState.Success -> {
            LaunchedEffect(Unit) {
                onLoginSuccess()
            }
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is AuthUiState.Error -> {
            AuthActionContent(
                modifier = modifier,
                message = state.message,
                onClick = { viewModel.kakaoLogin(context) },
            )
        }
    }
}

@Composable
private fun AuthActionContent(
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text = message)
        KakaoLoginButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp),
        )
    }
}

@Composable
private fun KakaoLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(6.dp),
        color = KakaoYellow,
        contentColor = KakaoLabelColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Canvas(modifier = Modifier.height(18.dp).widthIn(min = 18.dp, max = 18.dp)) {
                    drawKakaoSymbol()
                }
                Text(
                    text = "카카오 로그인",
                    modifier = Modifier.align(Alignment.Center),
                    color = KakaoLabelColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKakaoSymbol() {
    val bubbleWidth = size.width * 0.82f
    val bubbleHeight = size.height * 0.74f
    val bubbleTop = size.height * 0.06f
    val bubbleLeft = size.width * 0.08f

    drawOval(
        color = Color.Black,
        topLeft = Offset(bubbleLeft, bubbleTop),
        size = Size(bubbleWidth, bubbleHeight),
    )

    val tail = Path().apply {
        moveTo(size.width * 0.30f, size.height * 0.62f)
        lineTo(size.width * 0.20f, size.height * 0.98f)
        lineTo(size.width * 0.47f, size.height * 0.76f)
        close()
    }
    drawPath(
        path = tail,
        color = Color.Black,
    )
}

private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabelColor = Color.Black.copy(alpha = 0.85f)
