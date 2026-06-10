package com.kmu_focus.focusandroid.core.media.data.processor

import android.graphics.Bitmap
import android.graphics.Rect
import com.kmu_focus.focusandroid.core.ai.data.recognition.FaceAlignment
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.core.ai.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.core.ai.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.core.ai.domain.detector.Facial3DMMExtractor
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.TrackLabelState
import com.kmu_focus.focusandroid.core.ai.domain.detector.tracking.FaceTracker
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.media.domain.entity.FaceExport
import com.kmu_focus.focusandroid.core.media.domain.entity.FrameExport
import com.kmu_focus.focusandroid.core.media.domain.entity.PrivacyMode
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor

class FrameProcessor @Inject constructor(
    private val faceDetector: FaceDetector,
    private val config: DetectionConfig,
    private val facial3DMMExtractor: Facial3DMMExtractor,
    private val faceTracker: FaceTracker,
    private val trackLabelState: TrackLabelState,
    private val embeddingExtractor: ArcFaceEmbeddingExtractor
) {
    private data class RecognitionCropWindow(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    companion object {
        private const val MAX_3DMM_FACE_COUNT = 5
        private const val RECOGNITION_CROP_SCALE = 2.0f
    }

    // GL/트랜스코드 루프에서 매 프레임 Bitmap 신규 할당을 피하기 위해 스레드별 재사용한다.
    private val frameBitmapHolder = ThreadLocal<Bitmap>()
    private val recognitionQueue = linkedSetOf<Int>()

    @Volatile
    private var privacyMode: PrivacyMode = PrivacyMode.Avatar

    fun setPrivacyMode(mode: PrivacyMode) {
        privacyMode = mode
    }

    fun process(bitmap: Bitmap, timestampMs: Long, frameIndex: Int? = null): ProcessedFrame {
        val activePrivacyMode = privacyMode
        val faces = faceDetector.detectFaces(bitmap)
            .filter { it.confidence >= config.confidenceThreshold }

        val shouldExtract3dmm = activePrivacyMode == PrivacyMode.Avatar
        val tdmmCandidateIndices = if (shouldExtract3dmm) {
            selectLargestFaceIndices(
                faces = faces,
                maxFaceCount = MAX_3DMM_FACE_COUNT,
            )
        } else {
            emptySet()
        }
        val raw3dmmList = if (shouldExtract3dmm && faces.isNotEmpty()) {
            faces.mapIndexed { idx, face ->
                if (idx !in tdmmCandidateIndices) {
                    null
                } else {
                    val rect = Rect(face.x, face.y, face.x + face.width, face.y + face.height)
                    facial3DMMExtractor.extract3DMM(bitmap, rect)?.coeffs
                }
            }
        } else {
            List(faces.size) { null }
        }

        val trackingIds: List<Int> = if (frameIndex != null && faces.isNotEmpty()) {
            val detections = faces.map { intArrayOf(it.x, it.y, it.width, it.height) }
            faceTracker.update(
                detections = detections,
                idCoeffs = raw3dmmList.map { it?.idCoeffs },
            )
        } else {
            faces.indices.toList()
        }

        fun hasValidRecognitionInput(idx: Int): Boolean = faces.getOrNull(idx)?.landmarks != null
        val seenTrackIds = trackingIds.toSet()
        trackLabelState.beginFrame(seenTrackIds)
        pruneRecognitionQueue(seenTrackIds)

        if (faces.isNotEmpty()) {
            val recognitionInputIndicesByTrackId = mutableMapOf<Int, Int>()
            for (idx in faces.indices) {
                if (!hasValidRecognitionInput(idx)) continue
                val trackId = trackingIds.getOrElse(idx) { idx }
                recognitionInputIndicesByTrackId[trackId] = idx
                trackLabelState.recordFrameSeen(trackId)
                if (trackLabelState.needsRecognition(trackId)) {
                    recognitionQueue.add(trackId)
                }
            }
            val activeTrackId = recognitionQueue.firstOrNull()
            if (activeTrackId != null) {
                val selectedIndex = recognitionInputIndicesByTrackId[activeTrackId]
                if (selectedIndex == null) {
                    if (trackLabelState.needsRecognition(activeTrackId)) {
                        moveRecognitionTrackToBack(activeTrackId)
                    } else {
                        recognitionQueue.remove(activeTrackId)
                    }
                } else {
                    val face = faces[selectedIndex]
                    val isFrontal = face.landmarks?.isFrontal(0.4f) ?: false
                    if (!trackLabelState.needsEmbeddingThisFrame(activeTrackId, isFrontal)) {
                        if (trackLabelState.needsRecognition(activeTrackId)) {
                            moveRecognitionTrackToBack(activeTrackId)
                        } else {
                            recognitionQueue.remove(activeTrackId)
                        }
                    } else {
                        val faceRect = Rect(face.x, face.y, face.x + face.width, face.y + face.height)
                        val cropRect = computeRecognitionCropRect(face, bitmap.width, bitmap.height)
                        var madeProgress = false
                        if (cropRect != null) {
                            val cropWidth = cropRect.right - cropRect.left
                            val cropHeight = cropRect.bottom - cropRect.top
                            if (cropWidth >= 16 && cropHeight >= 16) {
                                var crop = Bitmap.createBitmap(
                                    bitmap,
                                    cropRect.left,
                                    cropRect.top,
                                    cropWidth,
                                    cropHeight,
                                )
                                face.landmarks?.let { lm ->
                                    val aligned = FaceAlignment.alignFaceForRecognition(crop, lm, faceRect)
                                    if (aligned != crop) {
                                        crop.recycle()
                                        crop = aligned
                                    }
                                }
                                embeddingExtractor.extractEmbedding(crop)?.let { emb ->
                                    madeProgress = true
                                    when (trackLabelState.getLabel(activeTrackId)) {
                                        null -> trackLabelState.addEmbedding(activeTrackId, emb)
                                        false -> trackLabelState.recheckFrontal(activeTrackId, emb)
                                        true -> { }
                                    }
                                }
                                crop.recycle()
                            }
                        }

                        if (!trackLabelState.needsRecognition(activeTrackId)) {
                            recognitionQueue.remove(activeTrackId)
                        } else if (!madeProgress) {
                            moveRecognitionTrackToBack(activeTrackId)
                        }
                    }
                }
            }
        }

        val frameExport = if (frameIndex != null) {
            val facesExport = if (activePrivacyMode == PrivacyMode.Avatar) {
                faces.mapIndexedNotNull { idx, face ->
                    if (idx !in tdmmCandidateIndices) return@mapIndexedNotNull null
                    val raw3dmm = raw3dmmList.getOrNull(idx)
                    val trackId = trackingIds.getOrElse(idx) { idx }
                    val isOwner = trackLabelState.getLabel(trackId)
                    FaceExport(
                        trackingId = trackId,
                        bbox = intArrayOf(face.x, face.y, face.width, face.height),
                        idCoeffs = raw3dmm?.idCoeffs,
                        expCoeffs = raw3dmm?.expCoeffs,
                        pose = raw3dmm?.pose,
                        extraCoeffs = raw3dmm?.extraCoeffs,
                        isOwner = isOwner
                    )
                }
            } else {
                emptyList()
            }
            FrameExport(
                frameNumber = frameIndex,
                timestamp = timestampMs / 1000.0,
                faces = facesExport
            )
        } else null

        val faceLabels = faces.indices.map { trackLabelState.getLabel(trackingIds.getOrElse(it) { it }) }
        return ProcessedFrame(
            faces = faces,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            timestampMs = timestampMs,
            frameExport = frameExport,
            trackingIds = trackingIds,
            faceLabels = faceLabels
        )
    }

    fun process(rgbaBuffer: ByteBuffer, width: Int, height: Int, timestampMs: Long, frameIndex: Int? = null): ProcessedFrame {
        val bitmap = obtainReusableFrameBitmap(width, height)
        rgbaBuffer.rewind()
        bitmap.copyPixelsFromBuffer(rgbaBuffer)
        return process(bitmap, timestampMs, frameIndex)
    }

    fun markTrackAsOwner(trackId: Int) {
        trackLabelState.markOwner(trackId)
    }

    private fun obtainReusableFrameBitmap(width: Int, height: Int): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val cached = frameBitmapHolder.get()
        if (cached != null &&
            !cached.isRecycled &&
            cached.width == safeWidth &&
            cached.height == safeHeight
        ) {
            return cached
        }
        if (cached != null && !cached.isRecycled) {
            cached.recycle()
        }
        return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also {
            frameBitmapHolder.set(it)
        }
    }

    fun clearThreadLocalCache() {
        val bitmap = frameBitmapHolder.get()
        frameBitmapHolder.remove()
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    fun resetSessionState() {
        faceTracker.reset()
        trackLabelState.clear()
        recognitionQueue.clear()
        clearThreadLocalCache()
    }

    private fun selectLargestFaceIndices(
        faces: List<DetectedFace>,
        maxFaceCount: Int,
    ): Set<Int> {
        if (maxFaceCount <= 0 || faces.isEmpty()) return emptySet()
        if (faces.size <= maxFaceCount) return faces.indices.toSet()
        return faces.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<DetectedFace>> {
                    it.value.width.toLong().coerceAtLeast(0L) * it.value.height.toLong().coerceAtLeast(0L)
                }.thenBy { it.index },
            )
            .take(maxFaceCount)
            .mapTo(linkedSetOf()) { it.index }
    }

    private fun pruneRecognitionQueue(seenTrackIds: Set<Int>) {
        val iterator = recognitionQueue.iterator()
        while (iterator.hasNext()) {
            val trackId = iterator.next()
            if (trackId !in seenTrackIds || !trackLabelState.needsRecognition(trackId)) {
                iterator.remove()
            }
        }
    }

    private fun moveRecognitionTrackToBack(trackId: Int) {
        if (!recognitionQueue.remove(trackId)) return
        recognitionQueue.add(trackId)
    }

    private fun computeRecognitionCropRect(
        face: DetectedFace,
        frameWidth: Int,
        frameHeight: Int,
    ): RecognitionCropWindow? {
        if (frameWidth <= 0 || frameHeight <= 0) return null

        val centerX = face.x + (face.width / 2f)
        val centerY = face.y + (face.height / 2f)
        val halfWidth = face.width * RECOGNITION_CROP_SCALE / 2f
        val halfHeight = face.height * RECOGNITION_CROP_SCALE / 2f

        val rawLeft = floor(centerX - halfWidth).toInt()
        val rawTop = floor(centerY - halfHeight).toInt()
        val rawRight = ceil(centerX + halfWidth).toInt()
        val rawBottom = ceil(centerY + halfHeight).toInt()

        val left = rawLeft.coerceIn(0, frameWidth - 1)
        val top = rawTop.coerceIn(0, frameHeight - 1)
        val right = rawRight.coerceIn(left + 1, frameWidth)
        val bottom = rawBottom.coerceIn(top + 1, frameHeight)
        return RecognitionCropWindow(left, top, right, bottom)
    }
}
