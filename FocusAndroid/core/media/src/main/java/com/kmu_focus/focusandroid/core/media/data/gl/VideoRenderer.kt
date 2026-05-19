package com.kmu_focus.focusandroid.core.media.data.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.media.data.recorder.EncoderThread
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val PRIVACY_REGION_PADDING_RATIO = 1.18f
private const val PRIVACY_REGION_PADDING_PX = 6
private const val PRIVACY_MASK_SHAPE_SMOOTHING_FACTOR = 0.72f
private const val PRIVACY_MASK_MATCH_RADIUS_MULTIPLIER = 1.25f
private const val PRIVACY_MASK_MATCH_PADDING = 0.02f
private const val PRIVACY_MASK_COLOR_LUMA_ALPHA = 0.18f
private const val PRIVACY_MASK_COLOR_CHROMA_ALPHA = 0.07f
private const val PRIVACY_MASK_COLOR_MAX_LUMA_STEP = 0.045f
private const val PRIVACY_MASK_COLOR_MAX_CHROMA_STEP = 0.018f
private const val PRIVACY_MASK_COLOR_CHROMA_SCALE = 1.10f
private const val PRIVACY_MASK_SAMPLE_RADIUS_DIVISOR = 18f
private const val PRIVACY_MASK_DEFAULT_R = 0.76f
private const val PRIVACY_MASK_DEFAULT_G = 0.65f
private const val PRIVACY_MASK_DEFAULT_B = 0.58f

/**
 * 비동기 파이프라인: FBO 렌더링 → PBO readback → 검출 콜백 → (프리뷰/인코더 분기).
 * 인코더에는 분석이 완료된 동일 프레임 텍스처만 전달해 privacy mask와 원본 프레임이 어긋나지 않게 유지한다.
 */
