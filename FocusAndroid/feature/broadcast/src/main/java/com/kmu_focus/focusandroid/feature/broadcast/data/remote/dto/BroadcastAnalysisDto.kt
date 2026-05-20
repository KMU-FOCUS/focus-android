package com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto

data class CreateBroadcastAnalysisJobRequestDto(
    val assetType: String,
    val jobType: String,
    val storageProvider: String,
    val storageKey: String,
    val storageUrl: String? = null,
    val durationSec: Int? = null,
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
    val fileSizeBytes: Long? = null,
    val summary: String? = null,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<String>,
    val viewerPeakInsight: ViewerPeakInsightRequestDto? = null,
    val faceStatistics: FaceStatisticsRequestDto? = null,
    val contentRatios: List<ContentRatioRequestDto>,
)

data class CompleteBroadcastAnalysisJobRequestDto(
    val storageUrl: String? = null,
    val durationSec: Int? = null,
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
    val fileSizeBytes: Long? = null,
    val summary: String? = null,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<String>,
    val viewerPeakInsight: ViewerPeakInsightRequestDto? = null,
    val faceStatistics: FaceStatisticsRequestDto? = null,
    val contentRatios: List<ContentRatioRequestDto>,
)

data class ViewerPeakInsightRequestDto(
    val peakViewerCount: Int,
    val occurredAt: String? = null,
    val sceneDescription: String? = null,
)

data class FaceStatisticsRequestDto(
    val totalReplacedFaceCount: Int,
    val maxSimultaneousCrowdCount: Int,
)

data class ContentRatioRequestDto(
    val contentType: String,
    val percentage: Double,
    val durationSec: Int,
)

data class BroadcastAnalysisJobResponseDto(
    val analysisJobId: String,
    val broadcastId: String,
    val jobType: String,
    val jobStatus: String,
    val completedAt: String? = null,
    val errorMessage: String? = null,
    val createdAt: String,
    val mediaAsset: BroadcastMediaAssetResponseDto,
)

data class BroadcastMediaAssetResponseDto(
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

data class BroadcastAnalysisResultResponseDto(
    val broadcastId: String,
    val latestJob: BroadcastAnalysisJobResponseDto? = null,
    val latestReport: BroadcastAiReportResponseDto? = null,
    val highlightCount: Int,
)

data class BroadcastAiReportResponseDto(
    val aiReportId: String,
    val reportType: String,
    val title: String,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<String>,
    val viewerPeakInsight: ViewerPeakInsightResponseDto? = null,
    val faceStatistics: FaceStatisticsResponseDto,
    val contentRatios: List<ContentRatioResponseDto>,
    val createdAt: String,
)

data class ViewerPeakInsightResponseDto(
    val peakViewerCount: Int,
    val occurredAt: String? = null,
    val sceneDescription: String? = null,
)

data class FaceStatisticsResponseDto(
    val totalReplacedFaceCount: Int? = 0,
    val maxSimultaneousCrowdCount: Int? = 0,
)

data class ContentRatioResponseDto(
    val contentType: String,
    val percentage: Double,
    val durationSec: Int,
)

data class BroadcastHighlightCandidateResponseDto(
    val highlightCandidateId: String,
    val startSec: Int,
    val endSec: Int,
    val title: String,
    val reason: String,
    val score: Double,
    val createdAt: String,
)
