package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAiReport
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisJob
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastFaceStatistics
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastMediaAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostBroadcastReportModelsTest {

    @Test
    fun `latestReport가 없으면 processing placeholder만 유지한다`() {
        val seed = CompletedBroadcastReportSeed(
            broadcastId = "broadcast-1",
            durationSec = 95,
            ownerCount = 2,
            recordingFilePath = null,
        )
        val result = BroadcastAnalysisResult(
            broadcastId = "broadcast-1",
            latestJob = BroadcastAnalysisJob(
                analysisJobId = "job-1",
                broadcastId = "broadcast-1",
                jobType = "FULL_SUMMARY",
                jobStatus = BroadcastAnalysisStatus.PROCESSING,
                completedAt = null,
                errorMessage = null,
                createdAt = "2026-05-21T00:00:00",
                mediaAsset = BroadcastMediaAsset(
                    mediaAssetId = "asset-1",
                    assetType = "ANALYSIS_MP4",
                    storageProvider = "LOCAL_FILE",
                    storageKey = "android/broadcast-1/analysis.mp4",
                    storageUrl = null,
                    durationSec = 95,
                    resolutionWidth = 1280,
                    resolutionHeight = 720,
                    fileSizeBytes = 2048,
                    createdAt = "2026-05-21T00:00:00",
                ),
            ),
            latestReport = null,
            highlightCount = 3,
        )

        val report = result.toCompletedBroadcastReport(
            seed = seed,
            highlights = listOf(
                CompletedBroadcastHighlightMoment(
                    timeLabel = "00:12",
                    title = "하이라이트",
                    description = "반응이 올라온 구간",
                ),
            ),
        )

        assertEquals(BroadcastAnalysisStatus.PROCESSING, report.analysisStatus)
        assertFalse(report.hasFinalAnalysis)
        assertTrue(report.summary.isBlank())
        assertTrue(report.strengths.isEmpty())
        assertTrue(report.weaknesses.isEmpty())
        assertTrue(report.actionItems.isEmpty())
        assertEquals(3, report.highlightCount)
        assertEquals(1, report.highlightMoments.size)
    }

    @Test
    fun `placeholder 성격의 서버 리포트는 아직 완료로 표시하지 않는다`() {
        val seed = CompletedBroadcastReportSeed(
            broadcastId = "broadcast-1",
            durationSec = 49,
            ownerCount = 0,
            recordingFilePath = null,
        )
        val result = BroadcastAnalysisResult(
            broadcastId = "broadcast-1",
            latestJob = BroadcastAnalysisJob(
                analysisJobId = "job-1",
                broadcastId = "broadcast-1",
                jobType = "FULL_SUMMARY",
                jobStatus = BroadcastAnalysisStatus.SUCCEEDED,
                completedAt = "2026-05-21T06:24:00",
                errorMessage = null,
                createdAt = "2026-05-21T06:24:00",
                mediaAsset = BroadcastMediaAsset(
                    mediaAssetId = "asset-1",
                    assetType = "ANALYSIS_MP4",
                    storageProvider = "LOCAL_FILE",
                    storageKey = "android/broadcast-1/analysis.mp4",
                    storageUrl = null,
                    durationSec = 49,
                    resolutionWidth = 1280,
                    resolutionHeight = 720,
                    fileSizeBytes = 2048,
                    createdAt = "2026-05-21T06:24:00",
                ),
            ),
            latestReport = BroadcastAiReport(
                aiReportId = "report-1",
                reportType = "POST_STREAM_SUMMARY",
                title = "'포커스방송' 방송 요약",
                summary = "총 0분 분량의 방송 분석 데이터를 등록했습니다. 현재 기준으로 확인 가능한 아바타 치환 세션은 21건이며, 최대 시청자 수 데이터는 아직 집계되지 않았습니다. 카테고리 비율 분석 데이터는 아직 비어 있습니다.",
                strengths = listOf(
                    "분석 가능한 방송 길이(0분) 정보가 확보되었습니다.",
                    "분석용 MP4가 등록되어 후속 Gemini 요약 파이프라인을 바로 연결할 수 있습니다.",
                ),
                weaknesses = listOf(
                    "시청자 피크 데이터가 아직 집계되지 않았습니다.",
                    "카테고리 비율 분석 데이터가 아직 비어 있습니다.",
                ),
                actionItems = listOf(
                    "방송 중 시청자 수 polling 결과를 연결해 최대 반응 시점을 함께 제공해 주세요.",
                    "카테고리 snapshot 집계를 연결해 이동, 토크, 게임 같은 비율을 함께 보여주세요.",
                ),
                viewerPeakInsight = null,
                faceStatistics = BroadcastFaceStatistics(
                    totalReplacedFaceCount = 21,
                    maxSimultaneousCrowdCount = 0,
                ),
                contentRatios = emptyList(),
                createdAt = "2026-05-21T06:24:00",
            ),
            highlightCount = 0,
        )

        val report = result.toCompletedBroadcastReport(seed, emptyList())

        assertEquals(BroadcastAnalysisStatus.PROCESSING, report.analysisStatus)
        assertFalse(report.hasFinalAnalysis)
        assertTrue(report.summary.isBlank())
        assertTrue(report.strengths.isEmpty())
        assertTrue(report.actionItems.isEmpty())
    }
}
