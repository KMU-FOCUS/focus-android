package com.kmu_focus.focusandroid.feature.camera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.core.ai.data.recognition.FaceAlignment
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.TrackLabelState
import com.kmu_focus.focusandroid.core.metadata.data.local.JsonMetadataRepository
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.mapper.MetadataMapper
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import com.kmu_focus.focusandroid.feature.camera.domain.entity.OwnerRegistrationResult
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraAnalysisRepository
import com.kmu_focus.focusandroid.core.media.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.media.di.IoDispatcher
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.core.media.domain.entity.PrivacyMode
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class CameraAnalysisRepositoryImpl @Inject constructor(
    private val frameProcessor: FrameProcessor,
    private val metadataRepositoryProvider: Provider<MetadataRepository>,
    private val ownerAdder: OwnerAdder,
    private val trackLabelState: TrackLabelState,
    private val embeddingExtractor: ArcFaceEmbeddingExtractor,
    realTimeRecorder: RealTimeRecorder,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CameraAnalysisRepository {

    companion object {
        private const val TAG = "CameraAnalysisRepo"
        private const val MAX_BROADCAST_METADATA_FACE_COUNT = 5
        private const val THUMBNAIL_QUALITY = 95
        private const val SNAPSHOT_DIR = "owner_snapshots"
        private const val UNSET_PTS_BASE_US = Long.MIN_VALUE
        /** 영상 분석 path 의 metadata 저장 위치와 동일한 base dir 의 sub-folder. */
        private const val METADATA_DUMP_BASE_DIR = "metadata"
        /** 인코더 PTS base 확정 전까지 보류 가능한 metadata frame 개수 상한. 60fps×2s 가정. */
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
    private var metadataDumpDir: File? = null

    init {
        realTimeRecorder.onVideoPtsBaseSet = { baseUs -> setEncoderPtsBaseUs(baseUs) }
        realTimeRecorder.onVideoSampleWritten = { rawPtsUs, rebasedPtsUs ->
            onVideoSampleWritten(
                rawPtsUs = rawPtsUs,
                rebasedPtsUs = rebasedPtsUs,
            )
        }
    }

    override fun setPrivacyMode(mode: PrivacyMode) {
        frameProcessor.setPrivacyMode(mode)
    }

    override fun updateSourceFrameSize(
        width: Int,
        height: Int,
    ) {
        synchronized(metadataStateLock) {
            sourceFrameWidth = width.coerceAtLeast(0)
            sourceFrameHeight = height.coerceAtLeast(0)
        }
    }

    override fun setBroadcastSourceOverride(
        width: Int,
        height: Int,
    ) {
        val w = width.coerceAtLeast(0)
        val h = height.coerceAtLeast(0)
        synchronized(metadataStateLock) {
            broadcastSourceWidth = w
            broadcastSourceHeight = h
        }
        Log.i(TAG, "setBroadcastSourceOverride = ${w}x${h}")
    }

    override fun registerOwnerFromFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        trackId: Int,
        processedFrame: ProcessedFrame,
    ): OwnerRegistrationResult {
        val faceIndex = processedFrame.trackingIds.indexOf(trackId)
        if (faceIndex < 0) {
            Log.w(TAG, "registerOwner: trackId=$trackId not found")
            return OwnerRegistrationResult(success = false)
        }

        val face = processedFrame.faces[faceIndex]
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        rgbaBuffer.rewind()
        bitmap.copyPixelsFromBuffer(rgbaBuffer)

        val rect = Rect(
            face.x.coerceIn(0, bitmap.width - 1),
            face.y.coerceIn(0, bitmap.height - 1),
            (face.x + face.width).coerceIn(1, bitmap.width),
            (face.y + face.height).coerceIn(1, bitmap.height),
        )
        if (rect.width() < 16 || rect.height() < 16) {
            bitmap.recycle()
            Log.w(TAG, "registerOwner: face too small")
            return OwnerRegistrationResult(success = false)
        }

        var crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        face.landmarks?.let { lm ->
            val aligned = FaceAlignment.alignFaceForRecognition(crop, lm, rect)
            if (aligned != crop) {
                crop.recycle()
                crop = aligned
            }
        }

        val embedding = embeddingExtractor.extractEmbedding(crop)
        if (embedding == null) {
            crop.recycle()
            bitmap.recycle()
            Log.w(TAG, "registerOwner: embedding extraction failed")
            return OwnerRegistrationResult(success = false)
        }

        val ownerId = ownerAdder.addOwnerFromEmbeddingWithOwnerId(embedding)
        val success = ownerId != null
        val thumbnailPath = if (success) saveFaceThumbnail(crop) else null

        if (success) {
            trackLabelState.markOwner(trackId)
            Log.i(
                TAG,
                "registerOwner: trackId=$trackId registered, ownerId=$ownerId, thumbnail=$thumbnailPath",
            )
        }

        crop.recycle()
        bitmap.recycle()
        return OwnerRegistrationResult(
            success = success,
            ownerId = ownerId,
            thumbnailPath = thumbnailPath,
        )
    }

    override fun removeOwner(
        ownerId: Int,
        trackId: Int,
        thumbnailPath: String?,
    ): Boolean {
        val removed = ownerAdder.removeOwner(ownerId)
        if (!removed) {
            return false
        }
        trackLabelState.removeTrack(trackId)
        thumbnailPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                runCatching { File(path).delete() }
                    .onFailure { throwable ->
                        Log.w(TAG, "removeOwner: thumbnail delete failed path=$path", throwable)
                    }
            }
        Log.i(TAG, "removeOwner: ownerId=$ownerId, trackId=$trackId removed")
        return true
    }

    override fun processFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        timestampUs: Long,
    ): ProcessedFrame {
        val frameIndex = synchronized(metadataStateLock) {
            if (metadataEnabled) {
                metadataFrameIndex++
                metadataFrameIndex - 1
            } else {
                null
            }
        }
        val processed = frameProcessor.process(
            rgbaBuffer = rgbaBuffer,
            width = width,
            height = height,
            timestampMs = timestampMs,
            frameIndex = frameIndex,
        )
        if (frameIndex != null) {
            enqueueMetadataFrame(processed, timestampUs)
        }
        return processed
    }

    override fun clearProcessingThreadCache() {
        frameProcessor.clearThreadLocalCache()
    }

    override fun resetSessionState() {
        ownerAdder.clearOwners()
        frameProcessor.resetSessionState()
    }

    override fun startMetadataSession() {
        beginMetadataSession(repository = null, sessionId = null)
    }

    override fun startMetadataSession(repository: MetadataRepository) {
        beginMetadataSession(repository = repository, sessionId = null)
    }

    override fun startMetadataSession(
        repository: MetadataRepository,
        sessionId: String,
    ) {
        beginMetadataSession(
            repository = repository,
            sessionId = sessionId,
        )
    }

    private fun beginMetadataSession(
        repository: MetadataRepository?,
        sessionId: String?,
    ) {
        val dumpPath = openMetadataDumpFile(sessionId)
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
                "  broadcastId(session_id) = $sessionId\n" +
                "  repo                    = ${repository?.javaClass?.simpleName}\n" +
                "  startedAt               = ${System.currentTimeMillis()}\n" +
                "  metadataDumpPath        = $dumpPath\n" +
                "================================",
        )
    }

    private fun openMetadataDumpFile(sessionId: String?): String? {
        closeMetadataDumpFile()
        val safeId = sessionId?.takeIf { it.isNotBlank() } ?: return null
        val baseDir = resolveMetadataBaseDir()
        val dir = File(baseDir, safeId).apply { mkdirs() }
        return try {
            val repo = JsonMetadataRepository(outputDir = dir)
            synchronized(metadataStateLock) {
                metadataDumpRepository = repo
                metadataDumpDir = dir
            }
            dir.absolutePath
        } catch (t: Throwable) {
            Log.w(TAG, "metadata dump open 실패: ${dir.absolutePath}", t)
            null
        }
    }

    private fun closeMetadataDumpFile() {
        val (repo, dir) = synchronized(metadataStateLock) {
            val r = metadataDumpRepository
            val d = metadataDumpDir
            metadataDumpRepository = null
            metadataDumpDir = null
            r to d
        }
        if (repo == null) return
        metadataScope.launch {
            runCatching { repo.close() }.onFailure {
                Log.w(TAG, "metadata dump close 실패", it)
            }
            Log.i(TAG, "metadata dump finalized: dir=${dir?.absolutePath}")
        }
    }

    /**
     * 영상 분석 path 의 [MetadataModule.MetadataOutputDir] 와 동일한 base 디렉토리.
     * 외부 files dir / Documents / metadata. (영상 처리 결과 metadata 들과 같은 위치)
     */
    private fun resolveMetadataBaseDir(): File {
        val externalDocuments = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        return File(externalDocuments ?: context.filesDir, METADATA_DUMP_BASE_DIR).apply { mkdirs() }
    }

    override suspend fun closeMetadataSession() {
        synchronized(metadataStateLock) {
            metadataEnabled = false
        }
        synchronized(pendingMetadataLock) {
            pendingMetadataFrames.clear()
        }

        val jobs = synchronized(metadataJobsLock) { metadataJobs.toList() }
        jobs.joinAll()

        closeMetadataDumpFile()

        val repo = synchronized(metadataStateLock) {
            val current = metadataRepository
            metadataRepository = null
            metadataSessionId = null
            metadataFrameIndex = 0
            encoderPtsBaseUs = UNSET_PTS_BASE_US
            limitBroadcastMetadataFaces = false
            broadcastMetadataTrackingSlotAllocator.reset()
            current
        }
        repo?.close()
    }

    override fun setEncoderPtsBaseUs(baseUs: Long) {
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

    private fun enqueueMetadataFrame(frame: ProcessedFrame, frameTimestampUs: Long) {
        if (frame.frameExport == null) return
        bufferPendingMetadataFrame(frame, frameTimestampUs)
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

    private fun bufferPendingMetadataFrame(frame: ProcessedFrame, frameTimestampUs: Long) {
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
            val firstBbox = metadata.faces.firstOrNull()?.bbox?.let {
                "[${it.x},${it.y},${it.width}x${it.height}]"
            } ?: "<none>"
            val activeSrcW = if (broadcastSourceWidth > 0) broadcastSourceWidth else sourceFrameWidth
            val activeSrcH = if (broadcastSourceHeight > 0) broadcastSourceHeight else sourceFrameHeight
            Log.i(
                TAG,
                "enqueueMetadata #$enqueued session=${dispatchContext.sessionId} pts_us=${metadata.ptsUs} " +
                    "rawUs=$frameTimestampUs base=$ptsBaseUs delta=${metadata.ptsUs} " +
                    "rawFaces=${frameExport.faces.size} sendFaces=${metadata.faces.size} " +
                    "faceCapDropped=$faceCapDroppedCount " +
                    "drop[$dropStats rawCoeffNull=$rawCoeffNullCount] " +
                    "analysis=${frame.frameWidth}x${frame.frameHeight} " +
                    "source=${activeSrcW}x${activeSrcH}" +
                    (if (broadcastSourceWidth > 0) "(output-frame)" else "(source-frame)") +
                    " firstBbox=$firstBbox",
            )
        }

        val dumpRepo = synchronized(metadataStateLock) { metadataDumpRepository }
        if (dumpRepo != null) {
            launchMetadataJob { dumpRepo.sendFrame(metadata) }
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
                val repo = synchronized(metadataStateLock) {
                    metadataRepository ?: metadataRepositoryProvider.get().also {
                        metadataRepository = it
                    }
                }
                repo.sendFrame(metadata)
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

    private fun saveFaceThumbnail(faceBitmap: Bitmap): String? = runCatching {
        val dir = File(context.cacheDir, SNAPSHOT_DIR).apply { mkdirs() }
        val file = File(dir, "owner_face_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            faceBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
        }
        file.absolutePath
    }.getOrNull()
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
