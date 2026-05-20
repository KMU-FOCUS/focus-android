package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPalette
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosPrimaryButton
import com.kmu_focus.focusandroid.core.ui.ios.FocusIosSecondaryButton
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostBroadcastReportSheet(
    report: CompletedBroadcastReport,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDetail by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFF6FBFE),
        dragHandle = null,
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

            FocusIosSecondaryButton(
                text = "닫기",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
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
                text = "방송 종료 직후 확인할 수 있는 AI 요약 리포트",
                style = MaterialTheme.typography.bodySmall,
                color = FocusIosPalette.TextMuted,
            )
        }
        StatusBadge(status = report.analysisStatus)
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
