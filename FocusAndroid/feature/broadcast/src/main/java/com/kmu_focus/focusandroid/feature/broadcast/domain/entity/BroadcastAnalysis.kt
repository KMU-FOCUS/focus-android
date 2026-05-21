package com.kmu_focus.focusandroid.feature.broadcast.domain.entity

enum class BroadcastAnalysisStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED,
}

data class BroadcastContentRatio(
    val contentType: String,
    val percentage: Double,
    val durationSec: Int,
)

data class BroadcastViewerPeakInsight(
    val peakViewerCount: Int,
    val occurredAt: String? = null,
    val sceneDescription: String? = null,
)

data class BroadcastFaceStatistics(
    val totalReplacedFaceCount: Int,
    val maxSimultaneousCrowdCount: Int,
)

data class BroadcastMediaAsset(
    val mediaAssetId: String,
    val assetType: String,
    val storageProvider: String,
    val storageKey: String,
    val storageUrl: String? = null,
    val durationSec: Int? = null,
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
    val fileSizeBytes: Long? = null,
    val createdAt: String,
)

data class BroadcastAnalysisJob(
    val analysisJobId: String,
    val broadcastId: String,
    val jobType: String,
    val jobStatus: BroadcastAnalysisStatus,
    val completedAt: String? = null,
    val errorMessage: String? = null,
    val createdAt: String,
    val mediaAsset: BroadcastMediaAsset,
)

data class BroadcastAiReport(
    val aiReportId: String,
    val reportType: String,
    val title: String,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<String>,
    val viewerPeakInsight: BroadcastViewerPeakInsight? = null,
    val faceStatistics: BroadcastFaceStatistics,
    val contentRatios: List<BroadcastContentRatio>,
    val createdAt: String,
)

data class BroadcastAnalysisResult(
    val broadcastId: String,
    val latestJob: BroadcastAnalysisJob? = null,
    val latestReport: BroadcastAiReport? = null,
    val highlightCount: Int,
)

data class BroadcastHighlightCandidate(
    val highlightCandidateId: String,
    val startSec: Int,
    val endSec: Int,
    val title: String,
    val reason: String,
    val score: Double,
    val createdAt: String,
)
