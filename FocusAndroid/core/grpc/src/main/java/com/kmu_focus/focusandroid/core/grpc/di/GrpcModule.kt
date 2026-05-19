package com.kmu_focus.focusandroid.core.grpc.di

import android.util.Log
import com.kmu_focus.focusandroid.core.grpc.BuildConfig
import com.kmu_focus.focusandroid.core.grpc.data.remote.FaceMetadataStreamManager
import com.kmu_focus.focusandroid.core.grpc.proto.FaceMetadataIngestServiceGrpc
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GrpcModule {
    @Provides
    @Singleton
    fun provideManagedChannel(): ManagedChannel {
        val host = BuildConfig.GRPC_SERVER_HOST
        val port = BuildConfig.GRPC_SERVER_PORT
        val useTls = BuildConfig.GRPC_USE_TLS
        Log.i(
            "GrpcModule",
            "==== gRPC ENDPOINT ====\n" +
                "  host  = $host\n" +
                "  port  = $port\n" +
                "  TLS   = $useTls\n" +
                "=======================",
        )
        val builder = OkHttpChannelBuilder.forAddress(host, port)
        return if (useTls) {
            builder.useTransportSecurity().build()
        } else {
            builder.usePlaintext().build()
        }
    }

    @Provides
    @Singleton
    fun provideFaceMetadataStreamManager(
        channel: ManagedChannel,
    ): FaceMetadataStreamManager {
        return FaceMetadataStreamManager(
            FaceMetadataIngestServiceGrpc.newStub(channel),
        )
    }
}
