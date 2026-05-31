package com.kmu_focus.focusandroid.core.grpc.di

import com.kmu_focus.focusandroid.core.grpc.data.repository.GrpcMetadataRepositoryFactory
import com.kmu_focus.focusandroid.core.metadata.domain.repository.LiveMetadataRepositoryFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GrpcMetadataRepositoryModule {
    @Binds
    abstract fun bindLiveMetadataRepositoryFactory(
        impl: GrpcMetadataRepositoryFactory,
    ): LiveMetadataRepositoryFactory
}
