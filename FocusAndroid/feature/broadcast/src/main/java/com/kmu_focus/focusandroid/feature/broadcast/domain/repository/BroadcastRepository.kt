package com.kmu_focus.focusandroid.feature.broadcast.domain.repository

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisJob
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastAnalysisResult
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastHighlightCandidate
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastOutputMode
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.CompleteBroadcastAnalysisJob
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.CreateBroadcastAnalysisJob

interface BroadcastRepository {
    suspend fun createBroadcast(
        title: String,
        outputMode: BroadcastOutputMode = BroadcastOutputMode.CHZZK_RTMP,
    ): Result<Broadcast>

    suspend fun startBroadcast(broadcastId: String): Result<Broadcast>

    suspend fun stopBroadcast(broadcastId: String): Result<Broadcast>

    suspend fun getBroadcastList(
        page: Int,
        size: Int,
    ): Result<List<Broadcast>>

    suspend fun getBroadcastDetail(broadcastId: String): Result<Broadcast>

    suspend fun updateBroadcast(
        broadcastId: String,
        title: String,
    ): Result<Broadcast>

    suspend fun deleteBroadcast(broadcastId: String): Result<Unit>

    suspend fun sendStreamerHeartbeat(broadcastId: String): Result<Unit>

    suspend fun createAnalysisJob(
        broadcastId: String,
        request: CreateBroadcastAnalysisJob,
    ): Result<BroadcastAnalysisJob>

    suspend fun completeAnalysisJob(
        broadcastId: String,
        analysisJobId: String,
        request: CompleteBroadcastAnalysisJob,
    ): Result<BroadcastAnalysisJob>

    suspend fun getLatestAnalysis(broadcastId: String): Result<BroadcastAnalysisResult>

    suspend fun getHighlights(broadcastId: String): Result<List<BroadcastHighlightCandidate>>
}
