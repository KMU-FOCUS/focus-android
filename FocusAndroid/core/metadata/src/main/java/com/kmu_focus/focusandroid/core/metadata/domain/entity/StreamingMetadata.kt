package com.kmu_focus.focusandroid.core.metadata.domain.entity

data class FrameMetadata(
    val sessionId: String,
    val ptsUs: Long,
    val faces: List<FaceData>,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val rotation: Int = 0,
    val mirrored: Boolean = false,
)

data class FaceData(
    val trackingId: Int,
    val bbox: BBox,
    val tdmm: ThreeDMM? = null,
)

data class BBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class ThreeDMM(
    val coeffs: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        other is ThreeDMM && coeffs.contentEquals(other.coeffs)

    override fun hashCode(): Int = coeffs.contentHashCode()
}
