package com.dualshot.recorder.data.di

import com.dualshot.recorder.data.camera.CameraRepositoryImpl
import com.dualshot.recorder.domain.repository.CameraRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing camera repository bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    /**
     * Binds [CameraRepositoryImpl] as [CameraRepository].
     */
    @Binds
    @Singleton
    abstract fun bindCameraRepository(impl: CameraRepositoryImpl): CameraRepository
}
