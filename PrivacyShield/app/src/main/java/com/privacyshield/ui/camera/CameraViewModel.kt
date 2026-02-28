package com.privacyshield.ui.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.lifecycle.*
import com.privacyshield.blur.BlurMode
import com.privacyshield.camera.CameraManager
import com.privacyshield.detection.DetectionResult
import com.privacyshield.export.SafeShareManager
import com.privacyshield.export.SharePurpose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CameraUiState(
    val blurMode: BlurMode           = BlurMode.BLACK_RECTANGLE,
    val privacyModeOn: Boolean       = true,
    val safeShareBitmap: Bitmap?     = null,
    val detections: List<DetectionResult> = emptyList()
)

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var cameraManager: CameraManager? = null
    private var safeShareManager: SafeShareManager? = null

    fun initCamera(context: Context) {
        cameraManager    = CameraManager(context, viewModelScope)
        safeShareManager = SafeShareManager(context)
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cm = cameraManager ?: return
        cm.startCamera(lifecycleOwner, previewView)

        viewModelScope.launch {
            @Suppress("UNCHECKED_CAST")
            (cm.detectionResults as StateFlow<List<DetectionResult>>)
                .collect { results ->
                    _uiState.update { it.copy(detections = results) }
                }
        }
    }

    fun setBlurMode(mode: BlurMode) = _uiState.update { it.copy(blurMode = mode) }

    fun togglePrivacyMode() = _uiState.update { it.copy(privacyModeOn = !it.privacyModeOn) }

    fun clearSafeShareBitmap() {
        _uiState.value.safeShareBitmap?.recycle()
        _uiState.update { it.copy(safeShareBitmap = null) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager?.shutdown()
        safeShareManager?.destroy()
        clearSafeShareBitmap()
    }
}
