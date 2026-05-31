package com.kmu_focus.focusandroid.feature.camera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.core.ai.data.recognition.FaceAlignment
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.TrackLabelState
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.camera.domain.entity.OwnerRegistrationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OwnerEnrollmentManager @Inject constructor(
    private val ownerAdder: OwnerAdder,
    private val trackLabelState: TrackLabelState,
    private val embeddingExtractor: ArcFaceEmbeddingExtractor,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "OwnerEnrollment"
        private const val THUMBNAIL_QUALITY = 95
        private const val SNAPSHOT_DIR = "owner_snapshots"
    }

    fun registerOwnerFromFrame(
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
        face.landmarks?.let { landmarks ->
            val aligned = FaceAlignment.alignFaceForRecognition(crop, landmarks, rect)
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
            Log.i(TAG, "registerOwner: trackId=$trackId registered, ownerId=$ownerId")
        }

        crop.recycle()
        bitmap.recycle()
        return OwnerRegistrationResult(
            success = success,
            ownerId = ownerId,
            thumbnailPath = thumbnailPath,
        )
    }

    fun removeOwner(
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
                        Log.w(TAG, "removeOwner: thumbnail delete failed", throwable)
                    }
            }
        Log.i(TAG, "removeOwner: ownerId=$ownerId, trackId=$trackId removed")
        return true
    }

    fun resetSessionState() {
        ownerAdder.clearOwners()
    }

    private fun saveFaceThumbnail(faceBitmap: Bitmap): String? = runCatching {
        val dir = File(context.cacheDir, SNAPSHOT_DIR).apply { mkdirs() }
        val file = File(dir, "owner_face_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { output ->
            faceBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, output)
        }
        file.absolutePath
    }.getOrNull()
}
