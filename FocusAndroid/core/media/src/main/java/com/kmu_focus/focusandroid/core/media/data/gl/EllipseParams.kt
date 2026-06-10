package com.kmu_focus.focusandroid.core.media.data.gl

/**
 * 셰이더 입력용 정규화 얼굴 마스크 파라미터.
 *
 * 모든 좌표/반경은 0.0~1.0 범위를 기준으로 전달한다.
 * angle은 라디안 단위다.
 * topClip은 회전 보정된 타원 좌표계(-1.0~1.0)에서 마스크를 시작할 Y 값이다.
 * left/rightRadiusX는 로컬 좌표계 기준 좌우 비대칭 가로 반경이다.
 * maskColorRGB는 안정화된 피부색 마스크 기본값이다.
 */
data class EllipseParams(
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val angle: Float,
    val topClip: Float = -1f,
    val leftRadiusX: Float = radiusX,
    val rightRadiusX: Float = radiusX,
    val maskColorR: Float = 0.76f,
    val maskColorG: Float = 0.65f,
    val maskColorB: Float = 0.58f,
    val trackingId: Int = -1,
    val profileStrength: Float = 0f,
)
