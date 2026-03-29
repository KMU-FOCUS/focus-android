package com.kmu_focus.focusandroid.feature.auth.di

import com.kmu_focus.focusandroid.feature.auth.data.repository.KakaoAuthRepositoryImpl
import com.kmu_focus.focusandroid.feature.auth.domain.repository.KakaoAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindKakaoAuthRepository(
        impl: KakaoAuthRepositoryImpl,
    ): KakaoAuthRepository
}
