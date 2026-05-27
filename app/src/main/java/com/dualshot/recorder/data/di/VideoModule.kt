package com.dualshot.recorder.data.di

import com.dualshot.recorder.data.video.VideoRepositoryImpl
import com.dualshot.recorder.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing video repository bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VideoModule {

    /**
     * Binds [VideoRepositoryImpl] as [VideoRepository].
     */
    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
}
