package com.kmu_focus.focusandroid.core.media.data.gl

import android.opengl.GLES30
import androidx.annotation.VisibleForTesting
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MosaicProgram {

    private var compositeProgramId = 0
    private var vaoId = 0
    private var vboId = 0

    private var compositeTextureLoc = 0
    private var compositeFaceCountLoc = 0
    private var compositeEllipseCenterLoc = 0
    private var compositeEllipseHorizontalRadiusLoc = 0
    private var compositeEllipseVerticalRadiusLoc = 0
    private var compositeEllipseAngleLoc = 0
    private var compositeTopClipLoc = 0
    private var compositeMaskColorLoc = 0
    private var compositeRegionRectLoc = 0
    private val uniformCenters = FloatArray(MAX_FACES * 2)
    private val uniformHorizontalRadii = FloatArray(MAX_FACES * 2)
    private val uniformVerticalRadii = FloatArray(MAX_FACES)
    private val uniformAngles = FloatArray(MAX_FACES)
    private val uniformTopClips = FloatArray(MAX_FACES)
    private val uniformMaskColors = FloatArray(MAX_FACES * 3)
    private var previousFaceCount = 0

    fun init() {
        compositeProgramId = createProgram(VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER)
        compositeTextureLoc = GLES30.glGetUniformLocation(compositeProgramId, "uTexture")
        compositeFaceCountLoc = GLES30.glGetUniformLocation(compositeProgramId, "uFaceCount")
        compositeEllipseCenterLoc = GLES30.glGetUniformLocation(compositeProgramId, "uEllipseCenter[0]")
        compositeEllipseHorizontalRadiusLoc = GLES30.glGetUniformLocation(compositeProgramId, "uEllipseRadiusX[0]")
        compositeEllipseVerticalRadiusLoc = GLES30.glGetUniformLocation(compositeProgramId, "uEllipseRadiusY[0]")
        compositeEllipseAngleLoc = GLES30.glGetUniformLocation(compositeProgramId, "uEllipseAngle[0]")
        compositeTopClipLoc = GLES30.glGetUniformLocation(compositeProgramId, "uTopClip[0]")
        compositeMaskColorLoc = GLES30.glGetUniformLocation(compositeProgramId, "uMaskColor[0]")
        compositeRegionRectLoc = GLES30.glGetUniformLocation(compositeProgramId, "uRegionRect")
        setupVao()
    }

    fun compositeMaskedRegion(
        textureId: Int,
        ellipses: List<EllipseParams>,
        regionRect: UvRect,
    ) {
        if (compositeProgramId == 0 || textureId == 0) return

        val faceCount = updateUniformData(ellipses)
        if (faceCount == 0) return

        GLES30.glUseProgram(compositeProgramId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(compositeTextureLoc, 0)
        GLES30.glUniform1i(compositeFaceCountLoc, faceCount)
        GLES30.glUniform2fv(compositeEllipseCenterLoc, MAX_FACES, uniformCenters, 0)
        GLES30.glUniform2fv(compositeEllipseHorizontalRadiusLoc, MAX_FACES, uniformHorizontalRadii, 0)
        GLES30.glUniform1fv(compositeEllipseVerticalRadiusLoc, MAX_FACES, uniformVerticalRadii, 0)
        GLES30.glUniform1fv(compositeEllipseAngleLoc, MAX_FACES, uniformAngles, 0)
        GLES30.glUniform1fv(compositeTopClipLoc, MAX_FACES, uniformTopClips, 0)
        GLES30.glUniform3fv(compositeMaskColorLoc, MAX_FACES, uniformMaskColors, 0)
        GLES30.glUniform4f(
            compositeRegionRectLoc,
            regionRect.minX,
            regionRect.minY,
            regionRect.maxX,
            regionRect.maxY,
        )
        drawQuad()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun updateUniformData(ellipses: List<EllipseParams>): Int {
        if (previousFaceCount > 0) {
            for (index in 0 until previousFaceCount) {
                val base = index * 2
                uniformCenters[base] = 0f
                uniformCenters[base + 1] = 0f
                uniformHorizontalRadii[base] = 0f
                uniformHorizontalRadii[base + 1] = 0f
                uniformVerticalRadii[index] = 0f
                uniformAngles[index] = 0f
                uniformTopClips[index] = 0f
                val colorBase = index * 3
                uniformMaskColors[colorBase] = 0f
                uniformMaskColors[colorBase + 1] = 0f
                uniformMaskColors[colorBase + 2] = 0f
            }
        }

        val faceCount = minOf(ellipses.size, MAX_FACES)
        for (index in 0 until faceCount) {
            val ellipse = ellipses[index]
            val base = index * 2
            uniformCenters[base] = ellipse.centerX
            uniformCenters[base + 1] = ellipse.centerY
            uniformHorizontalRadii[base] = ellipse.leftRadiusX
            uniformHorizontalRadii[base + 1] = ellipse.rightRadiusX
            uniformVerticalRadii[index] = ellipse.radiusY
            uniformAngles[index] = ellipse.angle
            uniformTopClips[index] = ellipse.topClip
            val colorBase = index * 3
            uniformMaskColors[colorBase] = ellipse.maskColorR
            uniformMaskColors[colorBase + 1] = ellipse.maskColorG
            uniformMaskColors[colorBase + 2] = ellipse.maskColorB
        }
        previousFaceCount = faceCount
        return faceCount
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformCentersForTest(): FloatArray = uniformCenters

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformHorizontalRadiiForTest(): FloatArray = uniformHorizontalRadii

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformVerticalRadiiForTest(): FloatArray = uniformVerticalRadii

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformAnglesForTest(): FloatArray = uniformAngles

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformTopClipsForTest(): FloatArray = uniformTopClips

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformMaskColorsForTest(): FloatArray = uniformMaskColors

    fun release() {
        deleteProgramIfNeeded(compositeProgramId)
        compositeProgramId = 0
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
    }

    private fun setupVao() {
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]

        val buffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUAD_VERTICES)
            .also { it.position(0) }

        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            QUAD_VERTICES.size * 4,
            buffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawQuad() {
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun deleteProgramIfNeeded(programId: Int) {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
        }
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)

        val createdProgramId = GLES30.glCreateProgram()
        GLES30.glAttachShader(createdProgramId, vertexShader)
        GLES30.glAttachShader(createdProgramId, fragmentShader)
        GLES30.glLinkProgram(createdProgramId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(createdProgramId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(createdProgramId)
            GLES30.glDeleteProgram(createdProgramId)
            throw RuntimeException("Mosaic program link failed: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return createdProgramId
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Mosaic shader compile failed: $log")
        }

        return shader
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val MAX_FACES = 8

        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            out vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition.xy, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        private const val COMPOSITE_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            #define MAX_FACES 8

            in vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform int uFaceCount;
            uniform vec2 uEllipseCenter[MAX_FACES];
            uniform vec2 uEllipseRadiusX[MAX_FACES];
            uniform float uEllipseRadiusY[MAX_FACES];
            uniform float uEllipseAngle[MAX_FACES];
            uniform float uTopClip[MAX_FACES];
            uniform vec3 uMaskColor[MAX_FACES];
            uniform vec4 uRegionRect;
            out vec4 fragColor;

            const float MASK_EDGE_SOFTNESS = 0.06;
            const float TOP_EDGE_SOFTNESS = 0.04;

            vec2 toNormalizedLocal(int index, vec2 uv) {
                vec2 d = uv - uEllipseCenter[index];
                float cosA = cos(-uEllipseAngle[index]);
                float sinA = sin(-uEllipseAngle[index]);
                vec2 rotated = vec2(
                    d.x * cosA - d.y * sinA,
                    d.x * sinA + d.y * cosA
                );
                float radiusX = rotated.x < 0.0 ? uEllipseRadiusX[index].x : uEllipseRadiusX[index].y;
                vec2 safeRadius = vec2(
                    max(radiusX, 0.000001),
                    max(uEllipseRadiusY[index], 0.000001)
                );
                return rotated / safeRadius;
            }

            float maskCoverage(int index, vec2 normalized) {
                float ellipseCoverage = 1.0 - smoothstep(
                    1.0 - MASK_EDGE_SOFTNESS,
                    1.0,
                    length(normalized)
                );
                float topCoverage = smoothstep(
                    uTopClip[index] - TOP_EDGE_SOFTNESS,
                    uTopClip[index] + TOP_EDGE_SOFTNESS,
                    normalized.y
                );
                return ellipseCoverage * topCoverage;
            }

            float featureFillWeight(int index, vec2 normalized) {
                float eyeCore = (1.0 - smoothstep(0.16, 0.98, abs(normalized.x))) *
                    smoothstep(uTopClip[index] - 0.02, uTopClip[index] + 0.44, normalized.y) *
                    (1.0 - smoothstep(-0.10, 0.36, normalized.y));
                float noseCore = (1.0 - smoothstep(0.14, 0.62, abs(normalized.x))) *
                    smoothstep(-0.10, 0.18, normalized.y) *
                    (1.0 - smoothstep(0.66, 0.92, normalized.y));
                float mouthCore = (1.0 - smoothstep(0.16, 0.72, abs(normalized.x))) *
                    smoothstep(0.08, 0.34, normalized.y) *
                    (1.0 - smoothstep(0.88, 1.08, normalized.y));
                float centerCore = (1.0 - smoothstep(0.10, 0.74, abs(normalized.x))) *
                    smoothstep(uTopClip[index] + 0.10, uTopClip[index] + 0.56, normalized.y) *
                    (1.0 - smoothstep(0.92, 1.10, normalized.y));
                float featureCore = max(max(eyeCore, noseCore), max(mouthCore, centerCore * 0.94));
                float coreAlpha = step(0.08, featureCore);
                float shellMask = (1.0 - smoothstep(0.42, 1.02, abs(normalized.x))) *
                    smoothstep(uTopClip[index] - 0.02, uTopClip[index] + 0.56, normalized.y) *
                    (1.0 - smoothstep(1.00, 1.16, normalized.y));
                float edgeFeather = 1.0 - smoothstep(
                    0.50,
                    1.02,
                    length(vec2(normalized.x * 0.92, normalized.y * 0.86))
                );
                float shellAlpha = shellMask * edgeFeather;
                return clamp(coreAlpha + ((1.0 - coreAlpha) * shellAlpha), 0.0, 1.0);
            }

            vec3 resolveSkinFillColor(int index, vec2 normalized) {
                vec3 baseColor = uMaskColor[index];
                float verticalShade = mix(1.02, 0.97, smoothstep(-0.04, 0.82, normalized.y));
                float horizontalShade = mix(1.0, 0.99, smoothstep(0.0, 1.0, abs(normalized.x)));
                vec3 shaded = baseColor * (verticalShade * horizontalShade);
                shaded.r *= 1.01;
                shaded.b *= 0.995;
                return clamp(mix(shaded, baseColor, 0.58), vec3(0.0), vec3(1.0));
            }

            void main() {
                vec2 regionSize = max(uRegionRect.zw - uRegionRect.xy, vec2(0.000001));
                vec2 localUv = (vTexCoord - uRegionRect.xy) / regionSize;
                if (localUv.x < 0.0 || localUv.x > 1.0 || localUv.y < 0.0 || localUv.y > 1.0) {
                    discard;
                }
                vec3 originalColor = texture(uTexture, vTexCoord).rgb;

                for (int i = 0; i < MAX_FACES; i++) {
                    if (i >= uFaceCount) break;
                    vec2 normalized = toNormalizedLocal(i, vTexCoord);
                    float coverage = maskCoverage(i, normalized);
                    if (coverage <= 0.0) {
                        continue;
                    }
                    float fillWeight = coverage * featureFillWeight(i, normalized);
                    vec3 fillColor = resolveSkinFillColor(i, normalized);
                    fragColor = vec4(mix(originalColor, fillColor, fillWeight), 1.0);
                    return;
                }

                discard;
            }
        """

        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
    }
}
