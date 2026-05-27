package com.dualshot.recorder.domain.usecase

import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.domain.repository.CameraRepository
import javax.inject.Inject

/**
 * Starts simultaneous vertical and horizontal recording.
 */
class StartDualRecordingUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    /**
     * @param config Active recording configuration.
     * @return [Result] indicating whether recording started successfully.
     */
    suspend operator fun invoke(config: DualRecordingConfig): Result<Unit> {
        return cameraRepository.startRecording(config)
    }
}
