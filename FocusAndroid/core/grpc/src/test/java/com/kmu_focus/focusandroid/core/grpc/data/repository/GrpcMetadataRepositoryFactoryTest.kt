package com.kmu_focus.focusandroid.core.grpc.data.repository

import io.mockk.mockk
import javax.inject.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GrpcMetadataRepositoryFactoryTest {

    @Test
    fun `create는 provider에서 세션별 repository wrapper를 요청한다`() {
        val firstRepository = mockk<GrpcMetadataRepositoryImpl>()
        val secondRepository = mockk<GrpcMetadataRepositoryImpl>()
        var calls = 0
        val factory = GrpcMetadataRepositoryFactory(
            repositoryProvider = Provider {
                calls += 1
                when (calls) {
                    1 -> firstRepository
                    else -> secondRepository
                }
            },
        )

        assertSame(firstRepository, factory.create())
        assertSame(secondRepository, factory.create())
        assertEquals(2, calls)
    }
}
