package com.kmu_focus.focusandroid.core.ui.ios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FocusIosPalette {
    val BackgroundTop = Color(0xFFF0F9FF)
    val BackgroundBottom = Color(0xFFE4F3FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFEAF3F9)
    val Border = Color(0xFFD7EAF5)
    val Text = Color(0xFF0C4A6E)
    val TextMuted = Color(0xFF4A7087)
    val Primary = Color(0xFF0369A1)
    val PrimaryBright = Color(0xFF0EA5E9)
    val Secondary = Color(0xFF047857)
    val SecondarySoft = Color(0xFFE8F6F1)
    val Danger = Color(0xFFDC2626)
    val DangerBright = Color(0xFFEF4444)
    val Warning = Color(0xFFD97706)
    val WarningSoft = Color(0xFFFFF3E0)
    val KakaoYellow = Color(0xFFFEE500)
    val KakaoText = Color(0xFF191919)
    val Dim = Color(0x55000000)
}

val FocusIosLargeShape = RoundedCornerShape(28.dp)
val FocusIosMediumShape = RoundedCornerShape(22.dp)
val FocusIosSmallShape = RoundedCornerShape(18.dp)

@Composable
fun FocusIosGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(
                    FocusIosPalette.BackgroundTop,
                    Color.White,
                    FocusIosPalette.BackgroundBottom,
                ),
            ),
        ),
        content = content,
    )
}

@Composable
fun FocusIosSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = FocusIosPalette.Surface,
        shape = FocusIosLargeShape,
        border = BorderStroke(1.dp, FocusIosPalette.Border),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun FocusIosPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDanger: Boolean = false,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = if (isDanger) {
        listOf(FocusIosPalette.Danger, FocusIosPalette.DangerBright)
    } else {
        listOf(FocusIosPalette.Primary, FocusIosPalette.PrimaryBright)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = FocusIosSmallShape,
        color = Color.Transparent,
        shadowElevation = if (enabled) 10.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(colors),
                    shape = FocusIosSmallShape,
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
            }
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                ),
            )
        }
    }
}

@Composable
fun FocusIosSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = FocusIosPalette.Primary,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        color = FocusIosPalette.Surface,
        shape = FocusIosSmallShape,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = accentColor,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
fun FocusIosStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = FocusIosPalette.SurfaceMuted,
    contentColor: Color = FocusIosPalette.Primary,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
fun FocusIosPanelHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = FocusIosPalette.Text,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = FocusIosPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun FocusIosMenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = FocusIosPalette.Primary,
    leading: @Composable BoxScope.() -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        color = FocusIosPalette.Surface,
        shape = FocusIosMediumShape,
        border = BorderStroke(1.dp, FocusIosPalette.Border),
        onClick = onClick,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
                content = leading,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = FocusIosPalette.Text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = subtitle,
                    color = FocusIosPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun FocusIosCapsuleHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(6.dp)
            .size(width = 56.dp, height = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(FocusIosPalette.Border),
    )
}
