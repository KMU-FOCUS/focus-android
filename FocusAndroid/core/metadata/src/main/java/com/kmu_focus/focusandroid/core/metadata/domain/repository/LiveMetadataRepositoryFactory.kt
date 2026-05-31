package com.kmu_focus.focusandroid.core.metadata.domain.repository

/**
 * 라이브 메타데이터 세션마다 독립적인 sink wrapper를 생성한다.
 */
fun interface LiveMetadataRepositoryFactory {
    fun create(): MetadataRepository
}
