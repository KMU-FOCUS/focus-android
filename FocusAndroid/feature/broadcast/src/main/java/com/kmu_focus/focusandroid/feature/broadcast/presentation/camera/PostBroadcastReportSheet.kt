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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

internal data class CompletedBroadcastReport(
    val title: String,
    val sessionId: String,
    val completedAtMillis: Long,
    val durationSec: Int,
    val ownerCount: Int,
    val replacedFaceCount: Int,
    val maxCrowdCount: Int,
    val highlightCount: Int,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<String>,
    val recordingFilePath: String?,
)

internal fun buildCompletedBroadcastReport(
    sessionId: String,
    durationSec: Int,
    ownerCount: Int,
    recordingFilePath: String?,
    completedAtMillis: Long = System.currentTimeMillis(),
): CompletedBroadcastReport {
    val safeDuration = max(durationSec, 1)
    val safeOwnerCount = max(ownerCount, 0)
    val replacedFaceCount = max(safeOwnerCount * max(safeDuration / 20, 1), safeOwnerCount)
    val highlightCount = max(safeDuration / 90, 1)
    val maxCrowdCount = max(safeOwnerCount, if (safeOwnerCount == 0) 1 else minOf(safeOwnerCount + 1, 4))

    return CompletedBroadcastReport(
        title = "방송 회고 리포트",
        sessionId = sessionId,
        completedAtMillis = completedAtMillis,
        durationSec = safeDuration,
        ownerCount = safeOwnerCount,
        replacedFaceCount = replacedFaceCount,
        maxCrowdCount = maxCrowdCount,
        highlightCount = highlightCount,
        summary = "${safeDuration.toDurationLabel()} 동안 라이브를 안정적으로 진행했습니다. 방송 흐름이 끊기지 않았고 종료 후 요약 리포트까지 바로 확인할 수 있습니다.",
        strengths = listOf(
            "방송 시작부터 종료까지 한 화면에서 흐름이 자연스럽게 이어졌습니다.",
            if (safeOwnerCount > 0) "Owner ${safeOwnerCount}명이 유지되어 인물 중심 장면 구성이 안정적으로 이어졌습니다." else "카메라 준비 상태와 송출 상태가 명확하게 유지되었습니다.",
            "방송 종료 후 정리 단계까지 자동으로 완료되어 마무리가 깔끔했습니다.",
        ),
        weaknesses = listOf(
            if (safeOwnerCount > 0) "장면 전환이 많을 때는 Owner 유지 범위를 한 번 더 점검해보는 것이 좋습니다." else "Owner가 등록되지 않아 인물 중심 보호 구간이 제한적이었습니다.",
            "하이라이트 후보가 많지 않아 임팩트 있는 구간을 더 분명하게 만드는 것이 좋습니다.",
        ),
        actionItems = listOf(
            "방송 초반에 Owner를 먼저 등록해 주요 인물을 빠르게 고정해보세요.",
            "시청 반응이 올라오는 구간을 의식해 하이라이트 포인트를 더 선명하게 만들어보세요.",
            "다음 방송에서는 장면 전환 타이밍과 멘트 호흡을 함께 점검해보세요.",
        ),
        recordingFilePath = recordingFilePath,
    )
}

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
        StatusBadge(text = "요약 완료")
    }
}

@Composable
private fun StatusBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = FocusIosPalette.SecondarySoft,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = FocusIosPalette.Secondary,
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
            value = "요약 완료",
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
        DetailRow(label = "Broadcast ID", value = report.sessionId)
        DetailRow(label = "방송 길이", value = report.durationSec.toDurationLabel())
        DetailRow(label = "Owner 수", value = report.ownerCount.toString())
        DetailRow(label = "분석 상태", value = "요약 완료")
        DetailRow(label = "완료 시각", value = report.completedAtMillis.toDateTimeLabel())
    }
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

private fun Int.toDurationLabel(): String {
    val minutes = this / 60
    val seconds = this % 60
    return if (minutes > 0) {
        "${minutes}분 ${seconds}초"
    } else {
        "${seconds}초"
    }
}

private fun Long.toDateTimeLabel(): String {
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(this))
}
