package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastContentRatio
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastFaceStatistics
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastHighlightCandidate
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastViewerPeakInsight
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.CreateBroadcastAnalysisJob
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val PLACEHOLDER_REPORT_MARKERS = listOf(
    "gemini",
    "파이프라인",
    "polling",
    "snapshot",
    "등록했습니다",
    "연결해",
    "집계되지 않았습니다",
    "비어 있습니다",
)

data class CompletedBroadcastReportSeed(
    val broadcastId: String,
    val durationSec: Int,
    val ownerCount: Int,
    val recordingFilePath: String?,
    val completedAtMillis: Long = System.currentTimeMillis(),
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
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

internal fun CompletedBroadcastReport.toCreateAnalysisJobRequest(
    seed: CompletedBroadcastReportSeed,
): CreateBroadcastAnalysisJob {
    return CreateBroadcastAnalysisJob(
        assetType = "ANALYSIS_MP4",
        jobType = "FULL_SUMMARY",
        storageProvider = "LOCAL_FILE",
        storageKey = seed.toStorageKey(),
        storageUrl = seed.recordingFilePath,
        durationSec = durationSec,
        resolutionWidth = seed.resolutionWidth,
        resolutionHeight = seed.resolutionHeight,
        fileSizeBytes = seed.recordingFilePath.toFileSizeOrNull(),
        summary = summary.takeIf { it.isNotBlank() },
        strengths = strengths,
        weaknesses = weaknesses,
        actionItems = actionItems,
        viewerPeakInsight = peakViewerInsightOrNull().takeIf { hasFinalAnalysis },
        faceStatistics = BroadcastFaceStatistics(
            totalReplacedFaceCount = replacedFaceCount,
            maxSimultaneousCrowdCount = maxCrowdCount,
        ).takeIf { hasFinalAnalysis },
        contentRatios = contentRatios.map {
            BroadcastContentRatio(
                contentType = it.contentType,
                percentage = it.percentage,
                durationSec = it.durationSec,
            )
        },
    )
}

internal fun BroadcastAnalysisResult.toCompletedBroadcastReport(
    seed: CompletedBroadcastReportSeed,
    highlights: List<CompletedBroadcastHighlightMoment>,
): CompletedBroadcastReport {
    val fallback = buildProcessingCompletedBroadcastReport(
        seed = seed,
        analysisStatus = latestJob?.jobStatus ?: BroadcastAnalysisStatus.PROCESSING,
        analysisJobId = latestJob?.analysisJobId,
    )
    val latest = latestReport ?: return fallback.copy(
        analysisStatus = latestJob?.jobStatus ?: BroadcastAnalysisStatus.PROCESSING,
        analysisErrorMessage = latestJob?.errorMessage,
        highlightCount = maxOf(highlightCount, highlights.size),
        highlightMoments = highlights,
    )
    if (!isPresentableFinalReport()) {
        return fallback.copy(
            analysisStatus = if (latestJob?.jobStatus == BroadcastAnalysisStatus.FAILED) {
                BroadcastAnalysisStatus.FAILED
            } else {
                BroadcastAnalysisStatus.PROCESSING
            },
            analysisErrorMessage = latestJob?.errorMessage,
            highlightCount = maxOf(highlightCount, highlights.size),
            highlightMoments = highlights,
        )
    }

    return CompletedBroadcastReport(
        reportId = latest.aiReportId,
        title = latest.title.ifBlank { fallback.title },
        broadcastId = broadcastId,
        analysisJobId = latestJob?.analysisJobId,
        analysisStatus = latestJob?.jobStatus ?: BroadcastAnalysisStatus.SUCCEEDED,
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
        hasFinalAnalysis = true,
    )
}

internal fun BroadcastAnalysisResult.isPresentableFinalReport(): Boolean {
    if (latestJob?.jobStatus == BroadcastAnalysisStatus.FAILED) {
        return false
    }
    val report = latestReport ?: return false
    return !report.looksLikePlaceholderReport()
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

private fun CompletedBroadcastReport.peakViewerInsightOrNull(): BroadcastViewerPeakInsight? {
    if (peakViewerCount <= 0 && peakOccurredAtLabel.isNullOrBlank() && peakSceneDescription.isBlank()) {
        return null
    }

    return BroadcastViewerPeakInsight(
        peakViewerCount = peakViewerCount,
        occurredAt = null,
        sceneDescription = peakSceneDescription.takeIf { it.isNotBlank() },
    )
}

private fun com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAiReport.looksLikePlaceholderReport(): Boolean {
    val allText = buildList {
        add(summary)
        addAll(strengths)
        addAll(weaknesses)
        addAll(actionItems)
    }.joinToString("\n").lowercase(Locale.ROOT)

    val containsPlaceholderMarker = PLACEHOLDER_REPORT_MARKERS.any { marker ->
        allText.contains(marker.lowercase(Locale.ROOT))
    }
    val lacksStructuredSignals = contentRatios.isEmpty() && (viewerPeakInsight?.peakViewerCount ?: 0) <= 0
    return containsPlaceholderMarker && lacksStructuredSignals
}

private fun CompletedBroadcastReportSeed.toStorageKey(): String {
    val recordingName = recordingFilePath
        ?.let(::File)
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?: "analysis_${broadcastId}.mp4"
    return "android/$broadcastId/$recordingName"
}

private fun String?.toFileSizeOrNull(): Long? {
    if (this.isNullOrBlank()) {
        return null
    }
    val file = File(this)
    return file.takeIf { it.exists() }?.length()
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
