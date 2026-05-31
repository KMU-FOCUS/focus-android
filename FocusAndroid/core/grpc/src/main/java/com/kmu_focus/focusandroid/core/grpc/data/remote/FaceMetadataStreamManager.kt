package com.kmu_focus.focusandroid.core.grpc.data.remote

import android.util.Log
import com.kmu_focus.focusandroid.core.grpc.proto.FaceMetadataIngestServiceGrpc
import com.kmu_focus.focusandroid.core.grpc.proto.PushFaceMetadataRequest
import com.kmu_focus.focusandroid.core.grpc.proto.PushFaceMetadataResponse
import io.grpc.stub.StreamObserver

class FaceMetadataStreamManager(
    private val asyncStub: FaceMetadataIngestServiceGrpc.FaceMetadataIngestServiceStub,
) {
    companion object {
        private const val TAG = "FaceMetaStream"
    }

    private val lock = Any()
    private var requestObserver: StreamObserver<PushFaceMetadataRequest>? = null
    private var currentSessionId: String? = null
    private var sentFrameCount = 0L

    fun start(sessionId: String) {
        synchronized(lock) {
            if (requestObserver != null && currentSessionId == sessionId) {
                return
            }

            requestObserver?.onCompleted()
            openStreamLocked(sessionId)
        }
    }

    fun sendFrame(frame: PushFaceMetadataRequest) {
        synchronized(lock) {
            if (requestObserver == null || currentSessionId != frame.sessionId) {
                requestObserver?.onCompleted()
                openStreamLocked(frame.sessionId)
            }

            try {
                requestObserver?.onNext(frame)
                sentFrameCount++
                if (sentFrameCount == 1L || sentFrameCount % 60L == 0L) {
                    Log.i(
                        TAG,
                        "sendFrame ok total=$sentFrameCount",
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "sendFrame failed", t)
                clearStreamLocked(requestObserver ?: return)
            }
        }
    }

    fun complete() {
        synchronized(lock) {
            Log.i(TAG, "complete total=$sentFrameCount")
            requestObserver?.onCompleted()
            requestObserver = null
            currentSessionId = null
            sentFrameCount = 0L
        }
    }

    private fun openStreamLocked(sessionId: String) {
        Log.i(TAG, "openStream")
        lateinit var nextRequestObserver: StreamObserver<PushFaceMetadataRequest>
        nextRequestObserver = asyncStub.pushFaceMetadata(
            object : StreamObserver<PushFaceMetadataResponse> {
                override fun onNext(value: PushFaceMetadataResponse) {
                    Log.i(
                        TAG,
                        "server response success=${value.success} " +
                            "received=${value.receivedFrames} accepted=${value.acceptedFrames} " +
                            "dropped=${value.droppedFrames}",
                    )
                }

                override fun onError(t: Throwable) {
                    Log.e(TAG, "stream onError", t)
                    synchronized(lock) {
                        clearStreamLocked(nextRequestObserver)
                    }
                }

                override fun onCompleted() {
                    Log.i(TAG, "stream onCompleted sent=$sentFrameCount")
                    synchronized(lock) {
                        clearStreamLocked(nextRequestObserver)
                    }
                }
            }
        )

        requestObserver = nextRequestObserver
        currentSessionId = sessionId
    }

    private fun clearStreamLocked(observer: StreamObserver<PushFaceMetadataRequest>) {
        if (requestObserver === observer) {
            requestObserver = null
            currentSessionId = null
        }
    }
}
