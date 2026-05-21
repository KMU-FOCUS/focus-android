package com.kmu_focus.focusandroid.feature.broadcast.data.mapper

import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastAiReportResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastAnalysisJobResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastAnalysisResultResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastHighlightCandidateResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastMediaAssetResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAiReport
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisJob
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastContentRatio
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastFaceStatistics
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastHighlightCandidate
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastMediaAsset
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastViewerPeakInsight

fun BroadcastAnalysisJobResponseDto.toEntity(): BroadcastAnalysisJob {
    return BroadcastAnalysisJob(
        analysisJobId = analysisJobId,
        broadcastId = broadcastId,
        jobType = jobType,
        jobStatus = jobStatus.toBroadcastAnalysisStatus(),
        completedAt = completedAt,
        errorMessage = errorMessage,
        createdAt = createdAt,
        mediaAsset = mediaAsset.toEntity(),
    )
}

fun BroadcastAnalysisResultResponseDto.toEntity(): BroadcastAnalysisResult {
    return BroadcastAnalysisResult(
        broadcastId = broadcastId,
        latestJob = latestJob?.toEntity(),
        latestReport = latestReport?.toEntity(),
        highlightCount = highlightCount,
    )
}

fun BroadcastHighlightCandidateResponseDto.toEntity(): BroadcastHighlightCandidate {
    return BroadcastHighlightCandidate(
        highlightCandidateId = highlightCandidateId,
        startSec = startSec,
        endSec = endSec,
        title = title,
        reason = reason,
        score = score,
        createdAt = createdAt,
    )
}

private fun BroadcastAiReportResponseDto.toEntity(): BroadcastAiReport {
    return BroadcastAiReport(
        aiReportId = aiReportId,
        reportType = reportType,
        title = title,
        summary = summary,
        strengths = strengths,
        weaknesses = weaknesses,
        actionItems = actionItems,
        viewerPeakInsight = viewerPeakInsight?.toEntity(),
        faceStatistics = BroadcastFaceStatistics(
            totalReplacedFaceCount = faceStatistics.totalReplacedFaceCount ?: 0,
            maxSimultaneousCrowdCount = faceStatistics.maxSimultaneousCrowdCount ?: 0,
        ),
        contentRatios = contentRatios.map {
            BroadcastContentRatio(
                contentType = it.contentType,
                percentage = it.percentage,
                durationSec = it.durationSec,
            )
        },
        createdAt = createdAt,
    )
}

private fun BroadcastMediaAssetResponseDto.toEntity(): BroadcastMediaAsset {
    return BroadcastMediaAsset(
        mediaAssetId = mediaAssetId,
        assetType = assetType,
        storageProvider = storageProvider,
        storageKey = storageKey,
        storageUrl = storageUrl,
        durationSec = durationSec,
        resolutionWidth = resolutionWidth,
        resolutionHeight = resolutionHeight,
        fileSizeBytes = fileSizeBytes,
        createdAt = createdAt,
    )
}

private fun com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.ViewerPeakInsightResponseDto.toEntity(): BroadcastViewerPeakInsight {
    return BroadcastViewerPeakInsight(
        peakViewerCount = peakViewerCount,
        occurredAt = occurredAt,
        sceneDescription = sceneDescription,
    )
}

private fun String.toBroadcastAnalysisStatus(): BroadcastAnalysisStatus {
    return when (this) {
        "SUCCEEDED" -> BroadcastAnalysisStatus.SUCCEEDED
        "FAILED" -> BroadcastAnalysisStatus.FAILED
        else -> BroadcastAnalysisStatus.PROCESSING
    }
}
