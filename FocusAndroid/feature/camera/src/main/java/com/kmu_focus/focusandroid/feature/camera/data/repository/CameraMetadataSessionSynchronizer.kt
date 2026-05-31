package com.kmu_focus.focusandroid.feature.camera.data.repository

import android.content.Context
import android.util.Log
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.media.di.IoDispatcher
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.core.metadata.data.local.JsonMetadataRepository
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.mapper.MetadataMapper
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Singleton
class CameraMetadataSessionSynchronizer @Inject constructor(
    private val metadataRepositoryProvider: Provider<MetadataRepository>,
    realTimeRecorder: RealTimeRecorder,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    companion object {
        private const val TAG = "CameraMetadataSync"
        private const val MAX_BROADCAST_METADATA_FACE_COUNT = 5
        private const val UNSET_PTS_BASE_US = Long.MIN_VALUE
        /** 영상 분석 path 의 metadata 저장 위치와 동일한 base dir 의 sub-folder. */
        private const val METADATA_DUMP_BASE_DIR = "metadata"
        /** 인코더 PTS base 확정 전까지 보류 가능한 metadata frame 개수 상한. 60fps x 2s 가정. */
        private const val MAX_PENDING_METADATA_FRAMES = 120
    }

    private data class PendingMetadataFrame(
        val processedFrame: ProcessedFrame,
        val frameTimestampUs: Long,
    )

    private data class MetadataDispatchContext(
        val sessionId: String,
        val coordinateSpace: MetadataMapper.CoordinateSpace?,
        val frameWidth: Int,
        val frameHeight: Int,
        val rotation: Int = 0,
        val mirrored: Boolean = false,
    )

    private val metadataScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val metadataJobs = mutableSetOf<Job>()
    private val metadataEnqueuedCount = AtomicLong(0L)
    private val metadataDroppedBeforeBaseCount = AtomicLong(0L)
    private val metadataDroppedPendingOverflowCount = AtomicLong(0L)
    private val metadataJobsLock = Any()
    private val metadataStateLock = Any()
    private val pendingMetadataLock = Any()
    private val liveMetadataLock = Any()
    private val pendingMetadataFrames = LinkedHashMap<Long, PendingMetadataFrame>()
    private var pendingLiveMetadata: FrameMetadata? = null
    private var liveMetadataSenderJob: Job? = null
    private var metadataRepository: MetadataRepository? = null
    private var metadataSessionId: String? = null
    private var metadataEnabled = false
    private var metadataFrameIndex = 0
    private var encoderPtsBaseUs: Long = UNSET_PTS_BASE_US
    private var sourceFrameWidth = 0
    private var sourceFrameHeight = 0
    private var broadcastSourceWidth = 0
    private var broadcastSourceHeight = 0
    private var limitBroadcastMetadataFaces = false
    private val broadcastMetadataTrackingSlotAllocator =
        BroadcastMetadataTrackingSlotAllocator(MAX_BROADCAST_METADATA_FACE_COUNT)
    private var metadataDumpRepository: JsonMetadataRepository? = null

    init {
        realTimeRecorder.onVideoPtsBaseSet = ::setEncoderPtsBaseUs
        realTimeRecorder.onVideoSampleWritten = ::onVideoSampleWritten
    }

    fun updateSourceFrameSize(
        width: Int,
        height: Int,
    ) {
        synchronized(metadataStateLock) {
            sourceFrameWidth = width.coerceAtLeast(0)
            sourceFrameHeight = height.coerceAtLeast(0)
        }
    }

    fun setBroadcastSourceOverride(
        width: Int,
        height: Int,
    ) {
        val resolvedWidth = width.coerceAtLeast(0)
        val resolvedHeight = height.coerceAtLeast(0)
        synchronized(metadataStateLock) {
            broadcastSourceWidth = resolvedWidth
            broadcastSourceHeight = resolvedHeight
        }
        Log.i(TAG, "setBroadcastSourceOverride = ${resolvedWidth}x${resolvedHeight}")
    }

    fun nextFrameIndex(): Int? {
        return synchronized(metadataStateLock) {
            if (metadataEnabled) {
                metadataFrameIndex++
                metadataFrameIndex - 1
            } else {
                null
            }
        }
    }

    fun enqueueMetadataFrame(
        frame: ProcessedFrame,
        frameTimestampUs: Long,
    ) {
        if (frame.frameExport == null) return
        bufferPendingMetadataFrame(frame, frameTimestampUs)
    }

    fun startMetadataSession(
        repository: MetadataRepository? = null,
        sessionId: String? = null,
    ) {
        openMetadataDumpFile(sessionId)
        synchronized(metadataStateLock) {
            metadataEnabled = true
            metadataFrameIndex = 0
            metadataSessionId = sessionId
            metadataRepository = repository
            encoderPtsBaseUs = UNSET_PTS_BASE_US
            limitBroadcastMetadataFaces = repository != null && sessionId != null
            broadcastMetadataTrackingSlotAllocator.reset()
        }
        synchronized(pendingMetadataLock) {
            pendingMetadataFrames.clear()
        }
        synchronized(liveMetadataLock) {
            pendingLiveMetadata = null
            liveMetadataSenderJob = null
        }
        metadataEnqueuedCount.set(0L)
        metadataDroppedBeforeBaseCount.set(0L)
        metadataDroppedPendingOverflowCount.set(0L)
        Log.i(
            TAG,
            "==== METADATA SESSION START ====\n" +
                "  repo                    = ${repository?.javaClass?.simpleName}\n" +
                "  startedAt               = ${System.currentTimeMillis()}\n" +
                "================================",
        )
    }

    suspend fun closeMetadataSession() {
        synchronized(metadataStateLock) {
            metadataEnabled = false
        }
        synchronized(pendingMetadataLock) {
            pendingMetadataFrames.clear()
        }

        val jobs = synchronized(metadataJobsLock) { metadataJobs.toList() }
        jobs.joinAll()

        closeMetadataDumpFile()

        val repository = synchronized(metadataStateLock) {
            val current = metadataRepository
            metadataRepository = null
            metadataSessionId = null
            metadataFrameIndex = 0
            encoderPtsBaseUs = UNSET_PTS_BASE_US
            limitBroadcastMetadataFaces = false
            broadcastMetadataTrackingSlotAllocator.reset()
            current
        }
        repository?.close()
    }

    fun setEncoderPtsBaseUs(baseUs: Long) {
        synchronized(metadataStateLock) {
            encoderPtsBaseUs = baseUs
        }
        val droppedBeforeBase = dropPendingMetadataBefore(baseUs)
        val pendingSize = synchronized(pendingMetadataLock) { pendingMetadataFrames.size }
        Log.i(
            TAG,
            "setEncoderPtsBaseUs base=$baseUs bufferedBeforeBase=${metadataDroppedBeforeBaseCount.get()} " +
                "pending=$pendingSize droppedBeforeBase=$droppedBeforeBase " +
                "overflowDropped=${metadataDroppedPendingOverflowCount.get()}",
        )
        // metadata는 base 확정만으로 전송하지 않는다.
        // 실제 비디오 sample write 콜백이 온 PTS만 전송해야 영상에 없는 metadata가 생기지 않는다.
    }

    internal fun onVideoSampleWritten(
        rawPtsUs: Long,
        rebasedPtsUs: Long,
    ) {
        if (rawPtsUs < 0L || rebasedPtsUs < 0L) return
        val isMetadataEnabled = synchronized(metadataStateLock) { metadataEnabled }
        if (!isMetadataEnabled) return

        val pending = takePendingMetadataForSample(rawPtsUs)
        if (pending == null) {
            Log.w(
                TAG,
                "video sample has no matching metadata rawPtsUs=$rawPtsUs rebasedPtsUs=$rebasedPtsUs " +
                    "pending=${synchronized(pendingMetadataLock) { pendingMetadataFrames.size }}",
            )
            return
        }
        dispatchMetadataFrame(
            frame = pending.processedFrame,
            frameTimestampUs = rawPtsUs,
            metadataPtsUs = rebasedPtsUs,
        )
    }

    private fun openMetadataDumpFile(sessionId: String?): String? {
        closeMetadataDumpFile()
        val safeId = sessionId?.takeIf { it.isNotBlank() } ?: return null
        val baseDir = resolveMetadataBaseDir()
        val dir = File(baseDir, safeId).apply { mkdirs() }
        return try {
            val repository = JsonMetadataRepository(outputDir = dir)
            synchronized(metadataStateLock) {
                metadataDumpRepository = repository
            }
            dir.absolutePath
        } catch (throwable: Throwable) {
            Log.w(TAG, "metadata dump open 실패", throwable)
            null
        }
    }

    private fun closeMetadataDumpFile() {
        val repository = synchronized(metadataStateLock) {
            val current = metadataDumpRepository
            metadataDumpRepository = null
            current
        }
        if (repository == null) return
        metadataScope.launch {
            runCatching { repository.close() }.onFailure {
                Log.w(TAG, "metadata dump close 실패", it)
            }
            Log.i(TAG, "metadata dump finalized")
        }
    }

    /**
     * 영상 분석 path 의 metadata output dir 과 동일한 base 디렉토리.
     * 민감한 얼굴 메타데이터는 백업되지 않는 앱 내부 저장소에만 기록한다.
     */
    private fun resolveMetadataBaseDir(): File {
        return File(context.noBackupFilesDir, METADATA_DUMP_BASE_DIR).apply { mkdirs() }
    }

    private fun bufferPendingMetadataFrame(
        frame: ProcessedFrame,
        frameTimestampUs: Long,
    ) {
        val baseUsSnapshot = synchronized(metadataStateLock) { encoderPtsBaseUs }
        val overflowed = synchronized(pendingMetadataLock) {
            pendingMetadataFrames[frameTimestampUs] = PendingMetadataFrame(frame, frameTimestampUs)
            var dropped = false
            while (pendingMetadataFrames.size > MAX_PENDING_METADATA_FRAMES) {
                val iterator = pendingMetadataFrames.entries.iterator()
                if (!iterator.hasNext()) break
                iterator.next()
                iterator.remove()
                dropped = true
            }
            dropped
        }
        val buffered = if (baseUsSnapshot == UNSET_PTS_BASE_US) {
            metadataDroppedBeforeBaseCount.incrementAndGet()
        } else {
            metadataDroppedBeforeBaseCount.get()
        }
        if (overflowed) {
            val dropped = metadataDroppedPendingOverflowCount.incrementAndGet()
            if (dropped == 1L || dropped % 30L == 0L) {
                Log.w(TAG, "pendingMetadata overflow drop #$dropped (cap=$MAX_PENDING_METADATA_FRAMES)")
            }
        }
        if (baseUsSnapshot == UNSET_PTS_BASE_US && (buffered == 1L || buffered % 30L == 0L)) {
            Log.w(
                TAG,
                "bufferMetadata before encoder pts base #$buffered " +
                    "pending=${synchronized(pendingMetadataLock) { pendingMetadataFrames.size }}",
            )
        }
    }

    private fun takePendingMetadataForSample(rawPtsUs: Long): PendingMetadataFrame? {
        return synchronized(pendingMetadataLock) {
            val iterator = pendingMetadataFrames.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key >= rawPtsUs) {
                    break
                }
                iterator.remove()
            }
            pendingMetadataFrames.remove(rawPtsUs)
        }
    }

    private fun dropPendingMetadataBefore(rawPtsUs: Long): Int {
        var dropped = 0
        synchronized(pendingMetadataLock) {
            val iterator = pendingMetadataFrames.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key >= rawPtsUs) {
                    break
                }
                iterator.remove()
                dropped++
            }
        }
        return dropped
    }

    private fun dispatchMetadataFrame(
        frame: ProcessedFrame,
        frameTimestampUs: Long,
        metadataPtsUs: Long,
    ) {
        if (metadataPtsUs < 0L) return
        val frameExport = frame.frameExport ?: return
        val ptsBaseUs = frameTimestampUs - metadataPtsUs
        val dispatchContext = synchronized(metadataStateLock) {
            val resolvedSessionId = metadataSessionId
                ?: UUID.randomUUID().toString().also { metadataSessionId = it }
            val activeSourceWidth = if (broadcastSourceWidth > 0) broadcastSourceWidth else sourceFrameWidth
            val activeSourceHeight = if (broadcastSourceHeight > 0) broadcastSourceHeight else sourceFrameHeight
            val resolvedCoordinateSpace = MetadataMapper.CoordinateSpace(
                analysisWidth = frame.frameWidth,
                analysisHeight = frame.frameHeight,
                sourceWidth = activeSourceWidth,
                sourceHeight = activeSourceHeight,
            ).takeIf(MetadataMapper.CoordinateSpace::isValid)
            MetadataDispatchContext(
                sessionId = resolvedSessionId,
                coordinateSpace = resolvedCoordinateSpace,
                frameWidth = activeSourceWidth,
                frameHeight = activeSourceHeight,
            )
        }

        val dropStats = MetadataMapper.DropStats()
        val rawCoeffNullCount = frameExport.faces.count {
            it.idCoeffs == null || it.expCoeffs == null || it.pose == null
        }
        val facePayloads = frameExport.faces.map { face ->
            MetadataMapper.FaceExportPayload(
                trackingId = face.trackingId,
                bbox = face.bbox,
                idCoeffs = face.idCoeffs,
                expCoeffs = face.expCoeffs,
                pose = face.pose,
                extraCoeffs = face.extraCoeffs,
                isOwner = face.isOwner,
            )
        }
        val limitedFacePayloads = if (limitBroadcastMetadataFaces) {
            limitBroadcastMetadataFacesForStreaming(
                faces = facePayloads,
                maxFaceCount = MAX_BROADCAST_METADATA_FACE_COUNT,
            )
        } else {
            facePayloads
        }
        val remappedFacePayloads = if (limitBroadcastMetadataFaces) {
            synchronized(metadataStateLock) {
                broadcastMetadataTrackingSlotAllocator.remapForTransmission(limitedFacePayloads)
            }
        } else {
            limitedFacePayloads
        }
        val faceCapDroppedCount =
            facePayloads.count { it.isOwner == false } - limitedFacePayloads.count { it.isOwner == false }
        val metadata = MetadataMapper.mapFrame(
            sessionId = dispatchContext.sessionId,
            timestampSeconds = frameExport.timestamp,
            faces = remappedFacePayloads,
            coordinateSpace = dispatchContext.coordinateSpace,
            ptsBaseUs = ptsBaseUs,
            frameWidth = dispatchContext.frameWidth,
            frameHeight = dispatchContext.frameHeight,
            rotation = dispatchContext.rotation,
            mirrored = dispatchContext.mirrored,
            overrideTimestampUs = frameTimestampUs,
            dropStats = dropStats,
        )
        val enqueued = metadataEnqueuedCount.incrementAndGet()
        if (enqueued == 1L || enqueued % 60L == 0L) {
            val activeSourceWidth = if (broadcastSourceWidth > 0) broadcastSourceWidth else sourceFrameWidth
            val activeSourceHeight = if (broadcastSourceHeight > 0) broadcastSourceHeight else sourceFrameHeight
            Log.i(
                TAG,
                "enqueueMetadata #$enqueued rawFaces=${frameExport.faces.size} sendFaces=${metadata.faces.size} " +
                    "faceCapDropped=$faceCapDroppedCount " +
                    "drop[$dropStats rawCoeffNull=$rawCoeffNullCount] " +
                    "analysis=${frame.frameWidth}x${frame.frameHeight} " +
                    "source=${activeSourceWidth}x${activeSourceHeight}" +
                    (if (broadcastSourceWidth > 0) "(output-frame)" else "(source-frame)"),
            )
        }

        val dumpRepository = synchronized(metadataStateLock) { metadataDumpRepository }
        if (dumpRepository != null) {
            launchMetadataJob { dumpRepository.sendFrame(metadata) }
        }

        enqueueLiveMetadataFrame(metadata)
    }

    private fun enqueueLiveMetadataFrame(metadata: FrameMetadata) {
        val jobToTrack = synchronized(liveMetadataLock) {
            if (liveMetadataSenderJob?.isActive == true) {
                pendingLiveMetadata = metadata
                null
            } else {
                pendingLiveMetadata = null
                metadataScope.launch { drainLiveMetadataFrames(firstMetadata = metadata) }
                    .also { liveMetadataSenderJob = it }
            }
        }
        jobToTrack?.let(::trackMetadataJob)
    }

    private suspend fun drainLiveMetadataFrames(firstMetadata: FrameMetadata) {
        var nextMetadata: FrameMetadata? = firstMetadata
        while (true) {
            val metadata = nextMetadata ?: synchronized(liveMetadataLock) {
                val pending = pendingLiveMetadata
                if (pending == null) {
                    liveMetadataSenderJob = null
                    return
                }
                pendingLiveMetadata = null
                pending
            }
            nextMetadata = null
            runCatching {
                val repository = synchronized(metadataStateLock) {
                    metadataRepository ?: metadataRepositoryProvider.get().also {
                        metadataRepository = it
                    }
                }
                repository.sendFrame(metadata)
            }.onFailure { throwable ->
                Log.e(TAG, "metadata sendFrame failed", throwable)
            }
        }
    }

    private fun launchMetadataJob(block: suspend () -> Unit) {
        val job = metadataScope.launch {
            runCatching { block() }.onFailure { throwable ->
                Log.e(TAG, "metadata sendFrame failed", throwable)
            }
        }
        trackMetadataJob(job)
    }

    private fun trackMetadataJob(job: Job) {
        synchronized(metadataJobsLock) {
            metadataJobs += job
        }
        job.invokeOnCompletion {
            synchronized(metadataJobsLock) {
                metadataJobs -= job
            }
        }
    }
}

