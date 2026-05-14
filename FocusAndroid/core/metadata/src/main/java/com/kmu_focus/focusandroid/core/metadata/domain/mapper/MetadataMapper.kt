package com.kmu_focus.focusandroid.core.metadata.domain.mapper

import com.kmu_focus.focusandroid.core.metadata.domain.entity.BBox
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FaceData
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.entity.ThreeDMM
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.ceil
import kotlin.math.floor

object MetadataMapper {

    data class CoordinateSpace(
        val analysisWidth: Int,
        val analysisHeight: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
    ) {
        fun isValid(): Boolean =
            analysisWidth > 0 &&
                analysisHeight > 0 &&
                sourceWidth > 0 &&
                sourceHeight > 0
    }

    data class FaceExportPayload(
        val trackingId: Int,
        val bbox: IntArray,
        val idCoeffs: FloatArray?,
        val expCoeffs: FloatArray?,
        val pose: FloatArray?,
        val extraCoeffs: FloatArray? = null,
        val isOwner: Boolean?,
    )

    fun mapFrame(
        sessionId: String,
        timestampSeconds: Double,
        faces: List<FaceExportPayload>,
        coordinateSpace: CoordinateSpace? = null,
    ): FrameMetadata {
        val ptsUs = if (timestampSeconds.isFinite()) {
            (timestampSeconds * MICROS_PER_SECOND).toLong()
        } else {
            0L
        }

        val mappedFaces = faces.asSequence()
            .filter { it.isOwner == false }
            .mapNotNull { face ->
                val id = face.idCoeffs
                val exp = face.expCoeffs
                val pose = face.pose
                if (id == null || exp == null || pose == null) {
                    logDrop("missing coeffs", face)
                    return@mapNotNull null
                }
                if (face.bbox.size < BBOX_SIZE) {
                    logDrop("invalid bbox size=${face.bbox.size}", face)
                    return@mapNotNull null
                }
                val extra = face.extraCoeffs ?: floatArrayOf()
                val idDim = id.size
                val expDim = exp.size
                val poseDim = pose.size
                val extraDim = extra.size
                if (!isFaceMapLayout(idDim, expDim, poseDim, extraDim)) {
                    logDrop(
                        reason = "unsupported FaceMap layout(id=$idDim, exp=$expDim, pose=$poseDim, extra=$extraDim)",
                        face = face,
                    )
                    return@mapNotNull null
                }
                val mappedBbox = mapBoundingBoxToSourcePixelSpace(
                    bbox = face.bbox,
                    coordinateSpace = coordinateSpace,
                )

                FaceData(
                    trackingId = face.trackingId,
                    bbox = BBox(
                        x = mappedBbox[0],
                        y = mappedBbox[1],
                        width = mappedBbox[2],
                        height = mappedBbox[3],
                    ),
                    tdmm = ThreeDMM(
                        coeffs = concatCoeffs(id, exp, pose, extra),
                    ),
                )
            }
            .toList()

        return FrameMetadata(
            sessionId = sessionId,
            ptsUs = ptsUs,
            faces = mappedFaces,
        )
    }

    private const val MICROS_PER_SECOND = 1_000_000.0
    private const val BBOX_SIZE = 4
    private const val FACEMAP_ID_DIM = 219
    private const val FACEMAP_EXP_DIM = 39
    private const val FACEMAP_POSE_DIM = 6
    private const val FACEMAP_EXTRA_DIM = 1
    private val logger: Logger = Logger.getLogger(MetadataMapper::class.java.name)

    private fun concatCoeffs(
        idCoeffs: FloatArray,
        expCoeffs: FloatArray,
        pose: FloatArray,
        extraCoeffs: FloatArray,
    ): FloatArray {
        val out = FloatArray(idCoeffs.size + expCoeffs.size + pose.size + extraCoeffs.size)
        var offset = 0
        idCoeffs.copyInto(out, destinationOffset = offset)
        offset += idCoeffs.size
        expCoeffs.copyInto(out, destinationOffset = offset)
        offset += expCoeffs.size
        pose.copyInto(out, destinationOffset = offset)
        offset += pose.size
        extraCoeffs.copyInto(out, destinationOffset = offset)
        return out
    }

    private fun isFaceMapLayout(
        idDim: Int,
        expDim: Int,
        poseDim: Int,
        extraDim: Int,
    ): Boolean {
        return idDim == FACEMAP_ID_DIM &&
            expDim == FACEMAP_EXP_DIM &&
            poseDim == FACEMAP_POSE_DIM &&
            extraDim == FACEMAP_EXTRA_DIM
    }

    private fun mapBoundingBoxToSourcePixelSpace(
        bbox: IntArray,
        coordinateSpace: CoordinateSpace?,
    ): IntArray {
        if (coordinateSpace?.isValid() != true) return bbox.copyOf()

        val analysisWidth = coordinateSpace.analysisWidth.toFloat()
        val analysisHeight = coordinateSpace.analysisHeight.toFloat()
        val sourceWidth = coordinateSpace.sourceWidth
        val sourceHeight = coordinateSpace.sourceHeight
        val scale = minOf(
            analysisWidth / sourceWidth.toFloat(),
            analysisHeight / sourceHeight.toFloat(),
        )
        if (!scale.isFinite() || scale <= 0f) return bbox.copyOf()

        val contentWidth = sourceWidth * scale
        val contentHeight = sourceHeight * scale
        val contentLeft = (analysisWidth - contentWidth) / 2f
        val contentTop = (analysisHeight - contentHeight) / 2f

        val rawLeft = (bbox[0] - contentLeft) / scale
        val rawTop = (bbox[1] - contentTop) / scale
        val rawRight = (bbox[0] + bbox[2] - contentLeft) / scale
        val rawBottom = (bbox[1] + bbox[3] - contentTop) / scale
        if (
            !rawLeft.isFinite() ||
            !rawTop.isFinite() ||
            !rawRight.isFinite() ||
            !rawBottom.isFinite()
        ) {
            return bbox.copyOf()
        }
        if (
            rawRight <= 0f ||
            rawBottom <= 0f ||
            rawLeft >= sourceWidth.toFloat() ||
            rawTop >= sourceHeight.toFloat()
        ) {
            return bbox.copyOf()
        }

        val mappedLeft = floor(rawLeft).toInt().coerceIn(0, sourceWidth - 1)
        val mappedTop = floor(rawTop).toInt().coerceIn(0, sourceHeight - 1)
        val mappedRight = ceil(rawRight).toInt().coerceIn(0, sourceWidth)
        val mappedBottom = ceil(rawBottom).toInt().coerceIn(0, sourceHeight)
        val mappedWidth = (mappedRight - mappedLeft)
            .coerceAtLeast(1)
            .coerceAtMost(sourceWidth - mappedLeft)
        val mappedHeight = (mappedBottom - mappedTop)
            .coerceAtLeast(1)
            .coerceAtMost(sourceHeight - mappedTop)

        return intArrayOf(mappedLeft, mappedTop, mappedWidth, mappedHeight)
    }

    private fun logDrop(reason: String, face: FaceExportPayload) {
        if (!logger.isLoggable(Level.WARNING)) return
        logger.warning(
            "MetadataMapper dropped face(trackId=${face.trackingId}): $reason"
        )
    }
}
