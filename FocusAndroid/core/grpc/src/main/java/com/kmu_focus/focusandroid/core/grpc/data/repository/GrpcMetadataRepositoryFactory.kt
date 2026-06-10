package com.kmu_focus.focusandroid.core.grpc.data.repository

import com.kmu_focus.focusandroid.core.metadata.domain.repository.LiveMetadataRepositoryFactory
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import javax.inject.Inject
import javax.inject.Provider

class GrpcMetadataRepositoryFactory @Inject constructor(
    private val repositoryProvider: Provider<GrpcMetadataRepositoryImpl>,
) : LiveMetadataRepositoryFactory {
    override fun create(): MetadataRepository = repositoryProvider.get()
}
