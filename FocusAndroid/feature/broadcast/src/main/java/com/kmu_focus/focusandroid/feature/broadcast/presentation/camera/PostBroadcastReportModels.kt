package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastContentRatio
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastFaceStatistics
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastHighlightCandidate
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastViewerPeakInsight
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.CompleteBroadcastAnalysisJob
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
)

internal fun buildLocalCompletedBroadcastReport(
    seed: CompletedBroadcastReportSeed,
    analysisStatus: BroadcastAnalysisStatus = BroadcastAnalysisStatus.SUCCEEDED,
    analysisJobId: String? = null,
): CompletedBroadcastReport {
    val safeDuration = max(seed.durationSec, 1)
    val safeOwnerCount = max(seed.ownerCount, 0)
    val replacedFaceCount = max(safeOwnerCount * max(safeDuration / 20, 1), safeOwnerCount)
    val highlightCount = max(safeDuration / 90, 1)
    val maxCrowdCount = max(safeOwnerCount, if (safeOwnerCount == 0) 1 else minOf(safeOwnerCount + 1, 4))

    return CompletedBroadcastReport(
        reportId = "local-${seed.broadcastId}",
        title = "FOCUS 방송 리포트",
        broadcastId = seed.broadcastId,
        analysisJobId = analysisJobId,
        analysisStatus = analysisStatus,
        completedAtMillis = seed.completedAtMillis,
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
        contentRatios = defaultContentRatios(safeDuration),
        recordingFilePath = seed.recordingFilePath,
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
        summary = summary,
        strengths = strengths,
        weaknesses = weaknesses,
        actionItems = actionItems,
        viewerPeakInsight = peakViewerInsightOrNull(),
        faceStatistics = BroadcastFaceStatistics(
            totalReplacedFaceCount = replacedFaceCount,
            maxSimultaneousCrowdCount = maxCrowdCount,
        ),
        contentRatios = contentRatios.map {
            BroadcastContentRatio(
                contentType = it.contentType,
                percentage = it.percentage,
                durationSec = it.durationSec,
            )
        },
    )
}

internal fun CompletedBroadcastReport.toCompleteAnalysisJobRequest(
    seed: CompletedBroadcastReportSeed,
): CompleteBroadcastAnalysisJob {
    return CompleteBroadcastAnalysisJob(
        storageUrl = seed.recordingFilePath,
        durationSec = durationSec,
        resolutionWidth = seed.resolutionWidth,
        resolutionHeight = seed.resolutionHeight,
        fileSizeBytes = seed.recordingFilePath.toFileSizeOrNull(),
        summary = summary,
        strengths = strengths,
        weaknesses = weaknesses,
        actionItems = actionItems,
        viewerPeakInsight = peakViewerInsightOrNull(),
        faceStatistics = BroadcastFaceStatistics(
            totalReplacedFaceCount = replacedFaceCount,
            maxSimultaneousCrowdCount = maxCrowdCount,
        ),
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
    val fallback = buildLocalCompletedBroadcastReport(
        seed = seed,
        analysisStatus = latestJob?.jobStatus ?: BroadcastAnalysisStatus.PROCESSING,
        analysisJobId = latestJob?.analysisJobId,
    )
    val latest = latestReport ?: return fallback.copy(
        analysisStatus = latestJob?.jobStatus ?: BroadcastAnalysisStatus.PROCESSING,
        highlightCount = maxOf(highlightCount, highlights.size, fallback.highlightCount),
        highlightMoments = highlights,
    )

    return CompletedBroadcastReport(
        reportId = latest.aiReportId,
        title = latest.title.ifBlank { fallback.title },
        broadcastId = broadcastId,
        analysisJobId = latestJob?.analysisJobId,
        analysisStatus = latestJob?.jobStatus ?: BroadcastAnalysisStatus.SUCCEEDED,
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

private fun defaultContentRatios(durationSec: Int): List<CompletedBroadcastContentRatio> {
    return listOf(
        Triple("토크", 41.0, 0.41),
        Triple("이동", 27.0, 0.27),
        Triple("실시간 상호작용", 19.0, 0.19),
        Triple("대기/전환", 13.0, 0.13),
    ).map { (title, percentage, ratio) ->
        CompletedBroadcastContentRatio(
            contentType = title,
            percentage = percentage,
            durationSec = max((durationSec * ratio).toInt(), 1),
        )
    }
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
