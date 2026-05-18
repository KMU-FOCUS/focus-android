package com.kmu_focus.focusandroid.core.media.data.gl

import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import kotlin.math.sqrt

/**
 * ProcessedFrame의 얼굴/라벨 정보를 privacy mask 셰이더 입력 타원 목록으로 변환한다.
 * 랜드마크가 있으면 눈/코/입 중심의 더 작은 타원과 상단 clip을 계산하고,
 * 없으면 박스 기반 근사치로 fallback한다.
 * OWNER(true)는 제외하고, PENDING(null)/OTHER(false)만 포함한다.
 */
object FaceEllipseCalculator {

    private const val MASK_RADIUS_EXPANSION_RATIO = 1.16f
    private const val LANDMARK_CENTER_BIAS_RATIO = 0.42f
    private const val LANDMARK_HORIZONTAL_RADIUS_RATIO = 0.90f
    private const val LANDMARK_VERTICAL_RADIUS_RATIO = 1.05f
    private const val FRONTAL_HORIZONTAL_SCALE = 1.08f
    private const val FRONTAL_VERTICAL_SCALE = 1.06f
    private const val EYEBROW_CLEARANCE_RATIO = 0.24f
    private const val PROFILE_FRONTAL_THRESHOLD = 0.20f
    private const val PROFILE_FULL_THRESHOLD = 0.55f
    private const val PROFILE_BOX_RADIUS_X_RATIO = 0.42f
    private const val PROFILE_BOX_RADIUS_Y_RATIO = 0.40f
    private const val PROFILE_SIDE_EXTRA_RATIO = 0.14f
    private const val PROFILE_CENTER_BLEND_RATIO = 0.65f
    private const val FRONTAL_TOP_CLIP = -0.62f
    private const val PROFILE_TOP_CLIP = -0.88f
    private const val ROLL_ANGLE_DAMPING_FRONTAL = 0.18f
    private const val ROLL_ANGLE_DAMPING_PROFILE = 0.30f
    private const val ROLL_ANGLE_MAX_RAD = 0.20f

    private const val BOX_CENTER_Y_RATIO = 0.56f
    private const val BOX_RADIUS_X_RATIO = 0.34f
    private const val BOX_RADIUS_Y_RATIO = 0.38f
    private const val BOX_TOP_CLIP = -0.72f
    private const val MAX_ELLIPSES = 8

    fun calculate(frame: ProcessedFrame): List<EllipseParams> {
        if (frame.faces.isEmpty()) return emptyList()
        if (frame.frameWidth <= 0 || frame.frameHeight <= 0) return emptyList()

        val result = ArrayList<EllipseParams>(minOf(frame.faces.size, MAX_ELLIPSES))
        val frameWidth = frame.frameWidth.toFloat()
        val frameHeight = frame.frameHeight.toFloat()

        for (index in frame.faces.indices) {
            if (result.size >= MAX_ELLIPSES) break

            val label = frame.faceLabels.getOrNull(index)
            if (label == true) continue

            val face = frame.faces[index]
            val trackingId = frame.trackingIds.getOrElse(index) { index }
            result.add(face.toEllipse(frameWidth, frameHeight, trackingId))
        }

        return result
    }

