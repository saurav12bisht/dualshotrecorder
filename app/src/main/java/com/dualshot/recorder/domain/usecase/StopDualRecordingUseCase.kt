package com.dualshot.recorder.domain.usecase

import com.dualshot.recorder.domain.model.VideoFile
import com.dualshot.recorder.domain.repository.CameraRepository
import javax.inject.Inject

/**
 * Stops dual recording and persists output files to MediaStore.
 */
class StopDualRecordingUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    /**
     * @return Saved [VideoFile] list (vertical + horizontal) on success.
     */
    suspend operator fun invoke(): Result<List<VideoFile>> {
        return cameraRepository.stopRecording()
    }
}