class VideoRenderer(
    /**
     * 분석 콜백. SurfaceTexture frame 의 native timestamp(nanoseconds, elapsedRealtimeNanos 기준)도 함께 전달한다.
     * 인코더 PTS와 동일 타임라인이므로 metadata pts_us 를 정확히 동기화할 때 사용.
     */
    private val onFrameCaptured: (ByteBuffer, Int, Int, Long) -> ProcessedFrame,
    private val onSurfaceReady: (Surface) -> Unit,
    private val onRendererReleased: (() -> Unit)? = null,
    private val encoderThread: EncoderThread = EncoderThread(),
) : android.opengl.GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private val texMatrix = FloatArray(16)
    private val finalTexMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val program = OESTextureProgram()
    private val privacyMaskProgram = MosaicProgram()
    private val pboReader = PBOReader()

    // 프리뷰/분석용 더블 버퍼 FBO (PBO readback과 같은 프레임을 유지)
    private val previewSourceFboIds = IntArray(2)
    private val previewSourceTextureIds = IntArray(2)
    private var previewSourceWriteIndex = 0
    private var previewDisplayTextureId = 0
    private var isPreviewSynchronizedToAnalysis = false

    // 인코더용 더블 버퍼 FBO (EncoderThread 읽기와 쓰기 충돌 방지)
    private val encoderFboIds = IntArray(2)
    private val encoderFboTextureIds = IntArray(2)
    private var encoderFboWriteIndex = 0

    private var viewWidth = 0
    private var viewHeight = 0
    private var renderContentScaleX = 1f
    private var renderContentScaleY = 1f

    @Volatile
    private var contentScaleDirty = true

    @Volatile
    private var frameAvailable = false

    @Volatile
    private var videoWidth = 0

    @Volatile
    private var videoHeight = 0

    @Volatile
    private var inputSurfaceWidth = 0

    @Volatile
    private var inputSurfaceHeight = 0

    @Volatile
    private var previewRotationDegrees = 0

    @Volatile
    private var isFrontLensFacing = false

    // --- 실시간 인코더 연동용 ---
    @Volatile
    private var encoderSurface: Surface? = null

    @Volatile
    private var encoderWidth: Int = 0

    @Volatile
    private var encoderHeight: Int = 0

    private var recordingEnabled: Boolean = false
    private var lastEncoderTimestampNs: Long = Long.MIN_VALUE
    private var lastAnalysisTimestampNs: Long = Long.MIN_VALUE
    private var lastFrameTimestampNs: Long = Long.MIN_VALUE

    /**
     * PBO는 1 frame 지연되어 픽셀을 반환한다(N번째 draw에서 N-1번째 frame의 pixel을 받음).
     * 따라서 analysisBuffer 에 대응하는 SurfaceTexture timestamp 는 "현재" frame timestamp 가 아닌
     * "이전 frame" 의 timestamp. metadata pts 를 인코더 PTS 와 정확히 일치시키기 위해 보관.
     */
    private var pendingAnalysisFrameTimestampNs: Long = 0L
    private var previousPrivacyEllipses: List<EllipseParams> = emptyList()
    private val privacyMaskColorsByTrackId = LinkedHashMap<Int, PrivacyMaskColorState>()

    /** 영상 해상도 설정 시 FBO에 fit(letter-box)로 렌더하여 종횡비 왜곡 제거. 0이면 보정 없음. */
    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        contentScaleDirty = true
    }

    fun setInputSurfaceSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        inputSurfaceWidth = width
        inputSurfaceHeight = height
        surfaceTexture?.setDefaultBufferSize(width, height)
    }

    fun setPreviewRotationDegrees(degrees: Int) {
        previewRotationDegrees = normalizeRotationDegrees(degrees)
    }

    fun setFrontLensFacing(isFront: Boolean) {
        isFrontLensFacing = isFront
    }

    // ExoPlayer.setVideoSurface()는 메인 스레드에서만 호출 가능
    private val mainHandler = Handler(Looper.getMainLooper())

    // GLSurfaceView 참조 (requestRender용)
    private var glSurfaceViewRef: android.opengl.GLSurfaceView? = null

    fun setGLSurfaceView(view: android.opengl.GLSurfaceView) {
        glSurfaceViewRef = view
    }

    /**
     * RealTimeRecorder에서 전달된 인코더 입력 Surface 설정.
     *
     * - 반드시 GLSurfaceView.queueEvent를 통해 GL 스레드에서 호출해야 한다.
     * - null을 전달하면 녹화를 중지하고 EGLSurface를 정리한다.
     */
    fun setEncoderSurface(
        surface: Surface?,
        width: Int = 0,
        height: Int = 0,
    ) {
        if (surface == null) {
            recordingEnabled = false
            encoderSurface = null
            encoderWidth = 0
            encoderHeight = 0
            lastEncoderTimestampNs = Long.MIN_VALUE
            previousPrivacyEllipses = emptyList()
            privacyMaskColorsByTrackId.clear()
            encoderThread.stop()
        } else {
            if (encoderSurface !== surface) {
                encoderThread.stop()
                lastEncoderTimestampNs = Long.MIN_VALUE
                previousPrivacyEllipses = emptyList()
                privacyMaskColorsByTrackId.clear()
            }
            encoderSurface = surface
            encoderWidth = width
            encoderHeight = height
            recordingEnabled = true
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        // OES 텍스처 생성
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // SurfaceTexture → Surface → ExoPlayer에 전달
        surfaceTexture = SurfaceTexture(oesTextureId).also {
            it.setOnFrameAvailableListener(this)
            if (inputSurfaceWidth > 0 && inputSurfaceHeight > 0) {
                it.setDefaultBufferSize(inputSurfaceWidth, inputSurfaceHeight)
            }
        }
        surface = Surface(surfaceTexture)

        program.init()
        privacyMaskProgram.init()

        // GL 스레드 → 메인 스레드 전환
        val readySurface = surface!!
        mainHandler.post { onSurfaceReady(readySurface) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        contentScaleDirty = true
        GLES30.glViewport(0, 0, width, height)

        // 기존 FBO 및 픽셀 읽기 상태 정리
        pboReader.release()
        releaseFramebuffers()
        resetAnalysisState()

        // 프리뷰/분석용 더블 버퍼 FBO 생성
        GLES30.glGenTextures(2, previewSourceTextureIds, 0)
        GLES30.glGenFramebuffers(2, previewSourceFboIds, 0)
        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, previewSourceTextureIds[i])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                width,
                height,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previewSourceFboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                previewSourceTextureIds[i],
                0
            )
        }

        // 인코더용 더블 버퍼 FBO 생성
        GLES30.glGenTextures(2, encoderFboTextureIds, 0)
        GLES30.glGenFramebuffers(2, encoderFboIds, 0)
        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, encoderFboTextureIds[i])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, encoderFboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                encoderFboTextureIds[i],
                0
            )
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        previewSourceWriteIndex = 0
        previewDisplayTextureId = 0
        encoderFboWriteIndex = 0

        if (width > 0 && height > 0) {
            pboReader.init(width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (frameAvailable) {
            frameAvailable = false

            // 1. SurfaceTexture 업데이트
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(texMatrix)
            val frameTimestampNs = surfaceTexture?.timestamp ?: 0L
            if (hasAnalysisTimestampReset(lastFrameTimestampNs, frameTimestampNs)) {
                resetAnalysisPipeline()
            }
            rememberFrameTimestamp(frameTimestampNs)
            val sourceTexMatrix = buildSourceTexMatrix(
                baseMatrix = texMatrix,
                rotationDegrees = previewRotationDegrees,
                isFrontLens = isFrontLensFacing,
            )

            // 2. OES → 프리뷰 FBO 렌더링
            if (contentScaleDirty) {
                updateRenderContentScale()
            }
            val scaleX = renderContentScaleX
            val scaleY = renderContentScaleY
            val currentPreviewBufferIndex = previewSourceWriteIndex
            val analysisPreviewBufferIndex = nextEncoderBufferIndex(currentPreviewBufferIndex)
            val currentPreviewTextureId = previewSourceTextureIds[currentPreviewBufferIndex]
            val analysisPreviewTextureId = previewSourceTextureIds[analysisPreviewBufferIndex]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previewSourceFboIds[currentPreviewBufferIndex])
            GLES30.glViewport(0, 0, viewWidth, viewHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            program.drawOES(oesTextureId, sourceTexMatrix, scaleX, scaleY)

            // 3. PBO를 사용해 비동기 픽셀 읽기를 요청하고, 이전 프레임 결과만 분석한다.
            var processedFrame: ProcessedFrame? = null
            var encoderTextureIdForSubmit = 0
            val analysisBuffer = pboReader.readPixelsAsync()
            if (analysisBuffer != null && (recordingEnabled || shouldAnalyzeFrame(frameTimestampNs))) {
                // analysisBuffer 는 PBO 1 frame 지연으로 "이전 frame" 의 pixel.
                // 그래서 그 frame 의 SurfaceTexture timestamp 를 박아야 인코더 PTS 와 정확히 매칭됨.
                processedFrame = onFrameCaptured(
                    analysisBuffer,
                    viewWidth,
                    viewHeight,
                    pendingAnalysisFrameTimestampNs,
                )
                lastAnalysisTimestampNs = resolveAnalysisTimestampNs(frameTimestampNs)
            }
            pendingAnalysisFrameTimestampNs = frameTimestampNs
            val previewSelection = resolvePreviewFrameSelection(
                recordingEnabled = recordingEnabled,
                processedFrame = processedFrame,
                currentPreviewTextureId = currentPreviewTextureId,
                analysisPreviewTextureId = analysisPreviewTextureId,
                previousPreviewTextureId = previewDisplayTextureId,
                wasSynchronized = isPreviewSynchronizedToAnalysis,
            )
            previewDisplayTextureId = previewSelection.textureId
            isPreviewSynchronizedToAnalysis = previewSelection.isSynchronized
            previewSourceWriteIndex = analysisPreviewBufferIndex

            // 4. 인코더용 FBO: 원본 프레임 위에 피부색 mask를 타원 영역에 합성한다.
            val frameForRecording = processedFrame
            val frameAnalysisBuffer = analysisBuffer
            if (recordingEnabled && frameForRecording != null && frameAnalysisBuffer != null) {
                encoderFboWriteIndex = nextEncoderBufferIndex(encoderFboWriteIndex)
                val ellipses = stabilizePrivacyEllipses(
                    previousEllipses = previousPrivacyEllipses,
                    currentEllipses = buildColoredPrivacyEllipses(
                        frame = frameForRecording,
                        rgbaBuffer = frameAnalysisBuffer,
                    ),
                )
                previousPrivacyEllipses = ellipses
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, encoderFboIds[encoderFboWriteIndex])
                GLES30.glViewport(0, 0, viewWidth, viewHeight)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                program.draw2DBlend(analysisPreviewTextureId)
                if (ellipses.isNotEmpty()) {
                    val maskRegion = calculatePrivacyMaskRegion(ellipses, viewWidth, viewHeight)
                    if (maskRegion != null) {
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, encoderFboIds[encoderFboWriteIndex])
                        GLES30.glViewport(0, 0, viewWidth, viewHeight)
                        privacyMaskProgram.compositeMaskedRegion(
                            textureId = analysisPreviewTextureId,
                            ellipses = ellipses,
                            regionRect = maskRegion.regionRect,
                        )
                    }
                }
                encoderTextureIdForSubmit = encoderFboTextureIds[encoderFboWriteIndex]
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            // 얼굴 미검출 프레임도 녹화는 지속한다.
            if (shouldSubmitFrameForRecording(recordingEnabled, processedFrame) && encoderTextureIdForSubmit != 0) {
                submitFrameToEncoderThread(
                    textureId = encoderTextureIdForSubmit,
                    frameTimestampNs = frameTimestampNs,
                )
            }
        }

        // 7. 프리뷰 FBO → 화면 렌더링 (검출 완료 후 표시)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (previewDisplayTextureId != 0) {
            program.draw2D(previewDisplayTextureId)
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        frameAvailable = true
        glSurfaceViewRef?.requestRender()
    }

    fun release() {
        recordingEnabled = false
        encoderSurface = null
        encoderWidth = 0
        encoderHeight = 0
        encoderThread.stop()

        pboReader.release()
        privacyMaskProgram.release()
        program.release()
        releaseFramebuffers()
        if (oesTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
        }

        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
        resetAnalysisState()

        onRendererReleased?.invoke()
    }

    private fun submitFrameToEncoderThread(
        textureId: Int,
        frameTimestampNs: Long,
    ) {
        val targetSurface = encoderSurface ?: return
        val targetWidth = encoderWidth
        val targetHeight = encoderHeight
        if (targetWidth <= 0 || targetHeight <= 0) {
            Log.w(TAG, "encoder size invalid: ${targetWidth}x$targetHeight")
            return
        }

        ensureEncoderThreadStarted(targetSurface)
        if (!encoderThread.isRenderReady()) return

        val fenceSync = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        if (fenceSync == 0L) {
            Log.w(TAG, "glFenceSync 생성 실패")
            return
        }

        val baseTimestampNs = if (frameTimestampNs > 0L) frameTimestampNs else System.nanoTime()
        val timestampNs = if (lastEncoderTimestampNs == Long.MIN_VALUE) {
            baseTimestampNs
        } else {
            maxOf(baseTimestampNs, lastEncoderTimestampNs + 1_000L)
        }
        lastEncoderTimestampNs = timestampNs

        val encoderSourceScale = calculateEncoderSourceScale(
            sourceWidth = viewWidth,
            sourceHeight = viewHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        GLES30.glFlush()
        encoderThread.submitFrame(
            fboTextureId = textureId,
            fenceSync = fenceSync,
            timestampNs = timestampNs,
            width = targetWidth,
            height = targetHeight,
            contentScaleX = encoderSourceScale.first,
            contentScaleY = encoderSourceScale.second,
        )
    }

    private fun ensureEncoderThreadStarted(surface: Surface) {
        if (encoderThread.isRunning()) {
            if (encoderThread.isRenderReady()) return
            Log.w(TAG, "EncoderThread running but not ready. restart 시도")
            encoderThread.stop()
        }

        val sharedContext = EGL14.eglGetCurrentContext()
        if (sharedContext == EGL14.EGL_NO_CONTEXT) {
            Log.w(TAG, "shared EGLContext가 없어 EncoderThread 시작을 건너뜁니다.")
            return
        }

        try {
            encoderThread.start(
                encoderInputSurface = surface,
                sharedContext = sharedContext,
            )
        } catch (e: Exception) {
            recordingEnabled = false
            Log.e(TAG, "EncoderThread 시작 실패", e)
        }
    }

    private fun updateRenderContentScale() {
        renderContentScaleX = 1f
        renderContentScaleY = 1f

        if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            val scale = minOf(viewWidth / videoWidth.toFloat(), viewHeight / videoHeight.toFloat())
            val contentW = videoWidth * scale
            val contentH = videoHeight * scale
            renderContentScaleX = contentW / viewWidth.toFloat()
            renderContentScaleY = contentH / viewHeight.toFloat()
        }
        contentScaleDirty = false
    }

    private fun shouldAnalyzeFrame(frameTimestampNs: Long): Boolean {
        if (lastAnalysisTimestampNs == Long.MIN_VALUE) {
            return true
        }
        val safeTimestampNs = resolveAnalysisTimestampNs(frameTimestampNs)
        return safeTimestampNs - lastAnalysisTimestampNs >= ANALYSIS_INTERVAL_NS
    }

    private fun resetAnalysisPipeline() {
        pboReader.resetPipeline()
        previewSourceWriteIndex = 0
        isPreviewSynchronizedToAnalysis = false
        lastAnalysisTimestampNs = Long.MIN_VALUE
        lastFrameTimestampNs = Long.MIN_VALUE
        pendingAnalysisFrameTimestampNs = 0L
        previousPrivacyEllipses = emptyList()
        privacyMaskColorsByTrackId.clear()
    }

    private fun resetAnalysisState() {
        lastAnalysisTimestampNs = Long.MIN_VALUE
        lastFrameTimestampNs = Long.MIN_VALUE
        previewDisplayTextureId = 0
        previewSourceWriteIndex = 0
        isPreviewSynchronizedToAnalysis = false
        previousPrivacyEllipses = emptyList()
        privacyMaskColorsByTrackId.clear()
    }

    private fun rememberFrameTimestamp(frameTimestampNs: Long) {
        if (frameTimestampNs > 0L) {
            lastFrameTimestampNs = frameTimestampNs
        }
    }

    private fun resolveAnalysisTimestampNs(frameTimestampNs: Long): Long {
        return if (frameTimestampNs > 0L) {
            frameTimestampNs
        } else {
            System.nanoTime()
        }
    }

    private fun calculateEncoderSourceScale(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Pair<Float, Float> {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return 1f to 1f
        }

        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        if (sourceAspect > targetAspect) {
            val scaleX = (targetAspect / sourceAspect).coerceIn(0f, 1f)
            return scaleX to 1f
        }
        val scaleY = (sourceAspect / targetAspect).coerceIn(0f, 1f)
        return 1f to scaleY
    }

    private fun buildSourceTexMatrix(
        baseMatrix: FloatArray,
        rotationDegrees: Int,
        isFrontLens: Boolean,
    ): FloatArray {
        val normalizedRotation = normalizeRotationDegrees(rotationDegrees)
        if (normalizedRotation == 0) {
            return baseMatrix
        }

        Matrix.setIdentityM(rotationMatrix, 0)
        Matrix.translateM(rotationMatrix, 0, 0.5f, 0.5f, 0f)
        val signedRotation = if (isFrontLens) -normalizedRotation else normalizedRotation
        Matrix.rotateM(rotationMatrix, 0, signedRotation.toFloat(), 0f, 0f, 1f)
        Matrix.translateM(rotationMatrix, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(finalTexMatrix, 0, rotationMatrix, 0, baseMatrix, 0)
        return finalTexMatrix
    }

    private fun releaseFramebuffers() {
        if (previewSourceFboIds[0] != 0 || previewSourceFboIds[1] != 0) {
            GLES30.glDeleteFramebuffers(2, previewSourceFboIds, 0)
            previewSourceFboIds[0] = 0
            previewSourceFboIds[1] = 0
        }
        if (previewSourceTextureIds[0] != 0 || previewSourceTextureIds[1] != 0) {
            GLES30.glDeleteTextures(2, previewSourceTextureIds, 0)
            previewSourceTextureIds[0] = 0
            previewSourceTextureIds[1] = 0
        }
        if (encoderFboIds[0] != 0 || encoderFboIds[1] != 0) {
            GLES30.glDeleteFramebuffers(2, encoderFboIds, 0)
            encoderFboIds[0] = 0
            encoderFboIds[1] = 0
        }
        if (encoderFboTextureIds[0] != 0 || encoderFboTextureIds[1] != 0) {
            GLES30.glDeleteTextures(2, encoderFboTextureIds, 0)
            encoderFboTextureIds[0] = 0
            encoderFboTextureIds[1] = 0
        }
    }

    private companion object {
        private const val TAG = "VideoRenderer"
        private const val ANALYSIS_INTERVAL_NS = 50_000_000L

        private fun normalizeRotationDegrees(degrees: Int): Int {
            val normalized = ((degrees % 360) + 360) % 360
            return when (normalized) {
                90, 180, 270 -> normalized
                else -> 0
            }
        }
    }

    private fun buildColoredPrivacyEllipses(
        frame: ProcessedFrame,
        rgbaBuffer: ByteBuffer,
    ): List<EllipseParams> {
        val baseEllipses = FaceEllipseCalculator.calculate(frame)
        if (baseEllipses.isEmpty()) return emptyList()

        val coloredEllipses = ArrayList<EllipseParams>(baseEllipses.size)
        var ellipseIndex = 0
        for (faceIndex in frame.faces.indices) {
            if (ellipseIndex >= baseEllipses.size) break
            if (frame.faceLabels.getOrNull(faceIndex) == true) continue

            val trackId = frame.trackingIds.getOrElse(faceIndex) { faceIndex }
            val measuredColor = measurePrivacyMaskColor(
                rgbaBuffer = rgbaBuffer,
                frameWidth = frame.frameWidth,
                frameHeight = frame.frameHeight,
                face = frame.faces[faceIndex],
            )
            val colorState = resolvePrivacyMaskColorState(
                previousState = privacyMaskColorsByTrackId[trackId],
                measuredColor = measuredColor,
            )
            privacyMaskColorsByTrackId[trackId] = colorState
            val stabilizedColor = colorState.toRgbColor()
            val ellipse = baseEllipses[ellipseIndex]
            coloredEllipses += ellipse.copy(
                maskColorR = stabilizedColor.r,
                maskColorG = stabilizedColor.g,
                maskColorB = stabilizedColor.b,
            )
            ellipseIndex++
        }

        while (ellipseIndex < baseEllipses.size) {
            coloredEllipses += baseEllipses[ellipseIndex]
            ellipseIndex++
        }

        return coloredEllipses
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun shouldSubmitFrameForRecording(
    recordingEnabled: Boolean,
    processedFrame: ProcessedFrame?
): Boolean = recordingEnabled && processedFrame != null

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun hasAnalysisTimestampReset(lastFrameTimestampNs: Long, frameTimestampNs: Long): Boolean {
    return lastFrameTimestampNs != Long.MIN_VALUE &&
        frameTimestampNs > 0L &&
        frameTimestampNs < lastFrameTimestampNs
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun nextEncoderBufferIndex(currentIndex: Int): Int = 1 - currentIndex

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
data class PreviewFrameSelection(
    val textureId: Int,
    val isSynchronized: Boolean,
)

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun resolvePreviewFrameSelection(
    recordingEnabled: Boolean,
    processedFrame: ProcessedFrame?,
    currentPreviewTextureId: Int,
    analysisPreviewTextureId: Int,
    previousPreviewTextureId: Int,
    wasSynchronized: Boolean,
): PreviewFrameSelection {
    if (!recordingEnabled) {
        return PreviewFrameSelection(
            textureId = currentPreviewTextureId,
            isSynchronized = false,
        )
    }
    if (processedFrame != null && analysisPreviewTextureId != 0) {
        return PreviewFrameSelection(
            textureId = analysisPreviewTextureId,
            isSynchronized = true,
        )
    }
    if (wasSynchronized && previousPreviewTextureId != 0) {
        return PreviewFrameSelection(
            textureId = previousPreviewTextureId,
            isSynchronized = true,
        )
    }
    return PreviewFrameSelection(
        textureId = currentPreviewTextureId,
        isSynchronized = false,
    )
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
data class PrivacyMaskRegion(
    val regionRect: UvRect,
)

private data class PrivacyMaskColorState(
    val y: Float,
    val cb: Float,
    val cr: Float,
) {
    fun toRgbColor(): PrivacyMaskRgbColor = yCbCrToRgbColor(y = y, cb = cb, cr = cr)
}

private data class PrivacyMaskRgbColor(
    val r: Float,
    val g: Float,
    val b: Float,
)

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun calculatePrivacyMaskRegion(
    ellipses: List<EllipseParams>,
    viewWidth: Int,
    viewHeight: Int,
): PrivacyMaskRegion? {
    if (ellipses.isEmpty() || viewWidth <= 0 || viewHeight <= 0) return null

    var leftPx = viewWidth
    var topPx = viewHeight
    var rightPx = 0
    var bottomPx = 0

    ellipses.forEach { ellipse ->
        val paddedLeftRadiusX = ellipse.leftRadiusX * PRIVACY_REGION_PADDING_RATIO
        val paddedRightRadiusX = ellipse.rightRadiusX * PRIVACY_REGION_PADDING_RATIO
        val paddedRadiusY = ellipse.radiusY * PRIVACY_REGION_PADDING_RATIO
        val candidateLeft = floor((ellipse.centerX - paddedLeftRadiusX) * viewWidth).toInt() - PRIVACY_REGION_PADDING_PX
        val candidateTop = floor((ellipse.centerY - paddedRadiusY) * viewHeight).toInt() - PRIVACY_REGION_PADDING_PX
        val candidateRight = ceil((ellipse.centerX + paddedRightRadiusX) * viewWidth).toInt() + PRIVACY_REGION_PADDING_PX
        val candidateBottom = ceil((ellipse.centerY + paddedRadiusY) * viewHeight).toInt() + PRIVACY_REGION_PADDING_PX

        leftPx = minOf(leftPx, candidateLeft)
        topPx = minOf(topPx, candidateTop)
        rightPx = maxOf(rightPx, candidateRight)
        bottomPx = maxOf(bottomPx, candidateBottom)
    }

    val clampedLeft = leftPx.coerceIn(0, viewWidth - 1)
    val clampedTop = topPx.coerceIn(0, viewHeight - 1)
    val clampedRight = rightPx.coerceIn(clampedLeft + 1, viewWidth)
    val clampedBottom = bottomPx.coerceIn(clampedTop + 1, viewHeight)
    return PrivacyMaskRegion(
        regionRect = UvRect(
            minX = clampedLeft / viewWidth.toFloat(),
            minY = clampedTop / viewHeight.toFloat(),
            maxX = clampedRight / viewWidth.toFloat(),
            maxY = clampedBottom / viewHeight.toFloat(),
        ),
    )
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun stabilizePrivacyEllipses(
    previousEllipses: List<EllipseParams>,
    currentEllipses: List<EllipseParams>,
    shapeSmoothingFactor: Float = PRIVACY_MASK_SHAPE_SMOOTHING_FACTOR,
): List<EllipseParams> {
    if (previousEllipses.isEmpty() || currentEllipses.isEmpty()) return currentEllipses

    val usedPrevious = BooleanArray(previousEllipses.size)
    return currentEllipses.map { current ->
        val matchedByTracking = previousEllipses.indices.firstOrNull { index ->
            !usedPrevious[index] &&
                current.trackingId >= 0 &&
                previousEllipses[index].trackingId == current.trackingId
        }
        val matchedIndex = matchedByTracking ?: previousEllipses.indices
            .filter { index ->
                !usedPrevious[index] && isPrivacyEllipseMatch(previousEllipses[index], current)
            }
            .minByOrNull { index ->
                privacyEllipseCenterDistanceSq(previousEllipses[index], current)
            }

        if (matchedIndex == null) {
            current
        } else {
            usedPrevious[matchedIndex] = true
            stabilizePrivacyEllipseShape(
                previous = previousEllipses[matchedIndex],
                current = current,
                shapeSmoothingFactor = shapeSmoothingFactor,
            )
        }
    }
}

private fun measurePrivacyMaskColor(
    rgbaBuffer: ByteBuffer,
    frameWidth: Int,
    frameHeight: Int,
    face: DetectedFace,
): PrivacyMaskRgbColor? {
    if (frameWidth <= 0 || frameHeight <= 0) return null

    val landmarks = face.landmarks
    val eyeCenterY = landmarks?.getEyeCenter()?.y ?: (face.y + face.height * 0.34f)
    val mouthCenterY = landmarks?.getMouthCenter()?.y ?: (face.y + face.height * 0.74f)
    val cheekUpperY = lerp(eyeCenterY, mouthCenterY, 0.48f)
    val cheekMidY = lerp(eyeCenterY, mouthCenterY, 0.62f)
    val cheekLowerY = lerp(eyeCenterY, mouthCenterY, 0.76f)
    val faceCenterX = face.x + face.width / 2f
    val horizontalInset = face.width * 0.16f
    val wideInset = face.width * 0.22f
    val outerInset = face.width * 0.26f
    val radius = (minOf(face.width, face.height) / PRIVACY_MASK_SAMPLE_RADIUS_DIVISOR).roundToInt().coerceIn(2, 5)

    val sampleCenters = listOf(
        faceCenterX - wideInset to cheekUpperY,
        faceCenterX + wideInset to cheekUpperY,
        faceCenterX - outerInset to cheekMidY,
        faceCenterX + outerInset to cheekMidY,
        faceCenterX - horizontalInset to cheekLowerY,
        faceCenterX + horizontalInset to cheekLowerY,
    )

    val candidates = sampleCenters.mapNotNull { (sampleX, sampleY) ->
        sampleBufferColor(
            rgbaBuffer = rgbaBuffer,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            centerX = sampleX.roundToInt(),
            centerY = sampleY.roundToInt(),
            radius = radius,
        )
    }
    if (candidates.isEmpty()) return null

    val filtered = candidates.filter(::isAcceptablePrivacyMaskColor)
    val selected = if (filtered.size >= 3) filtered else candidates
    return sanitizePrivacyMaskColor(averagePrivacyMaskColor(selected))
}

private fun sampleBufferColor(
    rgbaBuffer: ByteBuffer,
    frameWidth: Int,
    frameHeight: Int,
    centerX: Int,
    centerY: Int,
    radius: Int,
): PrivacyMaskRgbColor? {
    var redSum = 0f
    var greenSum = 0f
    var blueSum = 0f
    var sampleCount = 0

    val safeLeft = (centerX - radius).coerceIn(0, frameWidth - 1)
    val safeRight = (centerX + radius).coerceIn(0, frameWidth - 1)
    val safeTop = (centerY - radius).coerceIn(0, frameHeight - 1)
    val safeBottom = (centerY + radius).coerceIn(0, frameHeight - 1)

    for (y in safeTop..safeBottom) {
        for (x in safeLeft..safeRight) {
            val pixelColor = readPrivacyMaskColor(
                rgbaBuffer = rgbaBuffer,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                x = x,
                y = y,
            ) ?: continue
            redSum += pixelColor.r
            greenSum += pixelColor.g
            blueSum += pixelColor.b
            sampleCount++
        }
    }

    if (sampleCount == 0) return null
    return PrivacyMaskRgbColor(
        r = redSum / sampleCount,
        g = greenSum / sampleCount,
        b = blueSum / sampleCount,
    )
}

private fun readPrivacyMaskColor(
    rgbaBuffer: ByteBuffer,
    frameWidth: Int,
    frameHeight: Int,
    x: Int,
    y: Int,
): PrivacyMaskRgbColor? {
    if (x !in 0 until frameWidth || y !in 0 until frameHeight) return null
    val baseIndex = ((y * frameWidth) + x) * 4
    if (baseIndex + 3 >= rgbaBuffer.limit()) return null

    val red = (rgbaBuffer.get(baseIndex).toInt() and 0xFF) / 255f
    val green = (rgbaBuffer.get(baseIndex + 1).toInt() and 0xFF) / 255f
    val blue = (rgbaBuffer.get(baseIndex + 2).toInt() and 0xFF) / 255f
    return PrivacyMaskRgbColor(red, green, blue)
}

private fun averagePrivacyMaskColor(colors: List<PrivacyMaskRgbColor>): PrivacyMaskRgbColor {
    var redSum = 0f
    var greenSum = 0f
    var blueSum = 0f
    colors.forEach { color ->
        redSum += color.r
        greenSum += color.g
        blueSum += color.b
    }
    val count = colors.size.coerceAtLeast(1)
    return PrivacyMaskRgbColor(
        r = redSum / count,
        g = greenSum / count,
        b = blueSum / count,
    )
}

private fun isAcceptablePrivacyMaskColor(color: PrivacyMaskRgbColor): Boolean {
    val yCbCr = rgbToYCbCr(color)
    val maxChannel = maxOf(color.r, color.g, color.b)
    val minChannel = minOf(color.r, color.g, color.b)
    val saturation = if (maxChannel <= 1e-6f) 0f else (maxChannel - minChannel) / maxChannel

    if (yCbCr.y !in 0.18f..0.90f) return false
    if (saturation > 0.55f) return false
    if (color.r > color.g * 1.28f && color.r > color.b * 1.35f && saturation > 0.18f) return false
    if (color.b > color.r * 1.20f && saturation > 0.18f) return false
    return true
}

private fun sanitizePrivacyMaskColor(color: PrivacyMaskRgbColor): PrivacyMaskRgbColor {
    val yCbCr = rgbToYCbCr(color)
    val sanitized = yCbCrToRgbColor(
        y = yCbCr.y.coerceIn(0.24f, 0.86f),
        cb = (yCbCr.cb * PRIVACY_MASK_COLOR_CHROMA_SCALE).coerceIn(-0.16f, 0.20f),
        cr = (yCbCr.cr * PRIVACY_MASK_COLOR_CHROMA_SCALE).coerceIn(-0.10f, 0.26f),
    )
    return blendPrivacyMaskRgbColor(
        base = PrivacyMaskRgbColor(
            r = PRIVACY_MASK_DEFAULT_R,
            g = PRIVACY_MASK_DEFAULT_G,
            b = PRIVACY_MASK_DEFAULT_B,
        ),
        measured = sanitized,
        measuredWeight = 0.96f,
    )
}

private fun resolvePrivacyMaskColorState(
    previousState: PrivacyMaskColorState?,
    measuredColor: PrivacyMaskRgbColor?,
): PrivacyMaskColorState {
    val measuredState = measuredColor?.let(::rgbToYCbCrState) ?: previousState ?: defaultPrivacyMaskColorState()
    if (previousState == null) return measuredState
    if (measuredColor == null) return previousState

    return PrivacyMaskColorState(
        y = easedPrivacyMaskChannel(
            previousValue = previousState.y,
            measuredValue = measuredState.y,
            alpha = PRIVACY_MASK_COLOR_LUMA_ALPHA,
            maxStep = PRIVACY_MASK_COLOR_MAX_LUMA_STEP,
        ),
        cb = easedPrivacyMaskChannel(
            previousValue = previousState.cb,
            measuredValue = measuredState.cb,
            alpha = PRIVACY_MASK_COLOR_CHROMA_ALPHA,
            maxStep = PRIVACY_MASK_COLOR_MAX_CHROMA_STEP,
        ),
        cr = easedPrivacyMaskChannel(
            previousValue = previousState.cr,
            measuredValue = measuredState.cr,
            alpha = PRIVACY_MASK_COLOR_CHROMA_ALPHA,
            maxStep = PRIVACY_MASK_COLOR_MAX_CHROMA_STEP,
        ),
    )
}

private fun easedPrivacyMaskChannel(
    previousValue: Float,
    measuredValue: Float,
    alpha: Float,
    maxStep: Float,
): Float {
    val target = lerp(previousValue, measuredValue, alpha)
    return previousValue + (target - previousValue).coerceIn(-maxStep, maxStep)
}

private fun rgbToYCbCrState(color: PrivacyMaskRgbColor): PrivacyMaskColorState {
    val yCbCr = rgbToYCbCr(color)
    return PrivacyMaskColorState(
        y = yCbCr.y.coerceIn(0.24f, 0.86f),
        cb = (yCbCr.cb * PRIVACY_MASK_COLOR_CHROMA_SCALE).coerceIn(-0.16f, 0.20f),
        cr = (yCbCr.cr * PRIVACY_MASK_COLOR_CHROMA_SCALE).coerceIn(-0.10f, 0.26f),
    )
}

private fun defaultPrivacyMaskColorState(): PrivacyMaskColorState {
    return rgbToYCbCrState(
        PrivacyMaskRgbColor(
            r = PRIVACY_MASK_DEFAULT_R,
            g = PRIVACY_MASK_DEFAULT_G,
            b = PRIVACY_MASK_DEFAULT_B,
        )
    )
}

private data class PrivacyMaskYCbCr(
    val y: Float,
    val cb: Float,
    val cr: Float,
)

private fun rgbToYCbCr(color: PrivacyMaskRgbColor): PrivacyMaskYCbCr {
    val y = 0.299f * color.r + 0.587f * color.g + 0.114f * color.b
    val cb = 0.564f * (color.b - y)
    val cr = 0.713f * (color.r - y)
    return PrivacyMaskYCbCr(y = y, cb = cb, cr = cr)
}

private fun yCbCrToRgbColor(
    y: Float,
    cb: Float,
    cr: Float,
): PrivacyMaskRgbColor {
    val red = (y + 1.403f * cr).coerceIn(0f, 1f)
    val green = (y - 0.344f * cb - 0.714f * cr).coerceIn(0f, 1f)
    val blue = (y + 1.773f * cb).coerceIn(0f, 1f)
    return PrivacyMaskRgbColor(r = red, g = green, b = blue)
}

private fun blendPrivacyMaskRgbColor(
    base: PrivacyMaskRgbColor,
    measured: PrivacyMaskRgbColor,
    measuredWeight: Float,
): PrivacyMaskRgbColor {
    val t = measuredWeight.coerceIn(0f, 1f)
    return PrivacyMaskRgbColor(
        r = lerp(base.r, measured.r, t),
        g = lerp(base.g, measured.g, t),
        b = lerp(base.b, measured.b, t),
    )
}

private fun isPrivacyEllipseMatch(
    previous: EllipseParams,
    current: EllipseParams,
): Boolean {
    val horizontalThreshold = maxOf(
        previous.leftRadiusX,
        previous.rightRadiusX,
        current.leftRadiusX,
        current.rightRadiusX,
    ) * PRIVACY_MASK_MATCH_RADIUS_MULTIPLIER + PRIVACY_MASK_MATCH_PADDING
    val verticalThreshold = maxOf(previous.radiusY, current.radiusY) *
        PRIVACY_MASK_MATCH_RADIUS_MULTIPLIER + PRIVACY_MASK_MATCH_PADDING
    return abs(previous.centerX - current.centerX) <= horizontalThreshold &&
        abs(previous.centerY - current.centerY) <= verticalThreshold
}

private fun privacyEllipseCenterDistanceSq(
    previous: EllipseParams,
    current: EllipseParams,
): Float {
    val dx = previous.centerX - current.centerX
    val dy = previous.centerY - current.centerY
    return dx * dx + dy * dy
}

private fun stabilizePrivacyEllipseShape(
    previous: EllipseParams,
    current: EllipseParams,
    shapeSmoothingFactor: Float,
): EllipseParams {
    val profileStrength = maxOf(previous.profileStrength, current.profileStrength).coerceIn(0f, 1f)
    val radiusT = lerp(shapeSmoothingFactor.coerceIn(0f, 1f), 0.58f, profileStrength)
    val positionT = lerp(0.96f, 0.74f, profileStrength)
    val angleT = lerp(0.38f, 0.52f, profileStrength)
    val topClipT = lerp(shapeSmoothingFactor.coerceIn(0f, 1f), 0.62f, profileStrength)
    return EllipseParams(
        trackingId = current.trackingId,
        profileStrength = current.profileStrength,
        centerX = lerp(previous.centerX, current.centerX, positionT),
        centerY = lerp(previous.centerY, current.centerY, positionT),
        radiusX = lerp(previous.radiusX, current.radiusX, radiusT),
        radiusY = lerp(previous.radiusY, current.radiusY, radiusT),
        angle = lerp(previous.angle, current.angle, angleT),
        topClip = lerp(previous.topClip, current.topClip, topClipT),
        leftRadiusX = lerp(previous.leftRadiusX, current.leftRadiusX, radiusT),
        rightRadiusX = lerp(previous.rightRadiusX, current.rightRadiusX, radiusT),
        maskColorR = current.maskColorR,
        maskColorG = current.maskColorG,
        maskColorB = current.maskColorB,
    )
}

private fun lerp(start: Float, end: Float, t: Float): Float {
    return start + (end - start) * t.coerceIn(0f, 1f)
}
