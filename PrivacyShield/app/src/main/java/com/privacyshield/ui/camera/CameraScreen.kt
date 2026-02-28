package com.privacyshield.ui.camera

import android.Manifest
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.privacyshield.blur.BlurMode
import com.privacyshield.detection.DetectionResult
import com.privacyshield.detection.FieldType

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateToChat: () -> Unit,
    viewModel: CameraViewModel = viewModel()
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState        by viewModel.uiState.collectAsState()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        viewModel.initCamera(context)
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermission.status.isGranted) {
            // ── Camera Preview ──────────────────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }.also { pv -> viewModel.startCamera(lifecycleOwner, pv) }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Detection Overlay ───────────────────────────────────────────
            if (uiState.privacyModeOn) {
                DetectionOverlayCanvas(
                    detections = uiState.detections,
                    blurMode   = uiState.blurMode,
                    modifier   = Modifier.fillMaxSize()
                )
            }

            // ── HUD ─────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Blur mode selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlurMode.entries.forEach { mode ->
                        FilterChip(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            selected = uiState.blurMode == mode,
                            onClick  = { viewModel.setBlurMode(mode) },
                            label    = { Text(mode.label) }
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = viewModel::togglePrivacyMode,
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (uiState.privacyModeOn)
                                MaterialTheme.colorScheme.primary
                            else Color.Gray
                        )
                    ) {
                        Text(if (uiState.privacyModeOn) "Privacy: ON" else "Privacy: OFF")
                    }

                    Button(onClick = { /* TODO: implement capture flow */ }) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Safe Share")
                    }

                    FilledTonalButton(onClick = onNavigateToChat) {
                        Icon(Icons.Default.Chat, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Assistant")
                    }
                }
            }

            // ── Field count badge ───────────────────────────────────────────
            if (uiState.detections.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "${uiState.detections.size} field(s) masked",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            // ── Permission denied UI ────────────────────────────────────────
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Camera permission required", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Privacy Shield needs camera access to detect and mask ID cards in real time.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun DetectionOverlayCanvas(
    detections: List<DetectionResult>,
    blurMode: BlurMode,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        detections.forEach { detection ->
            val color = when (detection.fieldType) {
                FieldType.ID_NUMBER     -> Color.Red
                FieldType.DATE_OF_BIRTH -> Color.Yellow
                FieldType.FULL_NAME     -> Color.Cyan
                FieldType.ADDRESS       -> Color(0xFF00E676)  // green
                FieldType.BARCODE,
                FieldType.QR_CODE       -> Color.Magenta
                FieldType.UNKNOWN       -> Color.White
            }

            val box = detection.boundingBox
            val topLeft = Offset(box.left, box.top)
            val size    = Size(box.width(), box.height())

            if (blurMode == BlurMode.BLACK_RECTANGLE) {
                drawRect(color = Color.Black, topLeft = topLeft, size = size)
            } else {
                // Semi-transparent fill + solid border
                drawRect(color = color.copy(alpha = 0.35f), topLeft = topLeft, size = size)
            }
            // Always draw border
            drawRect(color = color, topLeft = topLeft, size = size, style = Stroke(width = 3f))
        }
    }
}