internal fun limitBroadcastMetadataFacesForStreaming(
    faces: List<MetadataMapper.FaceExportPayload>,
    maxFaceCount: Int,
): List<MetadataMapper.FaceExportPayload> {
    if (maxFaceCount <= 0) {
        return faces.filter { it.isOwner != false }
    }

    val nonOwnerFaces = faces.withIndex().filter { it.value.isOwner == false }
    if (nonOwnerFaces.size <= maxFaceCount) {
        return faces
    }

    val selectedIndices = nonOwnerFaces
        .sortedWith(
            compareByDescending<IndexedValue<MetadataMapper.FaceExportPayload>> {
                faceBoundingBoxArea(it.value.bbox)
            }.thenBy { it.index },
        )
        .take(maxFaceCount)
        .mapTo(linkedSetOf()) { it.index }

    return faces.filterIndexed { index, face ->
        face.isOwner != false || index in selectedIndices
    }
}

private fun faceBoundingBoxArea(bbox: IntArray): Long {
    if (bbox.size < 4) return 0L
    return bbox[2].toLong().coerceAtLeast(0L) * bbox[3].toLong().coerceAtLeast(0L)
}

internal class BroadcastMetadataTrackingSlotAllocator(
    private val maxSlots: Int,
) {
    private val slotByTrackId = linkedMapOf<Int, Int>()
    private val trackIdBySlot = IntArray(maxSlots) { UNASSIGNED_TRACK_ID }

    fun reset() {
        slotByTrackId.clear()
        trackIdBySlot.fill(UNASSIGNED_TRACK_ID)
    }

    fun remapForTransmission(
        faces: List<MetadataMapper.FaceExportPayload>,
    ): List<MetadataMapper.FaceExportPayload> {
        if (maxSlots <= 0) {
            return faces
        }

        val transmittedTrackIds = faces.asSequence()
            .filter { it.isOwner == false }
            .map { it.trackingId }
            .toSet()

        releaseInactiveSlots(activeTrackIds = transmittedTrackIds)

        return faces.map { face ->
            if (face.isOwner != false) {
                face
            } else {
                val slot = slotByTrackId[face.trackingId] ?: allocateSlot(face.trackingId)
                if (slot == null) {
                    face
                } else {
                    face.copy(trackingId = slot)
                }
            }
        }
    }

    private fun releaseInactiveSlots(activeTrackIds: Set<Int>) {
        val inactiveTrackIds = slotByTrackId.keys.filterNot(activeTrackIds::contains)
        inactiveTrackIds.forEach { trackId ->
            val slot = slotByTrackId.remove(trackId) ?: return@forEach
            if (slot in trackIdBySlot.indices) {
                trackIdBySlot[slot] = UNASSIGNED_TRACK_ID
            }
        }
    }

    private fun allocateSlot(trackId: Int): Int? {
        val freeSlot = trackIdBySlot.indexOfFirst { it == UNASSIGNED_TRACK_ID }
        if (freeSlot < 0) {
            return null
        }
        slotByTrackId[trackId] = freeSlot
        trackIdBySlot[freeSlot] = trackId
        return freeSlot
    }

    private companion object {
        private const val UNASSIGNED_TRACK_ID = -1
    }
}
