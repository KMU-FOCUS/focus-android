package com.kmu_focus.focusandroid.feature.metadatareview.data

import com.kmu_focus.focusandroid.core.metadata.domain.entity.BBox
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FaceData
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.entity.ThreeDMM
import com.kmu_focus.focusandroid.feature.metadatareview.domain.model.ParsedMetadata
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class MetadataPreviewParser @Inject constructor() {

    fun parse(rawJson: String): ParsedMetadata {
        val trimmedJson = rawJson.trim()
        if (trimmedJson.startsWith("[")) {
            return parseCoreMetadataFrames(
                framesJson = JSONArray(trimmedJson),
                coordinateWidth = 0,
                coordinateHeight = 0,
                fps = null,
            )
        }

        if (!trimmedJson.startsWith("{")) {
            throw IllegalArgumentException("지원하지 않는 메타데이터 형식입니다.")
        }

        val root = JSONObject(trimmedJson)
        val framesJson = root.optJSONArray("frames")
            ?: throw IllegalArgumentException("메타데이터에서 frames 배열을 찾을 수 없습니다.")

        return if (root.has("video_info")) {
            parseVideoExportMetadata(root, framesJson)
        } else {
            parseCoreMetadataFrames(
                framesJson = framesJson,
                coordinateWidth = 0,
                coordinateHeight = 0,
                fps = null,
            )
        }
    }

    private fun parseVideoExportMetadata(
        root: JSONObject,
        framesJson: JSONArray,
    ): ParsedMetadata {
        val videoInfoJson = root.optJSONObject("video_info")
        val fps = videoInfoJson?.optDouble("fps")?.takeIf { it.isFinite() && it > 0.0 }?.toFloat()
        val frames = buildList(framesJson.length()) {
            for (index in 0 until framesJson.length()) {
                val frameJson = framesJson.optJSONObject(index) ?: continue
                add(parseVideoExportFrame(frameJson, fps))
            }
        }

        if (frames.isEmpty()) {
            throw IllegalArgumentException("메타데이터 프레임이 비어 있습니다.")
        }

        return ParsedMetadata(
            frames = frames,
            coordinateWidth = videoInfoJson?.optInt("width") ?: 0,
            coordinateHeight = videoInfoJson?.optInt("height") ?: 0,
            fps = fps,
        )
    }

    private fun parseCoreMetadataFrames(
        framesJson: JSONArray,
        coordinateWidth: Int,
        coordinateHeight: Int,
        fps: Float?,
    ): ParsedMetadata {
        val frames = buildList(framesJson.length()) {
            for (index in 0 until framesJson.length()) {
                val frameJson = framesJson.optJSONObject(index) ?: continue
                add(parseCoreMetadataFrame(frameJson))
            }
        }
        if (frames.isEmpty()) {
            throw IllegalArgumentException("메타데이터 프레임이 비어 있습니다.")
        }

        return ParsedMetadata(
            frames = frames,
            coordinateWidth = coordinateWidth,
            coordinateHeight = coordinateHeight,
            fps = fps,
        )
    }

    private fun parseVideoExportFrame(
        frameJson: JSONObject,
        fps: Float?,
    ): FrameMetadata {
        val timestampSeconds = frameJson.optDouble("timestamp")
        val frameNumber = frameJson.optInt("frame_number", -1)
        val ptsUs = when {
            timestampSeconds.isFinite() -> (timestampSeconds * MICROS_PER_SECOND).toLong()
            fps != null && fps > 0f && frameNumber >= 0 -> ((frameNumber / fps) * MICROS_PER_SECOND).toLong()
            else -> 0L
        }
        val facesJson = frameJson.optJSONArray("faces") ?: JSONArray()

        return FrameMetadata(
            sessionId = "",
            ptsUs = ptsUs,
            faces = buildList(facesJson.length()) {
                for (index in 0 until facesJson.length()) {
                    val faceJson = facesJson.optJSONObject(index) ?: continue
                    add(parseFace(faceJson))
                }
            },
        )
    }

    private fun parseCoreMetadataFrame(frameJson: JSONObject): FrameMetadata {
        val facesJson = frameJson.optJSONArray("faces") ?: JSONArray()

        return FrameMetadata(
            sessionId = frameJson.optString("session_id", ""),
            ptsUs = frameJson.optLong("pts_us"),
            faces = buildList(facesJson.length()) {
                for (index in 0 until facesJson.length()) {
                    val faceJson = facesJson.optJSONObject(index) ?: continue
                    add(parseFace(faceJson))
                }
            },
        )
    }

    private fun parseFace(faceJson: JSONObject): FaceData {
        val bbox = parseBoundingBox(faceJson)

        return FaceData(
            trackingId = faceJson.optInt("tracking_id"),
            bbox = bbox,
            tdmm = parseTdmm(faceJson),
        )
    }

    private fun parseBoundingBox(faceJson: JSONObject): BBox {
        faceJson.optJSONObject("bbox")?.let { bboxJson ->
            return BBox(
                x = bboxJson.optInt("x"),
                y = bboxJson.optInt("y"),
                width = bboxJson.optInt("width").coerceAtLeast(0),
                height = bboxJson.optInt("height").coerceAtLeast(0),
            )
        }

        faceJson.optJSONArray("bbox")?.let { bboxArray ->
            if (bboxArray.length() >= 4) {
                return BBox(
                    x = bboxArray.optInt(0),
                    y = bboxArray.optInt(1),
                    width = bboxArray.optInt(2).coerceAtLeast(0),
                    height = bboxArray.optInt(3).coerceAtLeast(0),
                )
            }
        }

        throw IllegalArgumentException("지원하지 않는 bbox 형식입니다.")
    }

    private fun parseTdmm(faceJson: JSONObject): ThreeDMM? {
        faceJson.optJSONObject("tdmm_raw")?.let { tdmmRaw ->
            return ThreeDMM(
                coeffs = tdmmRaw.optJSONArray("coeffs").toFloatArray(),
            )
        }

        val raw3dmm = faceJson.optJSONObject("3dmm") ?: return null
        val values = ArrayList<Float>()
        appendCoefficients(values, raw3dmm.optJSONArray("id_coeffs"))
        appendCoefficients(values, raw3dmm.optJSONArray("exp_coeffs"))
        appendCoefficients(values, raw3dmm.optJSONArray("pose"))
        appendCoefficients(values, raw3dmm.optJSONArray("extra_coeffs"))
        return ThreeDMM(coeffs = values.toFloatArray())
    }

    private fun appendCoefficients(
        destination: MutableList<Float>,
        source: JSONArray?,
    ) {
        if (source == null) return
        for (index in 0 until source.length()) {
            destination += source.optDouble(index).toFloat()
        }
    }

    private fun JSONArray?.toFloatArray(): FloatArray {
        if (this == null) return FloatArray(0)

        return FloatArray(length()) { index ->
            optDouble(index).toFloat()
        }
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000.0
    }
}
