package com.dualshot.recorder.domain.usecase

import com.dualshot.recorder.domain.model.RecordingState
import com.dualshot.recorder.domain.repository.CameraRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes the current recording state as a [StateFlow].
 */
class GetRecordingStateUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    /** Active recording state flow from the camera repository. */
    operator fun invoke(): StateFlow<RecordingState> = cameraRepository.recordingState
}