    private fun com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace.toEllipse(
        frameWidth: Float,
        frameHeight: Float,
        trackingId: Int,
    ): EllipseParams {
        val landmarks = landmarks ?: return toFallbackEllipse(frameWidth, frameHeight, trackingId)
        val eyeCenter = landmarks.getEyeCenter()
        val mouthCenter = landmarks.getMouthCenter()
        val eyeDistance = landmarks.getEyeDistance()
        if (eyeDistance <= 0f) return toFallbackEllipse(frameWidth, frameHeight, trackingId)

        val eyeMouthDistance = sqrt(
            (mouthCenter.x - eyeCenter.x) * (mouthCenter.x - eyeCenter.x) +
                (mouthCenter.y - eyeCenter.y) * (mouthCenter.y - eyeCenter.y)
        )
        val profileStrength = landmarks.profileStrength()
        val profileCenterBlend = profileStrength * PROFILE_CENTER_BLEND_RATIO
        val landmarkCenterX = eyeCenter.x + (mouthCenter.x - eyeCenter.x) * LANDMARK_CENTER_BIAS_RATIO
        val landmarkCenterY = eyeCenter.y + (mouthCenter.y - eyeCenter.y) * LANDMARK_CENTER_BIAS_RATIO
        val fallbackCenterX = x + width / 2f
        val fallbackCenterY = y + height * BOX_CENTER_Y_RATIO
        val centerX = lerp(landmarkCenterX, fallbackCenterX, profileCenterBlend)
        val centerY = lerp(landmarkCenterY, fallbackCenterY, profileCenterBlend)
        val frontalScale = 1f - profileStrength
        val frontalHorizontalScale = lerp(1f, FRONTAL_HORIZONTAL_SCALE, frontalScale)
        val frontalVerticalScale = lerp(1f, FRONTAL_VERTICAL_SCALE, frontalScale)
        val baseRadiusX = eyeDistance * LANDMARK_HORIZONTAL_RADIUS_RATIO * MASK_RADIUS_EXPANSION_RATIO * frontalHorizontalScale
        val baseRadiusY = eyeMouthDistance * LANDMARK_VERTICAL_RADIUS_RATIO * MASK_RADIUS_EXPANSION_RATIO * frontalVerticalScale
        val exposedSideSign = landmarks.exposedSideSign()
        val sideAdjustedRadiusX = lerp(
            baseRadiusX,
            width * PROFILE_BOX_RADIUS_X_RATIO * MASK_RADIUS_EXPANSION_RATIO,
            profileStrength,
        )
        val sideAdjustedRadiusY = lerp(
            baseRadiusY,
            height * PROFILE_BOX_RADIUS_Y_RATIO * MASK_RADIUS_EXPANSION_RATIO,
            profileStrength,
        )
        val radiusX = maxOf(baseRadiusX, sideAdjustedRadiusX)
        val radiusY = maxOf(baseRadiusY, sideAdjustedRadiusY)
        val sideExtraRadiusX = width * PROFILE_SIDE_EXTRA_RATIO * profileStrength
        val leftRadiusX = radiusX + if (exposedSideSign < 0f) sideExtraRadiusX else 0f
        val rightRadiusX = radiusX + if (exposedSideSign > 0f) sideExtraRadiusX else 0f
        val baseMaskTopY = eyeCenter.y - (eyeDistance * EYEBROW_CLEARANCE_RATIO)
        val baseTopClip = ((baseMaskTopY - centerY) / radiusY).coerceIn(-1f, 1f)
        val frontalTopClip = minOf(baseTopClip, FRONTAL_TOP_CLIP)
        val topClip = lerp(frontalTopClip, PROFILE_TOP_CLIP, profileStrength).coerceIn(-1f, 1f)
        return EllipseParams(
            trackingId = trackingId,
            profileStrength = profileStrength,
            centerX = normalize(centerX, frameWidth),
            centerY = normalize(centerY, frameHeight),
            radiusX = normalize(radiusX, frameWidth),
            radiusY = normalize(radiusY, frameHeight),
            angle = landmarks.stabilizedMaskAngle(profileStrength),
            topClip = topClip,
            leftRadiusX = normalize(leftRadiusX, frameWidth),
            rightRadiusX = normalize(rightRadiusX, frameWidth),
        )
    }

    private fun com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace.toFallbackEllipse(
        frameWidth: Float,
        frameHeight: Float,
        trackingId: Int,
    ): EllipseParams {
        val centerX = x + width / 2f
        val centerY = y + height * BOX_CENTER_Y_RATIO
        val radiusX = width * BOX_RADIUS_X_RATIO * MASK_RADIUS_EXPANSION_RATIO
        val radiusY = height * BOX_RADIUS_Y_RATIO * MASK_RADIUS_EXPANSION_RATIO
        return EllipseParams(
            trackingId = trackingId,
            profileStrength = 0.75f,
            centerX = normalize(centerX, frameWidth),
            centerY = normalize(centerY, frameHeight),
            radiusX = normalize(radiusX, frameWidth),
            radiusY = normalize(radiusY, frameHeight),
            angle = 0f,
            topClip = BOX_TOP_CLIP,
        )
    }

    private fun normalize(value: Float, size: Float): Float {
        if (size <= 0f) return 0f
        return (value / size).coerceIn(0f, 1f)
    }

    private fun FaceLandmarks5.profileStrength(): Float {
        val eyeCenterX = (leftEye.x + rightEye.x) / 2f
        val eyeDistance = getEyeDistance()
        if (eyeDistance <= 1e-6f) return 1f
        val noseOffsetRatio = kotlin.math.abs(nose.x - eyeCenterX) / eyeDistance
        return ((noseOffsetRatio - PROFILE_FRONTAL_THRESHOLD) /
            (PROFILE_FULL_THRESHOLD - PROFILE_FRONTAL_THRESHOLD)).coerceIn(0f, 1f)
    }

    private fun FaceLandmarks5.exposedSideSign(): Float {
        val eyeCenterX = (leftEye.x + rightEye.x) / 2f
        val delta = eyeCenterX - nose.x
        return when {
            delta < 0f -> -1f
            delta > 0f -> 1f
            else -> 0f
        }
    }

    private fun FaceLandmarks5.stabilizedMaskAngle(profileStrength: Float): Float {
        val rawAngle = getFaceAngle()
        val damping = lerp(
            start = ROLL_ANGLE_DAMPING_FRONTAL,
            end = ROLL_ANGLE_DAMPING_PROFILE,
            t = profileStrength,
        )
        return (rawAngle * damping).coerceIn(-ROLL_ANGLE_MAX_RAD, ROLL_ANGLE_MAX_RAD)
    }

    private fun lerp(
        start: Float,
        end: Float,
        t: Float,
    ): Float = start + (end - start) * t
}
