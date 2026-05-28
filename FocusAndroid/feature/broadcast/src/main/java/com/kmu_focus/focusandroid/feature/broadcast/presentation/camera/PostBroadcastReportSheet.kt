package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPrimaryButton
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSecondaryButton
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus

@Composable
internal fun PostBroadcastReportSheet(
    report: CompletedBroadcastReport,
    onDismiss: () -> Unit,
) {
    var showDetail by remember { mutableStateOf(false) }
    val shouldShowFinalAnalysis = report.hasFinalAnalysis && report.analysisStatus == BroadcastAnalysisStatus.SUCCEEDED
    val scrimColor = Color.Black.copy(alpha = 0.58f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        ReportDialogSystemBars(scrimColor = scrimColor)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                color = Color(0xFFF6FBFE),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, FocusIosPalette.Border.copy(alpha = 0.55f)),
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    CapsuleHandle()
                    ReportHeader(report = report)
                    MetaCards(report = report)
                    when {
                        shouldShowFinalAnalysis -> {
                            ReportCard(title = "요약") {
                                Text(
                                    text = report.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FocusIosPalette.TextMuted,
                                )
                            }
                            BulletCard(
                                title = "잘한 점",
                                items = report.strengths,
                                accent = FocusIosPalette.Secondary,
                            )
                            BulletCard(
                                title = "아쉬운 점",
                                items = report.weaknesses,
                                accent = FocusIosPalette.Warning,
                            )
                            BulletCard(
                                title = "다음 방송 팁",
                                items = report.actionItems,
                                accent = FocusIosPalette.Primary,
                            )
                            StatsRow(report = report)

                            if (showDetail) {
                                ReportDetailCard(report = report)
                            } else {
                                FocusIosPrimaryButton(
                                    text = "자세히 보기",
                                    onClick = { showDetail = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        report.analysisStatus == BroadcastAnalysisStatus.FAILED -> {
                            AnalysisFailureCard(report = report)
                        }
                        else -> {
                            AnalysisLoadingCard()
                            if (showDetail) {
                                ReportDetailCard(report = report)
                            } else {
                                FocusIosSecondaryButton(
                                    text = "분석 진행 정보",
                                    onClick = { showDetail = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    FocusIosSecondaryButton(
                        text = "닫기",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ReportDialogSystemBars(scrimColor: Color) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return

    DisposableEffect(window, scrimColor) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousStatusBarColor = window.statusBarColor
        val previousNavigationBarColor = window.navigationBarColor
        val previousSystemBarsBehavior = controller.systemBarsBehavior
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = scrimColor.toArgb()
        window.navigationBarColor = scrimColor.toArgb()
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        onDispose {
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavigationBarColor
            controller.systemBarsBehavior = previousSystemBarsBehavior
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}

@Composable
private fun AnalysisLoadingCard() {
    ReportCard(title = "AI 분석 중") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = FocusIosPalette.Primary,
                trackColor = FocusIosPalette.Border,
                strokeWidth = 2.5.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "방송 요약과 집계 결과를 불러오는 중입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusIosPalette.Text,
                )
                Text(
                    text = "결과가 준비되면 이 시트가 자동으로 업데이트됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FocusIosPalette.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun AnalysisFailureCard(report: CompletedBroadcastReport) {
    ReportCard(title = "분석 결과를 불러오지 못했습니다") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "서버에서 최종 AI 리포트를 반환하지 않아 세부 결과를 표시할 수 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = FocusIosPalette.TextMuted,
            )
            report.analysisErrorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Surface(
                    color = FocusIosPalette.Danger.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, FocusIosPalette.Danger.copy(alpha = 0.18f)),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = FocusIosPalette.Danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleHandle() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        color = Color.Transparent,
    ) {
        Surface(
            modifier = Modifier
                .size(width = 56.dp, height = 6.dp),
            color = FocusIosPalette.Border,
            shape = RoundedCornerShape(999.dp),
        ) {}
    }
}

@Composable
private fun ReportHeader(report: CompletedBroadcastReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = report.title,
                style = MaterialTheme.typography.headlineSmall,
                color = FocusIosPalette.Text,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = report.headerMessage,
                style = MaterialTheme.typography.bodySmall,
                color = FocusIosPalette.TextMuted,
            )
        }
        StatusBadge(status = report.analysisStatus)
    }
}

private val CompletedBroadcastReport.headerMessage: String
    get() = when {
        hasFinalAnalysis && analysisStatus == BroadcastAnalysisStatus.SUCCEEDED -> {
            "방송 종료 직후 확인할 수 있는 AI 요약 리포트"
        }
        analysisStatus == BroadcastAnalysisStatus.FAILED -> {
            "AI 분석 결과를 가져오지 못했습니다"
        }
        else -> {
            "AI 분석 결과를 준비하고 있습니다"
        }
    }

@Composable
private fun StatusBadge(status: BroadcastAnalysisStatus) {
    val containerColor = when (status) {
        BroadcastAnalysisStatus.SUCCEEDED -> FocusIosPalette.SecondarySoft
        BroadcastAnalysisStatus.PROCESSING -> FocusIosPalette.WarningSoft
        BroadcastAnalysisStatus.FAILED -> FocusIosPalette.Danger.copy(alpha = 0.12f)
    }
    val contentColor = when (status) {
        BroadcastAnalysisStatus.SUCCEEDED -> FocusIosPalette.Secondary
        BroadcastAnalysisStatus.PROCESSING -> FocusIosPalette.Warning
        BroadcastAnalysisStatus.FAILED -> FocusIosPalette.Danger
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Text(
            text = status.title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MetaCards(report: CompletedBroadcastReport) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            modifier = Modifier.weight(1f),
            title = "방송 길이",
            value = report.durationSec.toDurationLabel(),
        )
        InfoCard(
            modifier = Modifier.weight(1f),
            title = "분석 상태",
            value = report.analysisStatus.title,
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, FocusIosPalette.Border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = FocusIosPalette.TextMuted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = FocusIosPalette.Text,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ReportCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, FocusIosPalette.Border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = FocusIosPalette.Text,
                    fontWeight = FontWeight.Bold,
                )
                content()
            },
        )
    }
}

@Composable
private fun BulletCard(
    title: String,
    items: List<String>,
    accent: Color,
) {
    ReportCard(title = title) {
        if (items.isEmpty()) {
            Text(
                text = "분석 결과를 준비하는 중입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = FocusIosPalette.TextMuted,
            )
        }
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier.padding(top = 6.dp),
                    color = accent,
                    shape = CircleShape,
                ) {
                    SpacerDot()
                }
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusIosPalette.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun SpacerDot() {
    Surface(
        modifier = Modifier.size(8.dp),
        color = Color.Transparent,
    ) {}
}

@Composable
private fun StatsRow(report: CompletedBroadcastReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            title = "보호 처리",
            value = report.replacedFaceCount.toString(),
            subtitle = "얼굴 수",
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            title = "최대 혼잡도",
            value = report.maxCrowdCount.toString(),
            subtitle = "동시 인원",
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            title = "하이라이트",
            value = report.highlightCount.toString(),
            subtitle = "추천 구간",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, FocusIosPalette.Border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = FocusIosPalette.TextMuted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = FocusIosPalette.Text,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FocusIosPalette.TextMuted,
            )
        }
    }
}

@Composable
private fun ReportDetailCard(report: CompletedBroadcastReport) {
    ReportCard(title = "세부 분석 결과") {
        DetailRow(label = "Broadcast ID", value = report.broadcastId)
        report.analysisJobId?.let { DetailRow(label = "Analysis Job", value = it) }
        DetailRow(label = "방송 길이", value = report.durationSec.toDurationLabel())
        DetailRow(label = "Owner 수", value = report.ownerCount.toString())
        DetailRow(label = "분석 상태", value = report.analysisStatus.title)
        if (report.peakViewerCount > 0) {
            DetailRow(label = "최고 시청자 수", value = report.peakViewerCount.toString())
        }
        report.peakOccurredAtLabel?.let { DetailRow(label = "최고점 시각", value = it) }
        if (report.peakSceneDescription.isNotBlank()) {
            DetailRow(label = "피크 장면", value = report.peakSceneDescription)
        }
        DetailRow(label = "완료 시각", value = report.completedAtMillis.toDateTimeLabel())
        if (report.contentRatios.isNotEmpty()) {
            DetailSectionTitle(title = "콘텐츠 비율")
            report.contentRatios.forEach { ratio ->
                DetailRow(
                    label = ratio.contentType,
                    value = "${ratio.percentage.toInt()}% · ${ratio.durationSec.toDurationLabel()}",
                )
            }
        }
        if (report.highlightMoments.isNotEmpty()) {
            DetailSectionTitle(title = "하이라이트 포인트")
            report.highlightMoments.forEach { moment ->
                DetailRow(
                    label = "${moment.timeLabel} ${moment.title}",
                    value = moment.description,
                )
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = FocusIosPalette.Text,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FocusIosPalette.TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = FocusIosPalette.Text,
        )
    }
}
