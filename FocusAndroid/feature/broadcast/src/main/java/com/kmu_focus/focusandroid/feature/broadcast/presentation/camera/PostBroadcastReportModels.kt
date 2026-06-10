package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastHighlightCandidate
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class CompletedBroadcastReportSeed(
    val broadcastId: String,
    val durationSec: Int,
    val ownerCount: Int,
    val recordingFilePath: String?,
    val completedAtMillis: Long = System.currentTimeMillis(),
)

data class CompletedBroadcastContentRatio(
    val contentType: String,
    val percentage: Double,
    val durationSec: Int,
)

data class CompletedBroadcastHighlightMoment(
    val timeLabel: String,
    val title: String,
    val description: String,
)

data class CompletedBroadcastReport(
    val reportId: String,
    val title: String,
    val broadcastId: String,
    val analysisJobId: String? = null,
    val analysisStatus: BroadcastAnalysisStatus,
    val analysisErrorMessage: String? = null,
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
    val peakViewerCount: Int = 0,
    val peakOccurredAtLabel: String? = null,
    val peakSceneDescription: String = "",
    val contentRatios: List<CompletedBroadcastContentRatio> = emptyList(),
    val highlightMoments: List<CompletedBroadcastHighlightMoment> = emptyList(),
    val recordingFilePath: String? = null,
    val hasFinalAnalysis: Boolean = false,
)

internal fun buildProcessingCompletedBroadcastReport(
    seed: CompletedBroadcastReportSeed,
    analysisStatus: BroadcastAnalysisStatus = BroadcastAnalysisStatus.PROCESSING,
    analysisJobId: String? = null,
): CompletedBroadcastReport {
    val safeDuration = max(seed.durationSec, 1)
    val safeOwnerCount = max(seed.ownerCount, 0)

    return CompletedBroadcastReport(
        reportId = "processing-${seed.broadcastId}",
        title = "FOCUS 방송 리포트",
        broadcastId = seed.broadcastId,
        analysisJobId = analysisJobId,
        analysisStatus = analysisStatus,
        analysisErrorMessage = null,
        completedAtMillis = seed.completedAtMillis,
        durationSec = safeDuration,
        ownerCount = safeOwnerCount,
        replacedFaceCount = 0,
        maxCrowdCount = 0,
        highlightCount = 0,
        summary = "",
        strengths = emptyList(),
        weaknesses = emptyList(),
        actionItems = emptyList(),
        contentRatios = emptyList(),
        recordingFilePath = seed.recordingFilePath,
        hasFinalAnalysis = false,
    )
}

internal fun BroadcastAnalysisResult.toCompletedBroadcastReport(
    seed: CompletedBroadcastReportSeed,
    highlights: List<CompletedBroadcastHighlightMoment>,
): CompletedBroadcastReport {
    val resolvedStatus = latestJob?.jobStatus ?: BroadcastAnalysisStatus.PROCESSING
    val fallback = buildProcessingCompletedBroadcastReport(
        seed = seed,
        analysisStatus = resolvedStatus,
        analysisJobId = latestJob?.analysisJobId,
    )
    val latest = latestReport ?: return fallback.copy(
        analysisStatus = resolvedStatus,
        analysisErrorMessage = latestJob?.errorMessage,
        highlightCount = maxOf(highlightCount, highlights.size),
        highlightMoments = highlights,
    )

    return CompletedBroadcastReport(
        reportId = latest.aiReportId,
        title = latest.title.ifBlank { fallback.title },
        broadcastId = broadcastId,
        analysisJobId = latestJob?.analysisJobId,
        analysisStatus = resolvedStatus,
        analysisErrorMessage = latestJob?.errorMessage,
        completedAtMillis = latest.createdAt.toEpochMillisOrNull()
            ?: latestJob?.completedAt.toEpochMillisOrNull()
            ?: seed.completedAtMillis,
        durationSec = latestJob?.mediaAsset?.durationSec ?: fallback.durationSec,
        ownerCount = seed.ownerCount.coerceAtLeast(0),
        replacedFaceCount = latest.faceStatistics.totalReplacedFaceCount,
        maxCrowdCount = latest.faceStatistics.maxSimultaneousCrowdCount,
        highlightCount = maxOf(highlightCount, highlights.size),
        summary = latest.summary.ifBlank { fallback.summary },
        strengths = latest.strengths.ifEmpty { fallback.strengths },
        weaknesses = latest.weaknesses.ifEmpty { fallback.weaknesses },
        actionItems = latest.actionItems.ifEmpty { fallback.actionItems },
        peakViewerCount = latest.viewerPeakInsight?.peakViewerCount ?: 0,
        peakOccurredAtLabel = latest.viewerPeakInsight?.occurredAt.toReadableDateTimeLabel(),
        peakSceneDescription = latest.viewerPeakInsight?.sceneDescription.orEmpty(),
        contentRatios = latest.contentRatios.map {
            CompletedBroadcastContentRatio(
                contentType = it.contentType,
                percentage = it.percentage,
                durationSec = it.durationSec,
            )
        },
        highlightMoments = highlights,
        recordingFilePath = seed.recordingFilePath,
        hasFinalAnalysis = resolvedStatus == BroadcastAnalysisStatus.SUCCEEDED,
    )
}

internal fun BroadcastHighlightCandidate.toCompletedHighlightMoment(): CompletedBroadcastHighlightMoment {
    return CompletedBroadcastHighlightMoment(
        timeLabel = startSec.toClockLabel(),
        title = title,
        description = reason,
    )
}

internal val BroadcastAnalysisStatus.title: String
    get() = when (this) {
        BroadcastAnalysisStatus.PROCESSING -> "분석 중"
        BroadcastAnalysisStatus.SUCCEEDED -> "분석 완료"
        BroadcastAnalysisStatus.FAILED -> "분석 실패"
    }

internal fun Int.toDurationLabel(): String {
    val minutes = this / 60
    val seconds = this % 60
    return if (minutes > 0) {
        "${minutes}분 ${seconds}초"
    } else {
        "${seconds}초"
    }
}

internal fun Long.toDateTimeLabel(): String {
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(this))
}

private fun String?.toReadableDateTimeLabel(): String? {
    val epochMillis = toEpochMillisOrNull() ?: return null
    return epochMillis.toDateTimeLabel()
}

private fun String?.toEpochMillisOrNull(): Long? {
    if (this.isNullOrBlank()) {
        return null
    }

    return runCatching {
        OffsetDateTime.parse(this).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

private fun Int.toClockLabel(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return if (hours > 0) {
        String.format(Locale.KOREA, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.KOREA, "%02d:%02d", minutes, seconds)
    }
}
